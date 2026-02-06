package com.example.inventory.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHashTest {
    @Test
    fun hashPassword_changesWithInput() {
        val first = hashPassword("password123")
        val second = hashPassword("password124")
        assertNotEquals(first, second)
    }

    @Test
    fun passwordPolicy_acceptsStrongPassword() {
        assertTrue(isPasswordValid("Stronger#2026", "user"))
    }

    @Test
    fun passwordPolicy_rejectsWeakPassword() {
        assertFalse(isPasswordValid("password123", "user"))
    }

    @Test
    fun passwordPolicy_rejectsPasswordContainingUsername() {
        assertFalse(isPasswordValid("User#2026Abc", "user"))
    }
}
