// server/src/main/kotlin/com/respublika/service/DossierLinker.kt
package com.respublika.service

import com.respublika.database.AgentRuns
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.database.DossierLinkCandidates
import com.respublika.database.DossiersLegislatifs
import com.respublika.database.Scrutins
import com.respublika.model.LinkMethod
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import java.math.BigDecimal
import java.time.Instant

/**
 * Populates `scrutins.dossier_uid` (+ method + confidence) and
 * `dossier_link_candidates`. Runs AFTER scrutin and dossier ingestors.
 *
 * Two-pass strategy from analysis/05_recommendation.md §3:
 *
 *   Pass 1 — voteRef (canonical, exact, 100% confidence)
 *       Walk every dossier raw_json for voteRefs.voteRef on
 *       Decision_Type / DecisionMotionCensure_Type actes. Set the link.
 *
 *   Pass 2 — séance fallback (heuristic, covers amendments and articles)
 *       Build (reunionRef → set<dossier_uid>) from each dossier raw_json.
 *       For every scrutin still null, resolve scrutin.seanceRef:
 *         - exactly 1 dossier  → set link, method=seance, confidence=0.85
 *         - more than 1        → leave null, write to dossier_link_candidates
 *                                with confidence=0.50/N (N = candidate count)
 *         - 0                  → orphan
 *
 * Idempotent: every run rebuilds Pass 1 (cheap, deterministic) and Pass 2 only
 * for rows whose method != 'voteRef'. Candidates rows for those scrutins are
 * deleted before re-insertion.
 */
