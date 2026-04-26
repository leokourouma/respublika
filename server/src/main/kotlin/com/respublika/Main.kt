// server/src/main/kotlin/com/respublika/Main.kt
package com.respublika

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.respublika.auth.JwtService
import com.respublika.auth.UserSeeder
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
    UserSeeder.seedIfEmpty()

    // JSON content negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // JWT Authentication
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "respublika"
            verifier(JwtService.verifier)
            validate { credential ->
                if (credential.payload.getClaim("user_id").asInt() != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token invalide ou expiré"))
            }
        }
    }

    // Error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.localizedMessage ?: "Erreur interne"))
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
        authRoutes()
        deputeRoutes()
        ethiqueRoutes()
        scrutinRoutes()
    }
}
