package com.respublika.service

import com.respublika.database.AgentRuns
import com.respublika.database.DossierLinkCandidates
import com.respublika.database.DossiersLegislatifs
import com.respublika.database.Scrutins
import com.respublika.model.DossierEtat
import com.respublika.model.DossierType
import com.respublika.model.LinkMethod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DossierLinkerTest {

    private lateinit var db: Database

    @BeforeTest
    fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:test_${System.nanoTime()}_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(Scrutins, DossiersLegislatifs, DossierLinkCandidates, AgentRuns)
            Scrutins.deleteAll()
            DossiersLegislatifs.deleteAll()
            DossierLinkCandidates.deleteAll()
            AgentRuns.deleteAll()
        }
    }

    private fun insertDossier(
        uid: String,
        rawJson: JsonElement,
        type: DossierType = DossierType.LOI_ORDINAIRE,
        etat: DossierEtat = DossierEtat.EN_COURS,
    ) = transaction(db) {
        val now = Instant.parse("2026-04-26T12:00:00Z")
        DossiersLegislatifs.insert {
            it[DossiersLegislatifs.uid] = uid
            it[titre] = "test $uid"
            it[titreCourt] = null
            it[DossiersLegislatifs.type] = type
            it[DossiersLegislatifs.etat] = etat
            it[legislature] = 17
            it[dateDepot] = LocalDate.parse("2025-01-01")
            it[dateDerniereDecision] = null
            it[datePromulgation] = null
            it[numeroLoi] = null
            it[themes] = null
            it[DossiersLegislatifs.rawJson] = rawJson
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    private fun insertScrutin(uid: String, seanceRef: String?, titre: String = "test $uid") = transaction(db) {
        Scrutins.insert {
            it[Scrutins.uid] = uid
            it[Scrutins.titre] = titre
            it[dateVote] = "2025-01-15"
            if (seanceRef != null) {
                it[rawJson] = Json.parseToJsonElement(
                    """{"scrutin":{"uid":"$uid","seanceRef":"$seanceRef"}}""",
                )
            }
        }
    }

    private fun selectScrutin(uid: String) = transaction(db) {
        Scrutins.selectAll().where { Scrutins.uid eq uid }.singleOrNull()?.let {
            mapOf(
                "uid" to it[Scrutins.uid],
                "dossier_uid" to it[Scrutins.dossierUid],
                "method" to it[Scrutins.dossierLinkMethod],
                "confidence" to it[Scrutins.dossierLinkConfidence],
            )
        }
    }

    private fun candidatesForScrutin(uid: String): List<Map<String, Any?>> = transaction(db) {
        DossierLinkCandidates.selectAll()
            .where { DossierLinkCandidates.scrutinUid eq uid }
            .map {
                mapOf(
                    "dossier_uid" to it[DossierLinkCandidates.dossierUid],
                    "method" to it[DossierLinkCandidates.linkMethod],
                    "confidence" to it[DossierLinkCandidates.confidence],
                )
            }
    }

    /** Tiny dossier raw_json: a single Decision_Type with one voteRef and one reunionRef. */
    private fun dossierRawWithVoteRef(voteRef: String, seance: String) = Json.parseToJsonElement(
        """
        {
          "actesLegislatifs": {
            "acteLegislatif": {
              "@xsi:type": "Etape_Type",
              "actesLegislatifs": {
                "acteLegislatif": {
                  "@xsi:type": "Decision_Type",
                  "codeActe": "AN1-DEBATS-DEC",
                  "dateActe": "2025-01-15T00:00:00.000+01:00",
                  "reunionRef": "$seance",
                  "voteRefs": { "voteRef": "$voteRef" }
                }
              }
            }
          }
        }
        """.trimIndent(),
    )

    /** Dossier raw_json with only a séance acte (no voteRef) — used for séance-only matches. */
    private fun dossierRawWithSeanceOnly(seance: String) = Json.parseToJsonElement(
        """
        {
          "actesLegislatifs": {
            "acteLegislatif": {
              "@xsi:type": "Etape_Type",
              "actesLegislatifs": {
                "acteLegislatif": {
                  "@xsi:type": "DiscussionSeancePublique_Type",
                  "codeActe": "AN1-DEBATS-SEANCE",
                  "dateActe": "2025-01-15T00:00:00.000+01:00",
                  "reunionRef": "$seance"
                }
              }
            }
          }
        }
        """.trimIndent(),
    )

    private val now = { Instant.parse("2026-04-26T12:00:00Z") }
    private fun newLinker(dryRun: Boolean = false) = DossierLinker(dryRun = dryRun, database = db, nowProvider = now)

    @Test
    fun `voteRef match yields confidence 1_00`() = runBlocking {
        insertDossier("DLR5L17N1", dossierRawWithVoteRef("VTANR5L17V1", "RU-A"))
        insertScrutin("VTANR5L17V1", seanceRef = "RU-A")

        val result = newLinker().run("test")

        assertEquals(1, result.voteRefMatches)
        assertEquals(0, result.seanceUniqueMatches)
        assertEquals(0, result.seanceAmbiguous)
        assertEquals(0, result.orphans)

        val s = assertNotNull(selectScrutin("VTANR5L17V1"))
        assertEquals("DLR5L17N1", s["dossier_uid"])
        assertEquals(LinkMethod.voteRef, s["method"])
        assertEquals(BigDecimal("1.00"), (s["confidence"] as BigDecimal).setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `unique seance match yields confidence 0_85`() = runBlocking {
        // Dossier carries a séance but no voteRef for this scrutin.
        insertDossier("DLR5L17N1", dossierRawWithSeanceOnly("RU-A"))
        insertScrutin("VTANR5L17V99", seanceRef = "RU-A")

        val result = newLinker().run("test")

        assertEquals(0, result.voteRefMatches)
        assertEquals(1, result.seanceUniqueMatches)
        assertEquals(0, result.seanceAmbiguous)

        val s = assertNotNull(selectScrutin("VTANR5L17V99"))
        assertEquals("DLR5L17N1", s["dossier_uid"])
        assertEquals(LinkMethod.seance, s["method"])
        assertEquals(BigDecimal("0.85"), (s["confidence"] as BigDecimal).setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `ambiguous seance leaves dossier_uid null and writes candidates`() = runBlocking {
        // Two dossiers both reference the same séance.
        insertDossier("DLR5L17N1", dossierRawWithSeanceOnly("RU-NICHE"))
        insertDossier("DLR5L17N2", dossierRawWithSeanceOnly("RU-NICHE"))
        insertScrutin("VTANR5L17V42", seanceRef = "RU-NICHE")

        val result = newLinker().run("test")

        assertEquals(0, result.voteRefMatches)
        assertEquals(0, result.seanceUniqueMatches)
        assertEquals(1, result.seanceAmbiguous)
        assertEquals(2, result.candidateRowsWritten)

        val s = assertNotNull(selectScrutin("VTANR5L17V42"))
        assertNull(s["dossier_uid"], "dossier_uid must remain null when séance is ambiguous")
        assertNull(s["method"])

        val candidates = candidatesForScrutin("VTANR5L17V42")
        assertEquals(2, candidates.size)
        assertEquals(setOf("DLR5L17N1", "DLR5L17N2"), candidates.map { it["dossier_uid"] }.toSet())
        candidates.forEach { c ->
            assertEquals(LinkMethod.seance, c["method"])
            // 0.50 / 2 = 0.25
            assertEquals(BigDecimal("0.25"), (c["confidence"] as BigDecimal).setScale(2, RoundingMode.HALF_UP))
        }
    }

    @Test
    fun `voteRef wins over seance when both are available for the same scrutin`() = runBlocking {
        // Dossier A holds the voteRef on a separate séance.
        insertDossier("DLR5L17N1", dossierRawWithVoteRef("VTANR5L17V1", "RU-VOTE"))
        // Dossier B claims the same séance the scrutin reports — but the voteRef
        // path is exact and must take precedence.
        insertDossier("DLR5L17N2", dossierRawWithSeanceOnly("RU-SEANCE"))
        insertScrutin("VTANR5L17V1", seanceRef = "RU-SEANCE")

        val result = newLinker().run("test")

        assertEquals(1, result.voteRefMatches)
        // Pass-2 still runs for unmatched scrutins, but this one is already linked so it's not counted.
        val s = assertNotNull(selectScrutin("VTANR5L17V1"))
        assertEquals("DLR5L17N1", s["dossier_uid"])
        assertEquals(LinkMethod.voteRef, s["method"])
    }

    @Test
    fun `re-running the linker is idempotent`() = runBlocking {
        insertDossier("DLR5L17N1", dossierRawWithVoteRef("VTANR5L17V1", "RU-A"))
        insertScrutin("VTANR5L17V1", seanceRef = "RU-A")
        insertScrutin("VTANR5L17V_AMB", seanceRef = "RU-NICHE")
        insertDossier("DLR5L17N2", dossierRawWithSeanceOnly("RU-NICHE"))
        insertDossier("DLR5L17N3", dossierRawWithSeanceOnly("RU-NICHE"))

        newLinker().run("run-1")
        val firstRunRows = transaction(db) { Scrutins.selectAll().toList() }
        val firstCandidates = transaction(db) { DossierLinkCandidates.selectAll().count() }

        newLinker().run("run-2")
        val secondRunRows = transaction(db) { Scrutins.selectAll().toList() }
        val secondCandidates = transaction(db) { DossierLinkCandidates.selectAll().count() }

        // Same scrutin row count, same dossier_uid resolutions.
        assertEquals(firstRunRows.size, secondRunRows.size)
        assertEquals(firstCandidates, secondCandidates, "candidate row count must be stable across runs")

        val agentRuns = transaction(db) { AgentRuns.selectAll().count() }
        assertEquals(2L, agentRuns)
    }

    @Test
    fun `dry-run does not write to scrutins or candidates but still records agent_runs`() = runBlocking {
        insertDossier("DLR5L17N1", dossierRawWithVoteRef("VTANR5L17V1", "RU-A"))
        insertScrutin("VTANR5L17V1", seanceRef = "RU-A")

        val result = newLinker(dryRun = true).run("test")

        assertEquals(1, result.voteRefMatches)
        val s = assertNotNull(selectScrutin("VTANR5L17V1"))
        assertNull(s["dossier_uid"], "dry-run must not write dossier_uid")

        val agentRuns = transaction(db) { AgentRuns.selectAll().count() }
        assertEquals(1L, agentRuns)
    }

    @Test
    fun `orphan scrutin (no seance match anywhere) stays unlinked`() = runBlocking {
        insertDossier("DLR5L17N1", dossierRawWithSeanceOnly("RU-A"))
        insertScrutin("VTANR5L17V_ORPHAN", seanceRef = "RU-NOWHERE")

        val result = newLinker().run("test")

        assertEquals(0, result.voteRefMatches)
        assertEquals(0, result.seanceUniqueMatches)
        assertEquals(1, result.orphans)

        val s = assertNotNull(selectScrutin("VTANR5L17V_ORPHAN"))
        assertNull(s["dossier_uid"])
    }
}
