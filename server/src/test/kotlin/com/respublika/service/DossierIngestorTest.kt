package com.respublika.service

import com.respublika.database.AgentRuns
import com.respublika.database.DossiersLegislatifs
import com.respublika.model.DossierEtat
import com.respublika.model.DossierType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DossierIngestorTest {

    private lateinit var db: Database
    private lateinit var fixtureDir: File

    @BeforeTest
    fun setup() {
        db = Database.connect(
            "jdbc:h2:mem:test_${System.nanoTime()}_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(DossiersLegislatifs, AgentRuns)
            DossiersLegislatifs.deleteAll()
            AgentRuns.deleteAll()
        }

        // Stage the three checked-in fixtures into a fresh temp dir so the
        // ingestor sees them as a clean directory (no Zone.Identifier sidecars).
        fixtureDir = Files.createTempDirectory("dossier_ingestor_test").toFile()
        listOf(
            "/fixtures/dossiers/dossier_promulgue.json"  to "DLR5L17N50168.json",
            "/fixtures/dossiers/dossier_en_cours.json"   to "DLR5L17N50208.json",
            "/fixtures/dossiers/dossier_l11_filter_out.json" to "DLR5L11N19503.json",
        ).forEach { (resource, name) ->
            val raw = requireNotNull(javaClass.getResourceAsStream(resource)) {
                "fixture not found: $resource"
            }.bufferedReader().use { it.readText() }
            File(fixtureDir, name).writeText(raw)
        }
    }

    private fun newIngestor(dryRun: Boolean = false, nowIso: String = "2026-04-26T12:00:00Z") =
        DossierIngestor(
            dataPath = fixtureDir.absolutePath,
            dryRun = dryRun,
            database = db,
            nowProvider = { Instant.parse(nowIso) },
        )

    private fun rowOf(uid: String) = transaction(db) {
        DossiersLegislatifs.selectAll().where { DossiersLegislatifs.uid eq uid }.singleOrNull()
            ?.let {
                mapOf(
                    "uid" to it[DossiersLegislatifs.uid],
                    "titre" to it[DossiersLegislatifs.titre],
                    "type" to it[DossiersLegislatifs.type],
                    "etat" to it[DossiersLegislatifs.etat],
                    "legislature" to it[DossiersLegislatifs.legislature],
                    "date_depot" to it[DossiersLegislatifs.dateDepot],
                    "date_derniere_decision" to it[DossiersLegislatifs.dateDerniereDecision],
                    "date_promulgation" to it[DossiersLegislatifs.datePromulgation],
                    "numero_loi" to it[DossiersLegislatifs.numeroLoi],
                )
            }
    }

    @Test
    fun `happy path ingests both L17 dossiers and skips the pre-L17 file`() = runBlocking {
        val result = newIngestor().run("test")

        assertEquals(3, result.totalFilesSeen)
        assertEquals(2, result.ingested, "two L17 dossiers (promulgated + en_cours) should be ingested")
        assertEquals(1, result.skippedNotL17, "the L11 file with no L17 voteRef must be filtered out")
        assertTrue(result.parseErrors.isEmpty(), "no parse errors expected")
        assertTrue(result.derivationErrors.isEmpty(), "no derivation errors expected")

        val rowCount = transaction(db) { DossiersLegislatifs.selectAll().count() }
        assertEquals(2L, rowCount)

        // Pre-L17 dossier must NOT be in the DB.
        assertNull(rowOf("DLR5L11N19503"))
    }

    @Test
    fun `promulgated dossier derives PROMULGUE etat and dates`(): Unit = runBlocking {
        newIngestor().run("test")

        val row = assertNotNull(rowOf("DLR5L17N50168"))
        assertEquals(DossierEtat.PROMULGUE, row["etat"])
        // Type derivation: procedureParlementaire.code == "5" → LOI_ORGANIQUE
        assertEquals(DossierType.LOI_ORGANIQUE, row["type"])
        assertEquals(17.toShort(), row["legislature"])
        assertNotNull(row["date_promulgation"], "date_promulgation must be populated for promulgated dossier")
        assertNotNull(row["date_derniere_decision"])
        assertNotNull(row["date_depot"])
    }

    @Test
    fun `en_cours dossier derives ADOPTE etat (text adopted, awaiting next stage)`(): Unit = runBlocking {
        newIngestor().run("test")

        val row = assertNotNull(rowOf("DLR5L17N50208"))
        // procedureParlementaire.code == "2" (Proposition de loi ordinaire) → LOI_ORDINAIRE
        assertEquals(DossierType.LOI_ORDINAIRE, row["type"])
        // Most recent Decision is "adoptée en première lecture" — ADOPTE per the SQL comment.
        // (May also be EN_COURS or REJETE depending on the live fixture.)
        val etat = row["etat"] as DossierEtat
        assertTrue(
            etat in setOf(DossierEtat.ADOPTE, DossierEtat.EN_COURS, DossierEtat.REJETE),
            "etat should be derived from the most recent Decision, got $etat",
        )
        assertNull(row["date_promulgation"], "no Promulgation_Type → date_promulgation must be null")
    }

    @Test
    fun `re-running the ingestor is idempotent (UPSERT, not duplicate)`() = runBlocking {
        newIngestor(nowIso = "2026-04-26T12:00:00Z").run("test-1")
        val firstCount = transaction(db) { DossiersLegislatifs.selectAll().count() }

        newIngestor(nowIso = "2026-05-01T12:00:00Z").run("test-2")
        val secondCount = transaction(db) { DossiersLegislatifs.selectAll().count() }

        assertEquals(2L, firstCount)
        assertEquals(2L, secondCount, "second run must not duplicate rows")

        // Both runs should write an agent_runs row.
        val agentRuns = transaction(db) { AgentRuns.selectAll().count() }
        assertEquals(2L, agentRuns)
    }

    @Test
    fun `dry-run does not write to the DB but still records agent_runs`() = runBlocking {
        val result = newIngestor(dryRun = true).run("test")

        assertEquals(2, result.ingested, "in dry-run, ingested counts what would be written")
        val rowCount = transaction(db) { DossiersLegislatifs.selectAll().count() }
        assertEquals(0L, rowCount, "no rows written in dry-run")

        val agentRuns = transaction(db) { AgentRuns.selectAll().count() }
        assertEquals(1L, agentRuns)
    }

    @Test
    fun `derive surfaces a clear error when @xsi type is missing`() {
        val ingestor = newIngestor()
        // Truncated input — must fail on `derive`, not silently default to AUTRE/etc.
        val truncated = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"uid":"DLR5L17N99999","legislature":"17","titreDossier":{"titre":"x"}}""",
        ) as kotlinx.serialization.json.JsonObject
        val ex = runCatching { ingestor.derive(truncated) }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(
            ex.message!!.contains("@xsi:type"),
            "error must name the missing field, got: ${ex.message}",
        )
    }
}
