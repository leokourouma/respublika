// server/src/main/kotlin/com/respublika/service/DossierIngestor.kt
package com.respublika.service

import com.respublika.database.AgentRuns
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.database.DossiersLegislatifs
import com.respublika.model.DossierEtat
import com.respublika.model.DossierType
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.upsert
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Walks every JSON file under `data/dossierParlementaire/`, filters to legislature 17 by content
 * (not by filename — analysis red flag #1), derives the V1 schema, and upserts
 * into `dossiers_legislatifs`.
 *
 * Idempotent: re-running on the same data produces the same DB state. Each run
 * writes one `agent_runs` row.
 */
class DossierIngestor(
    private val dataPath: String,
    private val dryRun: Boolean = false,
    private val database: Database? = null,
    private val nowProvider: () -> Instant = Instant::now,
) {

    companion object {
        const val AGENT_NAME = "dossier-ingestor"
        const val L17 = "17"
        // L17 started 2024-06-09 — used to recognise carry-over dossiers (DLR5L16…
        // files that received L17 acts). Source: analysis/04_join_strategy.md.
        val L17_START: LocalDate = LocalDate.parse("2024-06-09")
    }

    data class Result(
        val totalFilesSeen: Int,
        val ingested: Int,
        val skippedNotL17: Int,
        val parseErrors: List<Failure>,
        val derivationErrors: List<Failure>,
        val agentRunId: Int?,
    )

    data class Failure(val file: String, val error: String)

    /**
     * Pure derivation result — what the ingestor computes per dossier from raw JSON.
     * Separating this from DB I/O lets the unit tests assert the rules directly.
     */
    data class Derived(
        val uid: String,
        val legislature: Short,
        val titre: String,
        val titreCourt: String?,
        val type: DossierType,
        val etat: DossierEtat,
        val dateDepot: LocalDate?,
        val dateDerniereDecision: LocalDate?,
        val datePromulgation: LocalDate?,
        val numeroLoi: String?,
    )

    suspend fun run(triggeredBy: String = "cli"): Result {
        val startedAt = nowProvider()
        val folder = File(dataPath)
        require(folder.isDirectory) { "Not a directory: $dataPath" }

        val files = folder.listFiles { _, name ->
            name.endsWith(".json") && !name.contains(":")
        }.orEmpty().sorted()

        println("DossierIngestor: scanning ${files.size} files in $dataPath (dry-run=$dryRun)")

        var ingested = 0
        var skipped = 0
        val parseErrors = mutableListOf<Failure>()
        val derivationErrors = mutableListOf<Failure>()

        files.forEachIndexed { index, file ->
            val raw = try {
                file.readText()
            } catch (e: Exception) {
                parseErrors += Failure(file.name, "I/O: ${e.message ?: e::class.simpleName}")
                return@forEachIndexed
            }
            if (raw.isBlank()) {
                parseErrors += Failure(file.name, "empty file")
                return@forEachIndexed
            }

            val root = try {
                Json.parseToJsonElement(raw).jsonObject["dossierParlementaire"]?.jsonObject
                    ?: error("Missing root key 'dossierParlementaire'")
            } catch (e: Exception) {
                parseErrors += Failure(file.name, "parse: ${e.message ?: e::class.simpleName}")
                return@forEachIndexed
            }

            // Filter by content. Skip pre-L17 dossiers UNLESS they carry an L17 act
            // (verified via legislature field of any voteRef matching VTANR5L17V*).
            if (!isInScopeForL17(root)) {
                skipped++
                return@forEachIndexed
            }

            val derived = try {
                derive(root)
            } catch (e: Exception) {
                derivationErrors += Failure(file.name, "derive: ${e.message ?: e::class.simpleName}")
                return@forEachIndexed
            }

            if (!dryRun) {
                try {
                    upsert(derived, root)
                    ingested++
                } catch (e: Exception) {
                    derivationErrors += Failure(file.name, "upsert: ${e.message ?: e::class.simpleName}")
                }
            } else {
                ingested++
            }

            if (index % 200 == 0) {
                print("\r  dossiers: $index/${files.size} (ingested=$ingested skipped=$skipped)")
            }
        }

        println()
        println(
            "DossierIngestor done: scanned=${files.size} ingested=$ingested " +
                "skipped_not_l17=$skipped parse_errors=${parseErrors.size} " +
                "derivation_errors=${derivationErrors.size}",
        )

        val finishedAt = nowProvider()
        val findings = buildJsonObject {
            put("dry_run", dryRun)
            put("data_path", dataPath)
            put("files_seen", files.size)
            put("ingested", ingested)
            put("skipped_not_l17", skipped)
            put("parse_errors_count", parseErrors.size)
            put("derivation_errors_count", derivationErrors.size)
            putJsonArray("parse_errors_samples") {
                parseErrors.take(20).forEach { add(buildJsonObject {
                    put("file", it.file); put("error", it.error)
                }) }
            }
            putJsonArray("derivation_errors_samples") {
                derivationErrors.take(20).forEach { add(buildJsonObject {
                    put("file", it.file); put("error", it.error)
                }) }
            }
        }

        val status = when {
            parseErrors.isNotEmpty() || derivationErrors.isNotEmpty() -> "warning"
            else -> "success"
        }

        val startedAtStr = startedAt.toString()
        val finishedAtStr = finishedAt.toString()
        val agentRunId = dbQuery(database) {
            AgentRuns.insert { row ->
                row[agentName] = AGENT_NAME
                row[AgentRuns.triggeredBy] = triggeredBy
                row[AgentRuns.startedAt] = startedAtStr
                row[AgentRuns.finishedAt] = finishedAtStr
                row[AgentRuns.status] = status
                row[recordsProcessed] = files.size
                row[recordsAffected] = ingested
                row[AgentRuns.findings] = findings.toString()
                row[errorDetail] = (parseErrors + derivationErrors).take(20)
                    .joinToString("\n") { "${it.file}: ${it.error}" }
                    .takeIf { it.isNotBlank() }
            } get AgentRuns.id
        }.value

        return Result(
            totalFilesSeen = files.size,
            ingested = ingested,
            skippedNotL17 = skipped,
            parseErrors = parseErrors,
            derivationErrors = derivationErrors,
            agentRunId = agentRunId,
        )
    }

    private suspend fun upsert(derived: Derived, raw: JsonObject) {
        val now = nowProvider()
        dbQuery(database) {
            DossiersLegislatifs.upsert(DossiersLegislatifs.uid) {
                it[uid] = derived.uid
                it[titre] = derived.titre
                it[titreCourt] = derived.titreCourt
                it[type] = derived.type
                it[etat] = derived.etat
                it[legislature] = derived.legislature
                it[dateDepot] = derived.dateDepot
                it[dateDerniereDecision] = derived.dateDerniereDecision
                it[datePromulgation] = derived.datePromulgation
                it[numeroLoi] = derived.numeroLoi
                // themes left null in V1 (no reliable source field; see analysis 05.1)
                it[themes] = null
                it[rawJson] = raw
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
    }

    /** True when the dossier belongs to L17 — by declared legislature OR by carry-over voteRef. */
    fun isInScopeForL17(root: JsonObject): Boolean {
        val leg = root["legislature"]?.jsonPrimitive?.contentOrNull
        if (leg == L17) return true
        // Carry-over case: dossier file from L16/L15 that received an L17 act.
        // Detect by presence of any voteRef matching VTANR5L17V*.
        return walkVoteRefs(root).any { it.startsWith("VTANR5L17V") }
    }

    fun derive(root: JsonObject): Derived {
        val uid = root["uid"]?.jsonPrimitive?.contentOrNull
            ?: error("dossierParlementaire.uid is missing")

        // Legislature: use the declared one if present; for carry-over dossiers the
        // dossier itself stays under its original legislature (this is faithful
        // semantics — the dossier is L16, but it produces L17 acts).
        val legislatureStr = root["legislature"]?.jsonPrimitive?.contentOrNull
            ?: error("dossierParlementaire.legislature is missing on $uid")
        val legislature = legislatureStr.toShortOrNull()
            ?: error("dossierParlementaire.legislature is non-numeric: '$legislatureStr' on $uid")

        val titreDossier = root["titreDossier"] as? JsonObject
        val titre = (titreDossier?.get("titre") as? JsonPrimitive)?.contentOrNull
            ?: error("titreDossier.titre is missing on $uid")

        val titreCourt = (titreDossier?.get("titreChemin") as? JsonPrimitive)?.contentOrNull

        val type = deriveType(root, uid)
        val (etat, lastDecisionDate, promulgationDate, numeroLoi) = deriveEtatAndDates(root, uid)
        val dateDepot = deriveDateDepot(root)

        return Derived(
            uid = uid,
            legislature = legislature,
            titre = titre,
            titreCourt = titreCourt,
            type = type,
            etat = etat,
            dateDepot = dateDepot,
            dateDerniereDecision = lastDecisionDate,
            datePromulgation = promulgationDate,
            numeroLoi = numeroLoi,
        )
    }

    private fun deriveType(root: JsonObject, uid: String): DossierType {
        val xsi = root["@xsi:type"]?.jsonPrimitive?.contentOrNull
            ?: error("@xsi:type is missing on $uid")
        return when (xsi) {
            "DossierLegislatif_Type" -> {
                val code = (root["procedureParlementaire"] as? JsonObject)
                    ?.let { it["code"] as? JsonPrimitive }?.contentOrNull
                if (code == "5") DossierType.LOI_ORGANIQUE else DossierType.LOI_ORDINAIRE
            }
            "DossierResolutionAN" -> DossierType.PROPOSITION_RESOLUTION
            "DossierIniativeExecutif_Type" -> DossierType.MOTION_CENSURE
            else -> DossierType.AUTRE
        }
    }

    private data class EtatDerivation(
        val etat: DossierEtat,
        val lastDecisionDate: LocalDate?,
        val promulgationDate: LocalDate?,
        val numeroLoi: String?,
    )

    private fun deriveEtatAndDates(root: JsonObject, uid: String): EtatDerivation {
        var promulgationDate: LocalDate? = null
        var numeroLoi: String? = null
        var hasRetrait = false
        val decisionActes = mutableListOf<DecisionActe>()

        walkActes(root) { acte ->
            when (acte["@xsi:type"]?.jsonPrimitive?.contentOrNull) {
                "Promulgation_Type" -> {
                    promulgationDate = parseLocalDateOrNull(acte["dateActe"]?.jsonPrimitive?.contentOrNull)
                    numeroLoi = acte["numeroLoi"]?.jsonPrimitive?.contentOrNull
                }
                "RetraitInitiative_Type" -> hasRetrait = true
                "Decision_Type", "DecisionMotionCensure_Type" -> {
                    val date = parseLocalDateOrNull(acte["dateActe"]?.jsonPrimitive?.contentOrNull)
                    val statut = (acte["statutConclusion"] as? JsonObject)
                        ?.let { it["libelle"] as? JsonPrimitive }?.contentOrNull
                    decisionActes += DecisionActe(date, statut)
                }
            }
        }

        val lastDecisionDate = decisionActes.mapNotNull { it.date }.maxOrNull()

        val etat = when {
            promulgationDate != null -> DossierEtat.PROMULGUE
            hasRetrait -> DossierEtat.RETIRE
            decisionActes.isEmpty() -> DossierEtat.DORMANT
            else -> {
                // Most recent decision libelle drives en_cours / adopte / rejete.
                val mostRecent = decisionActes
                    .filter { it.date != null }
                    .maxByOrNull { it.date!! }
                    ?: decisionActes.last()
                val libelle = mostRecent.statutLibelle?.lowercase().orEmpty()
                when {
                    "rejet" in libelle -> DossierEtat.REJETE
                    "adopt" in libelle -> DossierEtat.ADOPTE
                    else -> DossierEtat.EN_COURS
                }
            }
        }

        return EtatDerivation(etat, lastDecisionDate, promulgationDate, numeroLoi)
    }

    private data class DecisionActe(val date: LocalDate?, val statutLibelle: String?)

    /** Earliest `DepotInitiative_Type` dateActe — the start of the dossier's life. */
    private fun deriveDateDepot(root: JsonObject): LocalDate? {
        var earliest: LocalDate? = null
        walkActes(root) { acte ->
            val xsi = acte["@xsi:type"]?.jsonPrimitive?.contentOrNull
            if (xsi == "DepotInitiative_Type" || xsi == "DepotInitiativeNavette_Type" ||
                xsi == "DepotMotionCensure_Type" || xsi == "DepotAccordInternational_Type"
            ) {
                val d = parseLocalDateOrNull(acte["dateActe"]?.jsonPrimitive?.contentOrNull)
                if (d != null && (earliest == null || d.isBefore(earliest))) earliest = d
            }
        }
        return earliest
    }

    /** Walk every acteLegislatif (object or array) recursively, calling `visit` on each acte object. */
    private fun walkActes(root: JsonObject, visit: (JsonObject) -> Unit) {
        fun recurse(node: JsonElement?) {
            when (node) {
                is JsonObject -> {
                    if ((node["@xsi:type"] as? JsonPrimitive)?.contentOrNull != null) {
                        visit(node)
                    }
                    // JsonNull on `actesLegislatifs` is common in the feed; `as? JsonObject`
                    // turns null/JsonNull/wrong-type into Kotlin null safely.
                    val children = (node["actesLegislatifs"] as? JsonObject)?.get("acteLegislatif")
                    when (children) {
                        is JsonArray -> children.forEach { recurse(it) }
                        is JsonObject -> recurse(children)
                        else -> {}
                    }
                }
                else -> {}
            }
        }
        val children = (root["actesLegislatifs"] as? JsonObject)?.get("acteLegislatif")
        when (children) {
            is JsonArray -> children.forEach { recurse(it) }
            is JsonObject -> recurse(children)
            else -> {}
        }
    }

    /** Walk every voteRef string in the tree (used by isInScopeForL17 and by tests). */
    fun walkVoteRefs(root: JsonObject): Sequence<String> = sequence {
        fun recurse(node: JsonElement?): Sequence<String> = sequence {
            when (node) {
                is JsonObject -> {
                    // `node["voteRefs"]` may be JsonNull, on which `.jsonObject` throws —
                    // use `as? JsonObject` to handle null/JsonNull/wrong-type uniformly.
                    val voteRefsObj = node["voteRefs"] as? JsonObject
                    val voteRefs = voteRefsObj?.get("voteRef")
                    when (voteRefs) {
                        is JsonPrimitive -> if (voteRefs.isString) yield(voteRefs.content)
                        is JsonArray -> voteRefs.forEach {
                            if (it is JsonPrimitive && it.isString) yield(it.content)
                        }
                        else -> {}
                    }
                    node.values.forEach { yieldAll(recurse(it)) }
                }
                is JsonArray -> node.forEach { yieldAll(recurse(it)) }
                else -> {}
            }
        }
        yieldAll(recurse(root))
    }

    private fun parseLocalDateOrNull(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        // The feed uses ISO offset datetimes ("2024-11-20T00:00:00.000+01:00").
        return try {
            OffsetDateTime.parse(s).toLocalDate()
        } catch (_: DateTimeParseException) {
            try { LocalDate.parse(s) } catch (_: DateTimeParseException) { null }
        }
    }
}

object DossierIngestorCli {
    suspend fun execute(args: Array<String>) {
        var dryRun = false
        var dataPath = System.getenv("DATA_PATH") ?: "./data"
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "--dry-run" -> { dryRun = true; i += 1 }
                "--data-path" -> { dataPath = args[i + 1]; i += 2 }
                else -> { System.err.println("Unknown arg: $a"); i += 1 }
            }
        }
        val folder = "$dataPath/dossierParlementaire"
        DossierIngestor(folder, dryRun = dryRun).run(triggeredBy = "cli")
    }
}

// Helper kept private to this file — converts `BigDecimal` confidences without surprise.
@Suppress("unused")
private fun bd(value: Double): BigDecimal = BigDecimal.valueOf(value)
