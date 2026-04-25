package com.respublika.routes

import com.respublika.auth.*
import com.respublika.database.DatabaseFactory.dbQuery
import com.respublika.database.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("AuthRoutes")

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val nom: String,
    val localite: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse
)

@Serializable
data class UserResponse(
    val id: Int,
    val email: String,
    val nom: String,
    val localite: String,
    val email_verified: Boolean
)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class ErrorResponse(val error: String)

fun Route.authRoutes() {
    route("/api/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()

            if (req.email.isBlank() || !req.email.contains("@")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email invalide"))
                return@post
            }
            if (req.password.length < 8) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Le mot de passe doit contenir au moins 8 caractères"))
                return@post
            }
            if (req.nom.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Le nom est requis"))
                return@post
            }
            if (req.localite.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("La localité est requise"))
                return@post
            }

            val existing = dbQuery {
                Users.selectAll().where { Users.email eq req.email.lowercase().trim() }.singleOrNull()
            }
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Un compte existe déjà avec cet email"))
                return@post
            }

            val now = Instant.now().toString()
            val passwordHash = PasswordHasher.hash(req.password)

            val userId = dbQuery {
                Users.insertAndGetId {
                    it[email] = req.email.lowercase().trim()
                    it[Users.passwordHash] = passwordHash
                    it[nom] = req.nom.trim()
                    it[localite] = req.localite.trim()
                    it[createdAt] = now
                    it[updatedAt] = now
                }.value
            }

            logger.info("New user registered: id=$userId, email=${req.email.lowercase().trim()}")
            call.respond(
                HttpStatusCode.Created,
                MessageResponse("Inscription réussie ! Vous pouvez maintenant vous connecter.")
            )
        }

        post("/login") {
            val req = call.receive<LoginRequest>()

            val user = dbQuery {
                Users.selectAll().where { Users.email eq req.email.lowercase().trim() }.singleOrNull()
            }

            if (user == null || !PasswordHasher.verify(req.password, user[Users.passwordHash])) {
                logger.warn("Failed login attempt for email: ${req.email}")
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email ou mot de passe incorrect"))
                return@post
            }

            val jwtToken = JwtService.generateToken(user[Users.id].value, user[Users.email])
            logger.info("User logged in: id=${user[Users.id].value}, email=${user[Users.email]}")

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    token = jwtToken,
                    user = UserResponse(
                        id = user[Users.id].value,
                        email = user[Users.email],
                        nom = user[Users.nom],
                        localite = user[Users.localite],
                        email_verified = true
                    )
                )
            )
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("user_id").asInt()

                val user = dbQuery {
                    Users.selectAll().where { Users.id eq userId }.singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Utilisateur non trouvé"))
                    return@get
                }

                call.respond(
                    HttpStatusCode.OK,
                    UserResponse(
                        id = user[Users.id].value,
                        email = user[Users.email],
                        nom = user[Users.nom],
                        localite = user[Users.localite],
                        email_verified = user[Users.emailVerified]
                    )
                )
            }
        }
    }
}
