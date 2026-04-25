// server/src/main/kotlin/com/respublika/agents/AnomalyDetector.kt
package com.respublika.agents

import com.respublika.database.AgentRuns
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.database.Scrutins
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

enum class Severity { CRITICAL, WARNING }

data class Finding(val severity: Severity, val file: String, val message: String)

/**
 * Result of validating a single JSON file before DB insertion.
 * If blocked == true, the record must NOT be inserted.
 */
data class ValidationResult(val blocked: Boolean, val findings: List<Finding>)

class AnomalyDetector {

    private val allFindings = mutableListOf<Finding>()
    private var recordsProcessed = 0
    private var recordsBlocked = 0

    // ── Public API ────────────────────────────────────────────────

    /**
     * Validate a raw JSON string for the given folder.
     * Returns a ValidationResult; caller must skip DB write when blocked.
     */
    fun validate(folder: String, fileName: String, rawJson: String): ValidationResult {
        recordsProcessed++
        val findings = mutableListOf<Finding>()

        try {
            val root = Json.parseToJsonElement(rawJson).jsonObject

            when (folder) {
                "scrutins"  -> validateScrutin(root, fileName, findings)
                "acteurs"   -> validateActeur(root, fileName, findings)
                "deports"   -> validateDeport(root, fileName, findings)
            }
        } catch (e: Exception) {
            findings += Finding(Severity.CRITICAL, fileName, "JSON parse error: ${e.message}")
        }

        val blocked = findings.any { it.severity == Severity.CRITICAL }
        if (blocked) recordsBlocked++
        allFindings += findings
        return ValidationResult(blocked, findings)
    }

