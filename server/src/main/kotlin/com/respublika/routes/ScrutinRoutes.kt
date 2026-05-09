package com.respublika.routes

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.model.VoteInsightRow
import com.respublika.model.toJsonElement
import com.respublika.model.toResponse
import com.respublika.repository.DossierRepository
import com.respublika.repository.VoteInsightRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*

private data class ScrutinResumeRow(
    val uid: String,
    val titre: String,
    val dateVote: String,
    val sort: String?,
    val nombreVotants: Int?,
)

private fun ScrutinResumeRow.toResumeJson(insight: VoteInsightRow?): JsonObject = buildJsonObject {
    put("uid", uid)
    put("titre", titre)
    put("date_vote", dateVote)
    put("sort", sort ?: "")
    put("nombre_votants", nombreVotants ?: 0)
    put("insights", insight?.let { it.toResponse().toJsonElement() } ?: JsonNull)
}

// `database` is null in production (uses Exposed's default DB). It is only set in
// integration tests, so the test's isolated H2 instance is used end-to-end instead of
// whichever DB happened to be cached in Exposed's per-thread TransactionManager.
fun Route.scrutinRoutes(
    insightRepository: VoteInsightRepository = VoteInsightRepository(),
    database: Database? = null,
    dossierRepository: DossierRepository = DossierRepository(database),
) {

    // GET /api/scrutins/latest — Derniers scrutins (paginé)
    get("/api/scrutins/latest") {
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()

        val (total, rows) = dbQuery(database) {
            val baseQuery = Scrutins.selectAll()
            val total = baseQuery.count()
            val rows = baseQuery
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { it.toResumeRow() }
            total to rows
        }

        val insightsByUid = insightRepository.findByScrutinUids(rows.map { it.uid })

        call.respond(buildJsonObject {
            put("page", page)
            put("limit", limit)
            put("total", total)
            put("total_pages", (total + limit - 1) / limit)
            put("scrutins", JsonArray(rows.map { it.toResumeJson(insightsByUid[it.uid]) }))
        })
    }

    // GET /api/scrutins/{uid}/full — Détail complet avec synthèse par groupe
    get("/api/scrutins/{uid}/full") {
        val uid = call.parameters["uid"] ?: return@get call.respondText(
            "uid manquant", status = HttpStatusCode.BadRequest
        )

        val result = dbQuery(database) {
            val scrutin = Scrutins.selectAll().where { Scrutins.uid eq uid }.singleOrNull()
                ?: return@dbQuery null

            val groupesVotes = (VotesGroupes.join(Organes, JoinType.INNER, VotesGroupes.groupeUid, Organes.uid))
                .selectAll()
                .where { VotesGroupes.scrutinUid eq uid }
                .orderBy(VotesGroupes.nombreMembres, SortOrder.DESC)
                .map { row ->
                    val membres = row[VotesGroupes.nombreMembres]
                    val pour = row[VotesGroupes.pour]
                    val contre = row[VotesGroupes.contre]
                    val abstentions = row[VotesGroupes.abstentions]
                    val votants = pour + contre + abstentions
                    val participation = if (membres > 0) ((votants.toDouble() / membres) * 100).toInt() else 0

                    val positionMaj = row[VotesGroupes.positionMajoritaire] ?: ""
                    val pctMaj = if (votants > 0) {
                        when (positionMaj) {
                            "pour" -> (pour.toDouble() / votants * 100).toInt()
                            "contre" -> (contre.toDouble() / votants * 100).toInt()
                            "abstention" -> (abstentions.toDouble() / votants * 100).toInt()
                            else -> 0
                        }
                    } else 0
                    val abrege = row[Organes.libelleAbrege] ?: row[Organes.libelle]
                    val synthese = "${pctMaj}% du groupe $abrege a voté ${positionMaj.uppercase()}"

                    buildJsonObject {
                        put("groupe_uid", row[Organes.uid])
                        put("groupe_libelle", row[Organes.libelle])
                        put("groupe_abrege", row[Organes.libelleAbrege] ?: "")
                        put("couleur", row[Organes.couleurAssociee] ?: "")
                        put("nombre_membres", membres)
                        put("position_majoritaire", positionMaj)
                        put("pour", pour)
                        put("contre", contre)
                        put("abstentions", abstentions)
                        put("non_votants", row[VotesGroupes.nonVotants])
                        put("participation_pct", participation)
                        put("synthese", synthese)
                    }
                }

            scrutin to groupesVotes
        }

        if (result == null) {
            call.respondText("Scrutin non trouvé", status = HttpStatusCode.NotFound)
            return@get
        }

        val (scrutin, groupesVotes) = result
        val insight = insightRepository.findByScrutinUid(uid)
        // Only resolve the parent dossier when the scrutin actually has a link —
        // saves a JOIN query for unlinked scrutins (97% of the corpus on first
        // ingest, per analysis/04_join_strategy.md).
        val dossierRef = scrutin[Scrutins.dossierUid]
            ?.let { dossierRepository.refForScrutin(uid) }

        call.respond(buildJsonObject {
            put("uid", scrutin[Scrutins.uid])
            put("titre", scrutin[Scrutins.titre])
            put("date_vote", scrutin[Scrutins.dateVote])
            put("sort", scrutin[Scrutins.sort] ?: "")
            put("nombre_votants", scrutin[Scrutins.nombreVotants] ?: 0)
            put("suffrages_exprimes", scrutin[Scrutins.suffragesExprimes] ?: 0)
            put("nbre_suffrages_requis", scrutin[Scrutins.nbreSuffragesRequis] ?: 0)
            put("dossier_uid", scrutin[Scrutins.dossierUid]?.let { JsonPrimitive(it) } ?: JsonNull)
            put("dossier", dossierRef?.toJsonElement() ?: JsonNull)
            put("groupes", JsonArray(groupesVotes))
            put("insights", insight?.let { it.toResponse().toJsonElement() } ?: JsonNull)
        })
    }

    // GET /api/scrutins/recherche?q=retraites — Recherche textuelle
    get("/api/scrutins/recherche") {
        val query = call.parameters["q"]
        if (query.isNullOrBlank()) {
            call.respondText("Paramètre 'q' requis", status = HttpStatusCode.BadRequest)
            return@get
        }
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()

        val (total, rows) = dbQuery(database) {
            val baseQuery = Scrutins.selectAll()
                .where { Scrutins.titre.lowerCase() like "%${query.lowercase()}%" }
            val total = baseQuery.count()
            val rows = baseQuery
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { it.toResumeRow() }
            total to rows
        }

        val insightsByUid = insightRepository.findByScrutinUids(rows.map { it.uid })

        call.respond(buildJsonObject {
            put("query", query)
            put("page", page)
            put("limit", limit)
            put("total", total)
            put("total_pages", (total + limit - 1) / limit)
            put("scrutins", JsonArray(rows.map { it.toResumeJson(insightsByUid[it.uid]) }))
        })
    }

    // GET /api/scrutins/{uid}/insights — Insights dérivés pour un scrutin
    get("/api/scrutins/{uid}/insights") {
        val uid = call.parameters["uid"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            buildJsonObject { put("error", "uid_required") },
        )

        val scrutinExists = dbQuery(database) {
            Scrutins.selectAll().where { Scrutins.uid eq uid }.limit(1).any()
        }
        if (!scrutinExists) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "scrutin_not_found")
                put("uid", uid)
            })
            return@get
        }

        val insight = insightRepository.findByScrutinUid(uid)
        if (insight == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "insights_not_computed")
                put("uid", uid)
            })
            return@get
        }

        call.respond(insight.toResponse().toJsonElement())
    }
}

private fun ResultRow.toResumeRow(): ScrutinResumeRow = ScrutinResumeRow(
    uid = this[Scrutins.uid],
    titre = this[Scrutins.titre],
    dateVote = this[Scrutins.dateVote],
    sort = this[Scrutins.sort],
    nombreVotants = this[Scrutins.nombreVotants],
)
