package com.respublika.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

object JwtService {
    private val algorithm = Algorithm.HMAC256(AuthConfig.jwtSecret)

    val verifier = JWT.require(algorithm)
        .withIssuer(AuthConfig.jwtIssuer)
        .withAudience(AuthConfig.jwtAudience)
        .build()

    fun generateToken(userId: Int, email: String): String =
        JWT.create()
            .withIssuer(AuthConfig.jwtIssuer)
            .withAudience(AuthConfig.jwtAudience)
            .withClaim("user_id", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + AuthConfig.jwtExpirationMs))
            .sign(algorithm)

    fun getUserId(jwt: DecodedJWT): Int = jwt.getClaim("user_id").asInt()
}
