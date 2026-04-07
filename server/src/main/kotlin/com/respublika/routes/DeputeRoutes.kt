package com.respublika.routes

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*

fun Route.deputeRoutes() {

    // GET /api/deputes/random — Député aléatoire avec stats
    get("/api/deputes/random") {
        val result = dbQuery {
            val total = Deputes.selectAll().count()
            if (total == 0L) return@dbQuery null

            val randomOffset = (Math.random() * total).toLong()
            val depute = Deputes.leftJoin(Organes, { groupePolitiqueUid }, { uid })
                .selectAll()
                .limit(1).offset(randomOffset)
                .singleOrNull() ?: return@dbQuery null

            val deputeId = depute[Deputes.idAn]

            val votes = VotesIndividuels.selectAll()
                .where { VotesIndividuels.deputeId eq deputeId }
            val stats = votes.groupBy { it[VotesIndividuels.position] }
                .mapValues { it.value.size }

            val groupeUid = depute[Deputes.groupePolitiqueUid]
            val nbDissidences = if (groupeUid != null) {
                (VotesIndividuels.join(VotesGroupes, JoinType.INNER, additionalConstraint = {
                    (VotesIndividuels.scrutinUid eq VotesGroupes.scrutinUid) and
                    (VotesGroupes.groupeUid eq groupeUid)
                }))
                    .selectAll()
                    .where { VotesIndividuels.deputeId eq deputeId }
                    .andWhere { VotesIndividuels.position neq VotesGroupes.positionMajoritaire }
                    .count()
            } else 0L

            val totalVotes = stats.values.sum()

            buildJsonObject {
                put("id_an", depute[Deputes.idAn])
                put("nom", depute[Deputes.civPrenomNom])
                putJsonObject("groupe") {
                    put("uid", groupeUid ?: "")
                    put("libelle", depute.getOrNull(Organes.libelle) ?: "")
                    put("abrege", depute.getOrNull(Organes.libelleAbrege) ?: "")
                    put("couleur", depute.getOrNull(Organes.couleurAssociee) ?: "")
                }
                putJsonObject("stats_votes") {
                    put("total", totalVotes)
                    put("pour", stats["pour"] ?: 0)
                    put("contre", stats["contre"] ?: 0)
                    put("abstention", stats["abstention"] ?: 0)
                    put("non_votant", stats["non_votant"] ?: 0)
                }
                put("nb_dissidences", nbDissidences)
                put("participation_pct", if (totalVotes > 0) {
                    // rough estimate: total scrutins as baseline
                    val totalScrutins = Scrutins.selectAll().count()
                    if (totalScrutins > 0) ((totalVotes.toDouble() / totalScrutins) * 100).toInt() else 0
                } else 0)
                put("loyaute_pct", if (totalVotes > 0 && nbDissidences >= 0) {
                    ((1.0 - nbDissidences.toDouble() / totalVotes) * 100).toInt()
                } else 0)
            }
        }

        if (result == null) {
            call.respondText("Aucun député trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    // GET /api/deputes — Liste paginée avec filtres
    get("/api/deputes") {
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()
        val groupeFilter = call.parameters["groupe"]
        val nomFilter = call.parameters["nom"]

        val result = dbQuery {
            var query = Deputes.leftJoin(Organes, { groupePolitiqueUid }, { uid }).selectAll()

            if (!groupeFilter.isNullOrBlank()) {
                query = query.andWhere { Deputes.groupePolitiqueUid eq groupeFilter }
            }
            if (!nomFilter.isNullOrBlank()) {
                query = query.andWhere { Deputes.civPrenomNom.lowerCase() like "%${nomFilter.lowercase()}%" }
            }

            val total = query.count()
            val deputes = query
                .orderBy(Deputes.civPrenomNom, SortOrder.ASC)
                .limit(limit).offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("id_an", row[Deputes.idAn])
                        put("nom", row[Deputes.civPrenomNom])
                        put("groupe_uid", row[Deputes.groupePolitiqueUid] ?: "")
                        put("groupe_libelle", row.getOrNull(Organes.libelle) ?: "")
                        put("groupe_abrege", row.getOrNull(Organes.libelleAbrege) ?: "")
                        put("couleur", row.getOrNull(Organes.couleurAssociee) ?: "")
                    }
                }

            buildJsonObject {
                put("page", page)
                put("limit", limit)
                put("total", total)
                put("total_pages", (total + limit - 1) / limit)
                put("deputes", JsonArray(deputes))
            }
        }

        call.respond(result)
    }

    // GET /api/deputes/{id} — Profil complet d'un député
    get("/api/deputes/{id}") {
        val deputeId = call.parameters["id"] ?: return@get call.respondText(
            "id manquant", status = HttpStatusCode.BadRequest
        )

        val result = dbQuery {
            val depute = Deputes.selectAll().where { Deputes.idAn eq deputeId }.singleOrNull()
                ?: return@dbQuery null

            val groupeUid = depute[Deputes.groupePolitiqueUid]
            val groupe = groupeUid?.let { gid ->
                Organes.selectAll().where { Organes.uid eq gid }.singleOrNull()
            }

            // Stats de vote
            val votes = VotesIndividuels.selectAll()
                .where { VotesIndividuels.deputeId eq deputeId }

            val stats = votes.groupBy { it[VotesIndividuels.position] }
                .mapValues { it.value.size }

            // Déports
            val nbDeports = Deports.selectAll()
                .where { Deports.deputeId eq deputeId }
                .count()

            buildJsonObject {
                put("id_an", depute[Deputes.idAn])
                put("nom", depute[Deputes.civPrenomNom])
                putJsonObject("groupe") {
                    put("uid", groupeUid ?: "")
                    put("libelle", groupe?.get(Organes.libelle) ?: "")
                    put("abrege", groupe?.get(Organes.libelleAbrege) ?: "")
                    put("couleur", groupe?.get(Organes.couleurAssociee) ?: "")
                }
                putJsonObject("stats_votes") {
                    put("total", stats.values.sum())
                    put("pour", stats["pour"] ?: 0)
                    put("contre", stats["contre"] ?: 0)
                    put("abstention", stats["abstention"] ?: 0)
                    put("non_votant", stats["non_votant"] ?: 0)
                }
                put("nb_deports", nbDeports)
            }
        }

        if (result == null) {
            call.respondText("Député non trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    // GET /api/deputes/{id}/votes — Historique de vote
    get("/api/deputes/{id}/votes") {
        val deputeId = call.parameters["id"] ?: return@get call.respondText(
            "id manquant", status = HttpStatusCode.BadRequest
        )
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()

        val result = dbQuery {
            Deputes.selectAll().where { Deputes.idAn eq deputeId }.singleOrNull()
                ?: return@dbQuery null

            val query = (VotesIndividuels.join(Scrutins, JoinType.INNER, VotesIndividuels.scrutinUid, Scrutins.uid))
                .selectAll()
                .where { VotesIndividuels.deputeId eq deputeId }

            val total = query.count()
            val votes = query
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("scrutin_uid", row[Scrutins.uid])
                        put("titre", row[Scrutins.titre])
                        put("date_vote", row[Scrutins.dateVote])
                        put("sort_scrutin", row[Scrutins.sort] ?: "")
                        put("position", row[VotesIndividuels.position])
                        put("par_delegation", row[VotesIndividuels.parDelegation])
                    }
                }

            buildJsonObject {
                put("page", page)
                put("limit", limit)
                put("total", total)
                put("votes", JsonArray(votes))
            }
        }

        if (result == null) {
            call.respondText("Député non trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }

    // GET /api/deputes/{id}/top-dissidences — Votes contre son groupe
    get("/api/deputes/{id}/top-dissidences") {
        val deputeId = call.parameters["id"] ?: return@get call.respondText(
            "id manquant", status = HttpStatusCode.BadRequest
        )
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 50)

        val result = dbQuery {
            val depute = Deputes.selectAll().where { Deputes.idAn eq deputeId }.singleOrNull()
                ?: return@dbQuery null

            val groupeUid = depute[Deputes.groupePolitiqueUid]
                ?: return@dbQuery buildJsonObject {
                    put("depute_id", deputeId)
                    put("nom", depute[Deputes.civPrenomNom])
                    put("message", "Aucun groupe politique associé")
                    put("dissidences", JsonArray(emptyList()))
                }

            // Trouver les scrutins où le député a voté différemment de la position majoritaire de son groupe
            val dissidences = (VotesIndividuels.join(Scrutins, JoinType.INNER, VotesIndividuels.scrutinUid, Scrutins.uid))
                .join(VotesGroupes, JoinType.INNER, additionalConstraint = {
                    (VotesIndividuels.scrutinUid eq VotesGroupes.scrutinUid) and
                    (VotesGroupes.groupeUid eq groupeUid)
                })
                .selectAll()
                .where { VotesIndividuels.deputeId eq deputeId }
                .andWhere { VotesIndividuels.position neq VotesGroupes.positionMajoritaire }
                .orderBy(Scrutins.dateVote, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    buildJsonObject {
                        put("scrutin_uid", row[Scrutins.uid])
                        put("titre", row[Scrutins.titre])
                        put("date_vote", row[Scrutins.dateVote])
                        put("sort_scrutin", row[Scrutins.sort] ?: "")
                        put("position_depute", row[VotesIndividuels.position])
                        put("position_groupe", row[VotesGroupes.positionMajoritaire] ?: "")
                    }
                }

            buildJsonObject {
                put("depute_id", deputeId)
                put("nom", depute[Deputes.civPrenomNom])
                put("groupe_uid", groupeUid)
                put("nb_dissidences", dissidences.size)
                put("dissidences", JsonArray(dissidences))
            }
        }

        if (result == null) {
            call.respondText("Député non trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }
}
