package com.respublika.auth

object AuthConfig {
    val jwtSecret: String = System.getenv("JWT_SECRET") ?: "respublika-dev-secret-change-in-production"
    val jwtIssuer: String = "respublika"
    val jwtAudience: String = "respublika-users"
    val jwtExpirationMs: Long = 24 * 60 * 60 * 1000 // 24h
}