    /**
     * Run gap detection on ingested scrutin UIDs.
     * Call this AFTER all scrutins have been ingested.
     */
    suspend fun detectScrutinGaps() {
        try {
            val uids = dbQuery {
                Scrutins.selectAll()
                    .map { it[Scrutins.uid] }
                    .filter { it.matches(Regex("VTANR5L\\d+V\\d+")) }
            }

            val parsed = uids.mapNotNull { uid ->
                val match = Regex("VTANR5L(\\d+)V(\\d+)").find(uid) ?: return@mapNotNull null
                val legislature = match.groupValues[1].toInt()
                val seq = match.groupValues[2].toInt()
                Triple(uid, legislature, seq)
            }

            val byLegislature = parsed.groupBy { it.second }

            for ((legislature, entries) in byLegislature) {
                val sequences = entries.map { it.third }.sorted()
                if (sequences.isEmpty()) continue

                val min = sequences.first()
                val max = sequences.last()
                val present = sequences.toSet()

                for (seq in min..max) {
                    if (seq !in present) {
                        val gapUid = "VTANR5L${legislature}V$seq"
                        allFindings += Finding(
                            Severity.WARNING,
                            "gap-detector",
                            "Missing scrutin UID in sequence: $gapUid"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            allFindings += Finding(
                Severity.CRITICAL,
                "gap-detector",
                "Gap detection failed: ${e.message}"
            )
        }
    }

    /**
     * Persist an agent_runs row summarising this run.
     * Call once at the end of processAll().
     */
    suspend fun saveRun(triggeredBy: String) {
        val blocked = allFindings.filter { it.severity == Severity.CRITICAL }
        val warnings = allFindings.filter { it.severity == Severity.WARNING && it.file != "gap-detector" }
        val gaps = allFindings.filter { it.file == "gap-detector" }

        val status = when {
            blocked.isNotEmpty() -> "failure"
            warnings.isNotEmpty() || gaps.isNotEmpty() -> "warning"
            else -> "success"
        }

        // Aggregate warnings by message template → count
        val warningSummary = mutableMapOf<String, Int>()
        for (w in warnings) {
            warningSummary[w.message] = (warningSummary[w.message] ?: 0) + 1
        }

        val findingsJson = buildJsonObject {
            putJsonArray("blocked") {
                blocked.forEach { add(buildJsonObject {
                    put("file", it.file)
                    put("message", it.message)
                }) }
            }
            putJsonObject("warning_summary") {
                warningSummary.forEach { (msg, count) -> put(msg, count) }
            }
            putJsonArray("gaps") {
                gaps.forEach { add(buildJsonObject {
                    put("file", it.file)
                    put("message", it.message)
                }) }
            }
        }

        dbQuery {
            AgentRuns.insert { row ->
                row[agentName] = "AnomalyDetector"
                row[AgentRuns.triggeredBy] = triggeredBy
                row[startedAt] = Instant.now().toString()
                row[finishedAt] = Instant.now().toString()
                row[AgentRuns.status] = status
                row[recordsProcessed] = this@AnomalyDetector.recordsProcessed
                row[recordsAffected] = this@AnomalyDetector.recordsBlocked
                row[findings] = findingsJson.toString()
                row[errorDetail] = if (blocked.isNotEmpty()) {
                    blocked.joinToString("\n") { "${it.file}: ${it.message}" }
                } else null
            }
        }

        println("  Agent AnomalyDetector: $status | processed=${this.recordsProcessed} blocked=${this.recordsBlocked} warnings=${warnings.size} gaps=${gaps.size}")
    }

    // ── Scrutin validation ────────────────────────────────────────

    private fun validateScrutin(root: JsonObject, fileName: String, findings: MutableList<Finding>) {
        val scrutin = root["scrutin"]?.jsonObjectOrNull
        if (scrutin == null) {
            findings += Finding(Severity.CRITICAL, fileName, "Missing root key 'scrutin'")
            return
        }

        val uid = scrutin["uid"]?.jsonPrimitiveOrNull?.contentOrNull
        if (uid.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "scrutin.uid is missing or blank")
            return
        }

        // ventilationVotes.organe.groupes.groupe must be present
        val groupes = scrutin["ventilationVotes"]?.jsonObjectOrNull
            ?.get("organe")?.jsonObjectOrNull
            ?.get("groupes")?.jsonObjectOrNull
            ?.get("groupe")

        if (groupes == null || (groupes !is JsonArray && groupes !is JsonObject)) {
            findings += Finding(Severity.WARNING, fileName, "ventilationVotes.organe.groupes.groupe missing or wrong type")
        }

        // sort.libelle should contain "adopté" or "rejeté" (AN uses full sentences)
        val sortLibelle = scrutin["sort"]?.jsonObjectOrNull
            ?.get("libelle")?.jsonPrimitiveOrNull?.contentOrNull

        if (sortLibelle != null
            && !sortLibelle.contains("adopté", ignoreCase = true)
            && !sortLibelle.contains("rejeté", ignoreCase = true)) {
            findings += Finding(Severity.WARNING, fileName, "sort.libelle unknown value: '$sortLibelle'")
        }

        // suffragesExprimes must not exceed nombreVotants
        val synthese = scrutin["syntheseVote"]?.jsonObjectOrNull
        if (synthese != null) {
            val votants = synthese["nombreVotants"]?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull()
            val exprimes = synthese["suffragesExprimes"]?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull()
            if (votants != null && exprimes != null && exprimes > votants) {
                findings += Finding(Severity.WARNING, fileName, "suffragesExprimes ($exprimes) exceeds nombreVotants ($votants)")
            }
        }

        // Each votant in pours/contres/abstentions must have non-blank acteurRef
        if (groupes != null) {
            val groupeList = when (groupes) {
                is JsonArray -> groupes.mapNotNull { it.jsonObjectOrNull }
                is JsonObject -> listOf(groupes)
                else -> emptyList()
            }
            for (g in groupeList) {
                val vote = g["vote"]?.jsonObjectOrNull ?: continue
                for (category in listOf("pours", "contres", "abstentions")) {
                    val votants = vote[category]?.jsonObjectOrNull?.get("votant")
                    val votantList = when (votants) {
                        is JsonArray -> votants.mapNotNull { it.jsonObjectOrNull }
                        is JsonObject -> listOf(votants)
                        else -> emptyList()
                    }
                    for (v in votantList) {
                        val ref = v["acteurRef"]?.jsonPrimitiveOrNull?.contentOrNull
                        if (ref.isNullOrBlank()) {
                            findings += Finding(Severity.WARNING, fileName, "Blank acteurRef in $category")
                        }
                    }
                }
            }
        }
    }

    // ── Acteur validation ─────────────────────────────────────────

    private fun validateActeur(root: JsonObject, fileName: String, findings: MutableList<Finding>) {
        val acteur = root["acteur"]?.jsonObjectOrNull
        if (acteur == null) {
            findings += Finding(Severity.CRITICAL, fileName, "Missing root key 'acteur'")
            return
        }

        val uidText = acteur["uid"]?.jsonObjectOrNull?.get("#text")?.jsonPrimitiveOrNull?.contentOrNull
        if (uidText.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "acteur.uid.#text is missing or blank")
            return
        }

        val ident = acteur["etatCivil"]?.jsonObjectOrNull?.get("ident")?.jsonObjectOrNull
        val prenom = ident?.get("prenom")?.jsonPrimitiveOrNull?.contentOrNull
        val nom = ident?.get("nom")?.jsonPrimitiveOrNull?.contentOrNull

        if (prenom.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "etatCivil.ident.prenom is missing or blank")
        }
        if (nom.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "etatCivil.ident.nom is missing or blank")
        }

        // Warning-only: at least one mandat with typeOrgane == "GP"
        val mandats = acteur["mandats"]?.jsonObjectOrNull?.get("mandat")
        val mandatList = when (mandats) {
            is JsonArray -> mandats
            is JsonObject -> JsonArray(listOf(mandats))
            else -> JsonArray(emptyList())
        }
        val hasGP = mandatList.any {
            it.jsonObjectOrNull?.get("typeOrgane")?.jsonPrimitiveOrNull?.contentOrNull == "GP"
        }
        if (!hasGP) {
            findings += Finding(Severity.WARNING, fileName, "No mandat with typeOrgane='GP' found")
        }
    }

    // ── Deport validation ─────────────────────────────────────────

    private fun validateDeport(root: JsonObject, fileName: String, findings: MutableList<Finding>) {
        val deport = root["deport"]?.jsonObjectOrNull
        if (deport == null) {
            findings += Finding(Severity.CRITICAL, fileName, "Missing root key 'deport'")
            return
        }

        val uid = deport["uid"]?.jsonPrimitiveOrNull?.contentOrNull
        if (uid.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "deport.uid is missing or blank")
        }

        val refActeur = deport["refActeur"]?.jsonPrimitiveOrNull?.contentOrNull
        if (refActeur.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "deport.refActeur is missing or blank")
        }

        val porteeLibelle = deport["portee"]?.jsonObjectOrNull?.get("libelle")?.jsonPrimitiveOrNull?.contentOrNull
        if (porteeLibelle.isNullOrBlank()) {
            findings += Finding(Severity.CRITICAL, fileName, "deport.portee.libelle is missing or blank")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private val JsonElement.jsonObjectOrNull: JsonObject?
        get() = if (this is JsonObject) this else null

    private val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
        get() = if (this is JsonPrimitive) this else null
}
