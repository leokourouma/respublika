package com.respublika.routes

import com.respublika.database.*
import com.respublika.database.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*

fun Route.agentRoutes() {

    // GET /api/agents/runs — Paginated agent runs, most recent first
    get("/api/agents/runs") {
        val page = (call.parameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val limit = (call.parameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = ((page - 1) * limit).toLong()
        val agentNameFilter = call.parameters["agent_name"]

        val result = dbQuery {
            var query = AgentRuns.selectAll()

            if (!agentNameFilter.isNullOrBlank()) {
                query = query.andWhere { AgentRuns.agentName eq agentNameFilter }
            }

            val total = query.count()
            val runs = query
                .orderBy(AgentRuns.startedAt, SortOrder.DESC)
                .limit(limit).offset(offset)
                .map { row ->
                    buildJsonObject {
                        put("id", row[AgentRuns.id].value)
                        put("agent_name", row[AgentRuns.agentName])
                        put("triggered_by", row[AgentRuns.triggeredBy])
                        put("status", row[AgentRuns.status])
                        put("records_processed", row[AgentRuns.recordsProcessed])
                        put("records_affected", row[AgentRuns.recordsAffected])
                        put("started_at", row[AgentRuns.startedAt])
                        put("finished_at", row[AgentRuns.finishedAt])
                        put("findings", row[AgentRuns.findings])
                        put("error_detail", row[AgentRuns.errorDetail] ?: "")
                    }
                }

            buildJsonObject {
                put("page", page)
                put("limit", limit)
                put("total", total)
                put("total_pages", (total + limit - 1) / limit)
                put("runs", JsonArray(runs))
            }
        }

        call.respond(result)
    }

    // GET /api/agents/runs/{id}/findings — Full findings JSON for one run, pretty-printed
    get("/api/agents/runs/{id}/findings") {
        val runId = call.parameters["id"]?.toIntOrNull()
        if (runId == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("id invalide"))
            return@get
        }

        val result = dbQuery {
            AgentRuns.selectAll()
                .where { AgentRuns.id eq runId }
                .singleOrNull()
                ?.let { row ->
                    val raw = row[AgentRuns.findings]
                    try {
                        val parsed = Json.parseToJsonElement(raw)
                        val pretty = Json { prettyPrint = true }
                        pretty.encodeToString(JsonElement.serializer(), parsed)
                    } catch (_: Exception) {
                        raw
                    }
                }
        }

        if (result == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Run non trouvé"))
        } else {
            call.respondText(result, ContentType.Application.Json)
        }
    }
}
