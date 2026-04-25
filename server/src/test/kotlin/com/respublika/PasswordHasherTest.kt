package com.respublika

import com.respublika.auth.PasswordHasher
import kotlin.test.*

class PasswordHasherTest {

    @Test
    fun `hash produces a bcrypt hash`() {
        val hash = PasswordHasher.hash("mypassword")
        assertTrue(hash.startsWith("\$2a\$") || hash.startsWith("\$2b\$") || hash.startsWith("\$2y\$"))
    }

    @Test
    fun `verify correct password returns true`() {
        val hash = PasswordHasher.hash("securepass123")
        assertTrue(PasswordHasher.verify("securepass123", hash))
    }

    @Test
    fun `verify wrong password returns false`() {
        val hash = PasswordHasher.hash("securepass123")
        assertFalse(PasswordHasher.verify("wrongpassword", hash))
    }

    @Test
    fun `different passwords produce different hashes`() {
        val hash1 = PasswordHasher.hash("password1")
        val hash2 = PasswordHasher.hash("password2")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `same password produces different hashes due to salt`() {
        val hash1 = PasswordHasher.hash("samepassword")
        val hash2 = PasswordHasher.hash("samepassword")
        assertNotEquals(hash1, hash2)
        assertTrue(PasswordHasher.verify("samepassword", hash1))
        assertTrue(PasswordHasher.verify("samepassword", hash2))
    }
}
