package com.respublika.routes

import com.respublika.database.DossierLinkCandidates
import com.respublika.database.DossiersLegislatifs
import com.respublika.database.Scrutins
import com.respublika.model.DossierEtat
import com.respublika.model.DossierType
import com.respublika.model.LinkMethod
import com.respublika.repository.DossierRepository
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DossierRoutesTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private lateinit var db: Database

    private fun setupTestDb() {
        db = Database.connect(
            "jdbc:h2:mem:test_${System.nanoTime()}_${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction(db) {
            SchemaUtils.create(Scrutins, DossiersLegislatifs, DossierLinkCandidates)
            Scrutins.deleteAll()
            DossiersLegislatifs.deleteAll()
            DossierLinkCandidates.deleteAll()
        }
    }

    private fun insertDossier(
        uid: String,
        titre: String,
        type: DossierType = DossierType.LOI_ORDINAIRE,
        etat: DossierEtat = DossierEtat.EN_COURS,
        dateDecision: LocalDate? = null,
        datePromulgation: LocalDate? = null,
    ) = transaction(db) {
        val now = Instant.parse("2026-04-26T12:00:00Z")
        DossiersLegislatifs.insert {
            it[DossiersLegislatifs.uid] = uid
            it[DossiersLegislatifs.titre] = titre
            it[titreCourt] = null
            it[DossiersLegislatifs.type] = type
            it[DossiersLegislatifs.etat] = etat
            it[legislature] = 17
            it[dateDepot] = LocalDate.parse("2025-01-01")
            it[dateDerniereDecision] = dateDecision
            it[DossiersLegislatifs.datePromulgation] = datePromulgation
            it[numeroLoi] = if (datePromulgation != null) "2025-001" else null
            it[themes] = null
            it[rawJson] = json.parseToJsonElement("""{"uid":"$uid"}""")
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    private fun insertScrutinForDossier(scrutinUid: String, dossierUid: String?) = transaction(db) {
        Scrutins.insert {
            it[uid] = scrutinUid
            it[titre] = "test scrutin $scrutinUid"
            it[dateVote] = "2025-01-15"
            it[Scrutins.dossierUid] = dossierUid
            it[dossierLinkMethod] = if (dossierUid != null) LinkMethod.voteRef else null
            it[dossierLinkConfidence] = if (dossierUid != null) BigDecimal("1.00") else null
        }
    }

    private fun ApplicationTestBuilder.configureApp() {
        application {
            install(ContentNegotiation) { json(json) }
            routing { dossierRoutes(DossierRepository(db)) }
        }
    }

    // ── List endpoint ────────────────────────────────────────────────────

    @Test
    fun `GET dossiers returns paginated results sorted by date_derniere_decision DESC NULLS LAST`() = testApplication {
        setupTestDb()
        insertDossier("DLR-A", "Loi A", dateDecision = LocalDate.parse("2025-03-01"))
        insertDossier("DLR-B", "Loi B", dateDecision = LocalDate.parse("2025-02-01"))
        insertDossier("DLR-C", "Loi C", dateDecision = null) // dormant
        configureApp()

        val resp = client.get("/api/dossiers?limit=10")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(3L, body["total"]!!.jsonPrimitive.long)
        val items = body["dossiers"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("DLR-A", "DLR-B", "DLR-C"), items.map { it["uid"]!!.jsonPrimitive.content })
    }

    @Test
    fun `GET dossiers filters by etat (comma-separated)`() = testApplication {
        setupTestDb()
        insertDossier("DLR-EN", "En cours", etat = DossierEtat.EN_COURS)
        insertDossier("DLR-AD", "Adoptée", etat = DossierEtat.ADOPTE)
        insertDossier("DLR-PR", "Promulguée", etat = DossierEtat.PROMULGUE, datePromulgation = LocalDate.parse("2025-04-01"))
        configureApp()

        val resp = client.get("/api/dossiers?etat=ADOPTE,PROMULGUE")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val uids = body["dossiers"]!!.jsonArray.map { it.jsonObject["uid"]!!.jsonPrimitive.content }.toSet()
        assertEquals(setOf("DLR-AD", "DLR-PR"), uids)
    }

    @Test
    fun `GET dossiers filters by since date and applies q text search`() = testApplication {
        setupTestDb()
        insertDossier("DLR-OLD", "Ancienne", dateDecision = LocalDate.parse("2024-12-01"))
        insertDossier("DLR-NEW", "Nouvelle loi importante", dateDecision = LocalDate.parse("2025-04-01"))
        insertDossier("DLR-OTH", "Autre nouvelle", dateDecision = LocalDate.parse("2025-04-15"))
        configureApp()

        val byDate = client.get("/api/dossiers?since=2025-01-01")
        val byDateBody = json.parseToJsonElement(byDate.bodyAsText()).jsonObject
        assertEquals(2L, byDateBody["total"]!!.jsonPrimitive.long)

        val byQuery = client.get("/api/dossiers?q=importante")
        val byQueryBody = json.parseToJsonElement(byQuery.bodyAsText()).jsonObject
        assertEquals(1L, byQueryBody["total"]!!.jsonPrimitive.long)
        assertEquals("DLR-NEW", byQueryBody["dossiers"]!!.jsonArray.single().jsonObject["uid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GET dossiers returns 400 invalid_since on malformed since param`() = testApplication {
        setupTestDb()
        configureApp()
        val resp = client.get("/api/dossiers?since=not-a-date")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("invalid_since", body["error"]!!.jsonPrimitive.content)
    }

    // ── Detail endpoint ──────────────────────────────────────────────────

    @Test
    fun `GET dossier detail includes attached scrutins sorted by date DESC`() = testApplication {
        setupTestDb()
        insertDossier("DLR-X", "Test loi", etat = DossierEtat.ADOPTE, dateDecision = LocalDate.parse("2025-04-01"))
        // Insert dates out of order; the route must sort DESC.
        transaction(db) {
            Scrutins.insert {
                it[uid] = "VT-2"; it[titre] = "scrutin 2"; it[dateVote] = "2025-04-10"; it[dossierUid] = "DLR-X"
                it[dossierLinkMethod] = LinkMethod.voteRef; it[dossierLinkConfidence] = BigDecimal("1.00")
            }
            Scrutins.insert {
                it[uid] = "VT-1"; it[titre] = "scrutin 1"; it[dateVote] = "2025-04-01"; it[dossierUid] = "DLR-X"
                it[dossierLinkMethod] = LinkMethod.seance; it[dossierLinkConfidence] = BigDecimal("0.85")
            }
            // Unlinked scrutin must NOT appear under this dossier.
            Scrutins.insert {
                it[uid] = "VT-X"; it[titre] = "no link"; it[dateVote] = "2025-04-15"; it[dossierUid] = null
            }
        }
        configureApp()

        val resp = client.get("/api/dossiers/DLR-X")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("DLR-X", body["uid"]!!.jsonPrimitive.content)
        assertEquals("ADOPTE", body["etat"]!!.jsonPrimitive.content)
        val scrutins = body["scrutins"]!!.jsonArray.map { it.jsonObject["uid"]!!.jsonPrimitive.content }
        assertEquals(listOf("VT-2", "VT-1"), scrutins, "must be DESC by date_vote and exclude unlinked scrutins")
    }

    @Test
    fun `GET dossier detail returns 404 dossier_not_found for unknown uid`() = testApplication {
        setupTestDb()
        configureApp()
        val resp = client.get("/api/dossiers/UNKNOWN")
        assertEquals(HttpStatusCode.NotFound, resp.status)
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("dossier_not_found", body["error"]!!.jsonPrimitive.content)
        assertEquals("UNKNOWN", body["uid"]!!.jsonPrimitive.content)
    }

    // ── /scrutins sub-endpoint ───────────────────────────────────────────

    @Test
    fun `GET dossier scrutins paginates and returns 404 for unknown dossier`() = testApplication {
        setupTestDb()
        insertDossier("DLR-Y", "Big loi")
        repeat(5) { i -> insertScrutinForDossier("VT-Y$i", "DLR-Y") }
        configureApp()

        val resp = client.get("/api/dossiers/DLR-Y/scrutins?limit=2&page=1")
        val body = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(5L, body["total"]!!.jsonPrimitive.long)
        assertEquals(2, body["scrutins"]!!.jsonArray.size)

        val notFound = client.get("/api/dossiers/UNKNOWN/scrutins")
        assertEquals(HttpStatusCode.NotFound, notFound.status)
    }

    // ── List endpoint exposes nb_scrutins per dossier ────────────────────

    @Test
    fun `list endpoint returns nb_scrutins per dossier`() = testApplication {
        setupTestDb()
        insertDossier("DLR-Z", "Linked loi", dateDecision = LocalDate.parse("2025-04-01"))
        insertDossier("DLR-EMPTY", "No scrutins yet", dateDecision = LocalDate.parse("2025-04-02"))
        insertScrutinForDossier("VT-Z1", "DLR-Z")
        insertScrutinForDossier("VT-Z2", "DLR-Z")
        configureApp()

        val resp = client.get("/api/dossiers")
        assertEquals(HttpStatusCode.OK, resp.status)
        val byUid = json.parseToJsonElement(resp.bodyAsText()).jsonObject["dossiers"]!!
            .jsonArray.associateBy { it.jsonObject["uid"]!!.jsonPrimitive.content }
        assertEquals(2, byUid["DLR-Z"]!!.jsonObject["nb_scrutins"]!!.jsonPrimitive.int)
        assertEquals(0, byUid["DLR-EMPTY"]!!.jsonObject["nb_scrutins"]!!.jsonPrimitive.int)
    }
}
