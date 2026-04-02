package com.respublika.routes

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*

fun Route.ethiqueRoutes() {

    // GET /api/deports/latest — Derniers déports signalés
    get("/api/deports/latest") {
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

        val result = dbQuery {
            val deports = (Deports.join(Deputes, JoinType.LEFT, Deports.deputeId, Deputes.idAn))
                .selectAll()
                .orderBy(Deports.uid, SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    buildJsonObject {
                        put("uid", row[Deports.uid])
                        put("depute_id", row[Deports.deputeId])
                        put("depute_nom", row.getOrNull(Deputes.civPrenomNom) ?: "")
                        put("libelle_portee", row[Deports.libellePortee])
                        put("explication_html", row[Deports.explicationHtml])
                    }
                }

            buildJsonObject {
                put("count", deports.size)
                put("deports", JsonArray(deports))
            }
        }

        call.respond(result)
    }

    // GET /api/deputes/{id}/deports — Déports d'un député (Badge Éthique+)
    get("/api/deputes/{id}/deports") {
        val deputeId = call.parameters["id"] ?: return@get call.respondText(
            "id manquant", status = HttpStatusCode.BadRequest
        )

        val result = dbQuery {
            val depute = Deputes.selectAll().where { Deputes.idAn eq deputeId }.singleOrNull()
                ?: return@dbQuery null

            val deports = Deports.selectAll()
                .where { Deports.deputeId eq deputeId }
                .orderBy(Deports.uid, SortOrder.DESC)
                .map { row ->
                    buildJsonObject {
                        put("uid", row[Deports.uid])
                        put("libelle_portee", row[Deports.libellePortee])
                        put("explication_html", row[Deports.explicationHtml])
                    }
                }

            buildJsonObject {
                put("depute_id", deputeId)
                put("nom", depute[Deputes.civPrenomNom])
                put("nb_deports", deports.size)
                put("badge_ethique_plus", deports.isNotEmpty())
                put("deports", JsonArray(deports))
            }
        }

        if (result == null) {
            call.respondText("Député non trouvé", status = HttpStatusCode.NotFound)
        } else {
            call.respond(result)
        }
    }
}
