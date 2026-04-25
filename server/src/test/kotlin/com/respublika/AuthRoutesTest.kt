package com.respublika

import com.respublika.auth.AuthConfig
import com.respublika.auth.JwtService
import com.respublika.auth.PasswordHasher
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.respublika.database.*
import com.respublika.routes.authRoutes
import kotlin.test.*

class AuthRoutesTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun setupTestDb() {
        Database.connect("jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users)
        }
    }

    private fun ApplicationTestBuilder.configureApp() {
        application {
            install(ContentNegotiation) {
                json(json)
            }
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
            routing {
                authRoutes()
            }
        }
    }

    @Test
    fun `register with valid data returns 201`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"securepass123","nom":"Test User","localite":"Paris"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["message"]!!.jsonPrimitive.content.contains("Inscription"))
    }

    @Test
    fun `register with short password returns 400`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"short","nom":"Test","localite":"Paris"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with missing name returns 400`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"securepass123","nom":"","localite":"Paris"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with missing localite returns 400`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"securepass123","nom":"Test","localite":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register with invalid email returns 400`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email","password":"securepass123","nom":"Test","localite":"Paris"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `register duplicate email returns 409`() = testApplication {
        setupTestDb()
        configureApp()

        // First registration
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@example.com","password":"securepass123","nom":"Test","localite":"Paris"}""")
        }

        // Duplicate
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dup@example.com","password":"securepass123","nom":"Test2","localite":"Lyon"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with wrong password returns 401`() = testApplication {
        setupTestDb()
        configureApp()

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"wrong@example.com","password":"securepass123","nom":"Test","localite":"Paris"}""")
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"wrong@example.com","password":"wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with nonexistent email returns 401`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@example.com","password":"whatever123"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `full registration then login flow`() = testApplication {
        setupTestDb()
        configureApp()

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"flow@example.com","password":"securepass123","nom":"Flow User","localite":"Marseille"}""")
        }

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"flow@example.com","password":"securepass123"}""")
        }

        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val body = json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        assertTrue(body.containsKey("token"))
        assertEquals("flow@example.com", body["user"]!!.jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals("Flow User", body["user"]!!.jsonObject["nom"]!!.jsonPrimitive.content)
        assertEquals("Marseille", body["user"]!!.jsonObject["localite"]!!.jsonPrimitive.content)
    }

    @Test
    fun `me endpoint without token returns 401`() = testApplication {
        setupTestDb()
        configureApp()

        val response = client.get("/api/auth/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me endpoint with valid token returns user`() = testApplication {
        setupTestDb()
        configureApp()

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"me@example.com","password":"securepass123","nom":"Me User","localite":"Lyon"}""")
        }

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"me@example.com","password":"securepass123"}""")
        }
        val jwt = json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val meResponse = client.get("/api/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $jwt")
        }
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val user = json.parseToJsonElement(meResponse.bodyAsText()).jsonObject
        assertEquals("me@example.com", user["email"]!!.jsonPrimitive.content)
    }
}
