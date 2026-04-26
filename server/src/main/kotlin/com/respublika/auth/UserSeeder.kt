package com.respublika.auth

import com.respublika.database.Users
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

object UserSeeder {
    private val logger = LoggerFactory.getLogger("UserSeeder")

    private const val SEED_PASSWORD = "password123"

    private data class Fixture(val email: String, val nom: String, val localite: String)

    private val fixtures = listOf(
        Fixture("alice@test.fr", "Alice Martin", "Paris"),
        Fixture("bob@test.fr", "Bob Dupont", "Lyon"),
        Fixture("camille@test.fr", "Camille Bernard", "Marseille"),
        Fixture("david@test.fr", "David Lefevre", "Toulouse"),
        Fixture("emma@test.fr", "Emma Rousseau", "Bordeaux"),
        Fixture("francois@test.fr", "François Petit", "Nantes"),
        Fixture("gabrielle@test.fr", "Gabrielle Moreau", "Strasbourg"),
        Fixture("hugo@test.fr", "Hugo Laurent", "Lille"),
        Fixture("ines@test.fr", "Inès Garcia", "Nice"),
        Fixture("julien@test.fr", "Julien Roux", "Rennes")
    )

    fun seedIfEmpty() {
        transaction {
            val existing = Users.selectAll().count()
            if (existing > 0L) {
                logger.info("Users table already has $existing rows — skipping seed")
                return@transaction
            }

            val now = Instant.now().toString()
            val passwordHash = PasswordHasher.hash(SEED_PASSWORD)

            fixtures.forEach { f ->
                Users.insert {
                    it[email] = f.email
                    it[Users.passwordHash] = passwordHash
                    it[nom] = f.nom
                    it[localite] = f.localite
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            logger.info("Seeded ${fixtures.size} test users — password for all: $SEED_PASSWORD")
        }
    }
}
