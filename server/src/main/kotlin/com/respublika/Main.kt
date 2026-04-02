// server/src/main/kotlin/com/respublika/Main.kt
package com.respublika

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.respublika.database.DatabaseFactory
import com.respublika.routes.*
import com.respublika.service.MasterIngestor
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    // JSON content negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // Error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                """{"error": "${cause.localizedMessage ?: "Erreur interne"}"}""",
                ContentType.Application.Json,
                HttpStatusCode.InternalServerError
            )
        }
    }

    val dataPath = System.getenv("DATA_PATH") ?: "./data"
    val ingestor = MasterIngestor(dataPath)

    routing {
        // Swagger UI
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")

        get("/") {
            call.respondText("ResPublika API is Live — /swagger pour la documentation")
        }

        get("/admin/ingest") {
            applicationScope.launch {
                try {
                    println("🚀 Début de l'ingestion exhaustive...")
                    ingestor.processAll()
                } catch (e: Exception) {
                    println("❌ Échec de l'ingestion : ${e.message}")
                    e.printStackTrace()
                }
            }
            call.respondText("Ingestion démarrée en tâche de fond.")
        }

        // Route modules
        deputeRoutes()
        ethiqueRoutes()
        scrutinRoutes()
    }
}