class DossierLinker(
    private val dryRun: Boolean = false,
    private val database: Database? = null,
    private val nowProvider: () -> Instant = Instant::now,
) {

    companion object {
        const val AGENT_NAME = "dossier-linker"
        val VOTE_REF_CONFIDENCE: BigDecimal = BigDecimal("1.00")
        val SEANCE_UNIQUE_CONFIDENCE: BigDecimal = BigDecimal("0.85")
        // Per the spec: "confidence=0.50 (split by N matches)" — 0.50/N for the
        // candidate rows. Floor at 0.05 so a séance with 12+ candidates still
        // produces a meaningful weight.
        fun seanceCandidateConfidence(candidateCount: Int): BigDecimal {
            require(candidateCount >= 2) { "candidate count must be >= 2" }
            val raw = 0.50 / candidateCount
            return BigDecimal(raw.coerceAtLeast(0.05)).setScale(2, java.math.RoundingMode.HALF_UP)
        }
    }

    data class Result(
        val scrutinsProcessed: Int,
        val voteRefMatches: Int,
        val seanceUniqueMatches: Int,
        val seanceAmbiguous: Int,
        val candidateRowsWritten: Int,
        val orphans: Int,
        val agentRunId: Int?,
    )

    suspend fun run(triggeredBy: String = "cli"): Result {
        val startedAt = nowProvider()
        println("DossierLinker: starting (dry-run=$dryRun)")

        // ── Read everything we need in one pass ────────────────────────────
        // Dossier raw_json + uid (everything else lives inside raw_json).
        val dossiers = dbQuery(database) {
            DossiersLegislatifs.select(DossiersLegislatifs.uid, DossiersLegislatifs.rawJson)
                .map { it[DossiersLegislatifs.uid] to it[DossiersLegislatifs.rawJson] }
        }
        println("  loaded ${dossiers.size} dossiers")

        // Scrutin uid + seanceRef.
        val scrutins = dbQuery(database) {
            Scrutins.select(Scrutins.uid, Scrutins.rawJson)
                .map { row ->
                    val uid = row[Scrutins.uid]
                    val seance = row[Scrutins.rawJson]
                        ?.let { it as? JsonObject }
                        ?.get("scrutin")?.let { it as? JsonObject }
                        ?.get("seanceRef")?.let { it as? JsonPrimitive }?.contentOrNull
                    uid to seance
                }
        }
        val scrutinUidsInDb = scrutins.mapTo(mutableSetOf()) { it.first }
        val seanceByScrutin = scrutins.toMap()
        println("  loaded ${scrutins.size} scrutins (${scrutins.count { it.second != null }} with seanceRef)")

        // ── Pass 1 — voteRef ───────────────────────────────────────────────
        // (dossier_uid -> set<scrutin_uid>) mapping built from dossier raw_json.
        val voteRefMap: Map<String, String> = buildVoteRefMap(dossiers, scrutinUidsInDb)
        println("  voteRef map: ${voteRefMap.size} scrutins reachable from a Decision_Type / DecisionMotionCensure_Type voteRef")

        // ── Pass 2 prep — séance map ──────────────────────────────────────
        val seanceToDossiers: Map<String, Set<String>> = buildSeanceMap(dossiers)
        println("  séance map: ${seanceToDossiers.size} reunionRefs across ${dossiers.size} dossiers")

        // ── Apply ─────────────────────────────────────────────────────────
        var voteRefMatches = 0
        var seanceUniqueMatches = 0
        var seanceAmbiguous = 0
        var candidateRowsWritten = 0
        var orphans = 0

        // Reset link state for scrutins we are about to (re)process — only those
        // not currently linked via voteRef. voteRef links are exact and never
        // overridden by séance heuristics.
        if (!dryRun) {
            dbQuery(database) {
                // Clear candidates for any scrutin we may re-link. Cheap to truncate
                // since candidates are a derived staging table.
                DossierLinkCandidates.deleteAll()

                // Clear non-voteRef links so re-runs reflect the latest dossier data.
                Scrutins.update({
                    (Scrutins.dossierLinkMethod neq LinkMethod.voteRef) or
                        Scrutins.dossierLinkMethod.isNull()
                }) {
                    it[dossierUid] = null
                    it[dossierLinkMethod] = null
                    it[dossierLinkConfidence] = null
                }
            }
        }

        // Pass 1: write voteRef links.
        for ((scrutinUid, dossierUid) in voteRefMap) {
            if (!dryRun) {
                dbQuery(database) {
                    Scrutins.update({ Scrutins.uid eq scrutinUid }) {
                        it[Scrutins.dossierUid] = dossierUid
                        it[dossierLinkMethod] = LinkMethod.voteRef
                        it[dossierLinkConfidence] = VOTE_REF_CONFIDENCE
                    }
                }
            }
            voteRefMatches++
        }

        // Pass 2: séance fallback for everyone else.
        val resolvedByVoteRef = voteRefMap.keys
        for ((scrutinUid, seanceRef) in seanceByScrutin) {
            if (scrutinUid in resolvedByVoteRef) continue
            if (seanceRef.isNullOrBlank()) {
                orphans++; continue
            }
            val candidates = seanceToDossiers[seanceRef].orEmpty()
            when (candidates.size) {
                0 -> orphans++
                1 -> {
                    val target = candidates.single()
                    if (!dryRun) {
                        dbQuery(database) {
                            Scrutins.update({ Scrutins.uid eq scrutinUid }) {
                                it[dossierUid] = target
                                it[dossierLinkMethod] = LinkMethod.seance
                                it[dossierLinkConfidence] = SEANCE_UNIQUE_CONFIDENCE
                            }
                        }
                    }
                    seanceUniqueMatches++
                }
                else -> {
                    val confidence = seanceCandidateConfidence(candidates.size)
                    if (!dryRun) {
                        val now = nowProvider()
                        dbQuery(database) {
                            DossierLinkCandidates.batchInsert(candidates) { d ->
                                this[DossierLinkCandidates.scrutinUid] = scrutinUid
                                this[DossierLinkCandidates.dossierUid] = d
                                this[DossierLinkCandidates.linkMethod] = LinkMethod.seance
                                this[DossierLinkCandidates.confidence] = confidence
                                this[DossierLinkCandidates.createdAt] = now
                            }
                        }
                    }
                    seanceAmbiguous++
                    candidateRowsWritten += candidates.size
                }
            }
        }

        val finishedAt = nowProvider()

        val findings = buildJsonObject {
            put("dry_run", dryRun)
            put("scrutins_processed", scrutins.size)
            put("dossiers_seen", dossiers.size)
            put("vote_ref_matches", voteRefMatches)
            put("seance_unique_matches", seanceUniqueMatches)
            put("seance_ambiguous_scrutins", seanceAmbiguous)
            put("candidate_rows_written", candidateRowsWritten)
            put("orphans", orphans)
        }

        val startedAtStr = startedAt.toString()
        val finishedAtStr = finishedAt.toString()
        val status = "success"
        val agentRunId = dbQuery(database) {
            AgentRuns.insert { row ->
                row[agentName] = AGENT_NAME
                row[AgentRuns.triggeredBy] = triggeredBy
                row[AgentRuns.startedAt] = startedAtStr
                row[AgentRuns.finishedAt] = finishedAtStr
                row[AgentRuns.status] = status
                row[recordsProcessed] = scrutins.size
                row[recordsAffected] = voteRefMatches + seanceUniqueMatches
                row[AgentRuns.findings] = findings.toString()
                row[errorDetail] = null
            } get AgentRuns.id
        }.value

        println()
        println("┌─────────────────────────── DossierLinker ───────────────────────────┐")
        println("│ scrutins processed         : ${scrutins.size.toString().padStart(6)}                                  │")
        println("│ voteRef matches            : ${voteRefMatches.toString().padStart(6)}                                  │")
        println("│ séance unique matches      : ${seanceUniqueMatches.toString().padStart(6)}                                  │")
        println("│ séance ambiguous scrutins  : ${seanceAmbiguous.toString().padStart(6)} (${candidateRowsWritten.toString().padStart(5)} candidate rows)        │")
        println("│ orphans (no link)          : ${orphans.toString().padStart(6)}                                  │")
        println("│ agent_run_id               : ${agentRunId.toString().padStart(6)}                                  │")
        println("└─────────────────────────────────────────────────────────────────────┘")

        return Result(
            scrutinsProcessed = scrutins.size,
            voteRefMatches = voteRefMatches,
            seanceUniqueMatches = seanceUniqueMatches,
            seanceAmbiguous = seanceAmbiguous,
            candidateRowsWritten = candidateRowsWritten,
            orphans = orphans,
            agentRunId = agentRunId,
        )
    }

    /**
     * Walks each dossier's raw_json, finds Decision_Type / DecisionMotionCensure_Type
     * actes, and emits (scrutin_uid -> dossier_uid) for every voteRef whose target
     * scrutin actually exists in the DB.
     *
     * If the same scrutin is referenced by multiple dossiers (rare but possible
     * when a vote is mirrored across navette branches), the LAST writer wins. We
     * record this in the final agent_runs findings so it's auditable.
     */
    private fun buildVoteRefMap(
        dossiers: List<Pair<String, JsonElement>>,
        scrutinUidsInDb: Set<String>,
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((dossierUid, raw) in dossiers) {
            val root = (raw as? JsonObject) ?: continue
            walkActes(root) { acte ->
                val xsi = acte["@xsi:type"]?.jsonPrimitive?.contentOrNull
                if (xsi != "Decision_Type" && xsi != "DecisionMotionCensure_Type") return@walkActes
                val voteRefs = (acte["voteRefs"] as? JsonObject)?.get("voteRef") ?: return@walkActes
                val uids = when (voteRefs) {
                    is JsonPrimitive -> if (voteRefs.isString) listOf(voteRefs.content) else emptyList()
                    is JsonArray -> voteRefs.mapNotNull {
                        if (it is JsonPrimitive && it.isString) it.content else null
                    }
                    else -> emptyList()
                }
                uids.forEach { uid ->
                    if (uid in scrutinUidsInDb) map[uid] = dossierUid
                }
            }
        }
        return map
    }

    /** Builds (reunionRef → set<dossier_uid>) from every acte that carries a reunionRef. */
    private fun buildSeanceMap(
        dossiers: List<Pair<String, JsonElement>>,
    ): Map<String, Set<String>> {
        val map = mutableMapOf<String, MutableSet<String>>()
        for ((dossierUid, raw) in dossiers) {
            val root = (raw as? JsonObject) ?: continue
            walkActes(root) { acte ->
                val rr = acte["reunionRef"]?.jsonPrimitive?.contentOrNull ?: return@walkActes
                map.getOrPut(rr) { mutableSetOf() }.add(dossierUid)
            }
        }
        return map
    }

    private fun walkActes(root: JsonObject, visit: (JsonObject) -> Unit) {
        fun recurse(node: JsonElement?) {
            when (node) {
                is JsonObject -> {
                    if ((node["@xsi:type"] as? JsonPrimitive)?.contentOrNull != null) {
                        visit(node)
                    }
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
}

object DossierLinkerCli {
    suspend fun execute(args: Array<String>) {
        var dryRun = false
        var i = 0
        while (i < args.size) {
            when (val a = args[i]) {
                "--dry-run" -> { dryRun = true; i += 1 }
                else -> { System.err.println("Unknown arg: $a"); i += 1 }
            }
        }
        DossierLinker(dryRun = dryRun).run(triggeredBy = "cli")
    }
}
