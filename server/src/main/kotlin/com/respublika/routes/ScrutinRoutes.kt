package com.respublika.routes

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*

fun Route.scrutinRoutes() {

    // GET /api/scrutins/latest — Derniers scrutins (paginé)
    get("/api/scrutins/latest") {
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()

        val result = dbQuery {
            val baseQuery = Scrutins.selectAll()
            val total = baseQuery.count()
            val scrutins = baseQuery
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("uid", row[Scrutins.uid])
                        put("titre", row[Scrutins.titre])
                        put("date_vote", row[Scrutins.dateVote])
                        put("sort", row[Scrutins.sort] ?: "")
                        put("nombre_votants", row[Scrutins.nombreVotants] ?: 0)
                    }
                }

            buildJsonObject {
                put("page", page)
                put("limit", limit)
                put("total", total)
                put("total_pages", (total + limit - 1) / limit)
                put("scrutins", JsonArray(scrutins))
            }
        }

        call.respond(result)
    }

    // GET /api/scrutins/{uid}/full — Détail complet avec synthèse par groupe
    get("/api/scrutins/{uid}/full") {
        val uid = call.parameters["uid"] ?: return@get call.respondText(
            "uid manquant", status = HttpStatusCode.BadRequest
        )

        val result = dbQuery {
            val scrutin = Scrutins.selectAll().where { Scrutins.uid eq uid }.singleOrNull()
                ?: return@dbQuery null

            // Votes agrégés par groupe
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

                    // Synthèse textuelle
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

            buildJsonObject {
                put("uid", scrutin[Scrutins.uid])
                put("titre", scrutin[Scrutins.titre])
                put("date_vote", scrutin[Scrutins.dateVote])
                put("sort", scrutin[Scrutins.sort] ?: "")
                put("nombre_votants", scrutin[Scrutins.nombreVotants] ?: 0)
                put("suffrages_exprimes", scrutin[Scrutins.suffragesExprimes] ?: 0)
                put("nbre_suffrages_requis", scrutin[Scrutins.nbreSuffragesRequis] ?: 0)
                put("groupes", JsonArray(groupesVotes))
            }
        }

        if (result == null) {
            call.respondText("Scrutin non trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
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

        val result = dbQuery {
            val baseQuery = Scrutins.selectAll()
                .where { Scrutins.titre.lowerCase() like "%${query.lowercase()}%" }

            val total = baseQuery.count()
            val scrutins = baseQuery
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("uid", row[Scrutins.uid])
                        put("titre", row[Scrutins.titre])
                        put("date_vote", row[Scrutins.dateVote])
                        put("sort", row[Scrutins.sort] ?: "")
                        put("nombre_votants", row[Scrutins.nombreVotants] ?: 0)
                    }
                }

            buildJsonObject {
                put("query", query)
                put("page", page)
                put("limit", limit)
                put("total", total)
                put("total_pages", (total + limit - 1) / limit)
                put("scrutins", JsonArray(scrutins))
            }
        }

        call.respond(result)
    }
}
