// server/src/main/kotlin/com/respublika/routes/DossierRoutes.kt
package com.respublika.routes

import com.respublika.model.DossierEtat
import com.respublika.model.toJsonElement
import com.respublika.repository.DossierRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.dossierRoutes(
    repo: DossierRepository = DossierRepository(),
) {

    // GET /api/dossiers — paginated list with optional filters.
    //
    // Query params:
    //   - etat   : comma-separated DossierEtat values (DORMANT, EN_COURS, ADOPTE, REJETE, RETIRE, PROMULGUE)
    //   - since  : ISO date (YYYY-MM-DD) — only dossiers with date_derniere_decision >= since
    //   - q      : substring on titre (case-insensitive)
    //   - page   : 1-based, default 1
    //   - limit  : 1..100, default 20
    get("/api/dossiers") {
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()

        val etats = call.parameters["etat"]?.split(",")
            ?.mapNotNull { token ->
                runCatching { DossierEtat.valueOf(token.trim().uppercase()) }.getOrNull()
            }?.toSet()

        // Validate `since` up front so a malformed value surfaces as a 400 in
        // tests that don't install StatusPages.
        val sinceRaw = call.parameters["since"]?.takeIf { it.isNotBlank() }
        val since = if (sinceRaw != null) {
            try { java.time.LocalDate.parse(sinceRaw) } catch (_: java.time.format.DateTimeParseException) {
                call.respond(HttpStatusCode.BadRequest, buildJsonObject {
                    put("error", "invalid_since")
                    put("hint", "Use ISO date format: YYYY-MM-DD")
                })
                return@get
            }
        } else null
        val query = call.parameters["q"]?.takeIf { it.isNotBlank() }

        val (total, rows) = repo.list(etats, since, query, limit, offset)

        call.respond(buildJsonObject {
            put("page", page)
            put("limit", limit)
            put("total", total)
            put("total_pages", (total + limit - 1) / limit)
            put("dossiers", JsonArray(rows.map { it.toJsonElement() }))
        })
    }

    // GET /api/dossiers/{uid} — detail with the first 100 attached scrutins.
    get("/api/dossiers/{uid}") {
        val uid = call.parameters["uid"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, buildJsonObject { put("error", "uid_required") }
        )
        val detail = repo.detail(uid)
        if (detail == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "dossier_not_found")
                put("uid", uid)
            })
            return@get
        }
        call.respond(detail.toJsonElement())
    }

    // GET /api/dossiers/{uid}/scrutins — paginated scrutins under a dossier.
    get("/api/dossiers/{uid}/scrutins") {
        val uid = call.parameters["uid"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, buildJsonObject { put("error", "uid_required") }
        )
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
        val offset = ((page - 1) * limit).toLong()

        // Confirm dossier exists, returning 404 explicitly so empty-list and
        // unknown-uid don't look identical.
        if (repo.detail(uid, scrutinsLimit = 0) == null) {
            call.respond(HttpStatusCode.NotFound, buildJsonObject {
                put("error", "dossier_not_found")
                put("uid", uid)
            })
            return@get
        }

        val (total, rows) = repo.scrutins(uid, limit, offset)
        call.respond(buildJsonObject {
            put("uid", uid)
            put("page", page)
            put("limit", limit)
            put("total", total)
            put("total_pages", (total + limit - 1) / limit)
            put("scrutins", JsonArray(rows.map { it.toJsonElement() }))
        })
    }
}
