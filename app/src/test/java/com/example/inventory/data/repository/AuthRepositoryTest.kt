package com.example.inventory.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import com.example.inventory.data.model.UserEntity
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * AuthRepository单元测试
 */
class AuthRepositoryTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPrefs: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var repository: AuthRepositoryImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
    }
    
    @Test
    fun `isPasswordValid should reject short password`() {
        // Given
        val shortPassword = "Short1!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(shortPassword, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password without uppercase`() {
        // Given
        val password = "nocapital123!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password without lowercase`() {
        // Given
        val password = "NOLOWER123!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password without digit`() {
        // Given
        val password = "NoDigitHere!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password without special char`() {
        // Given
        val password = "NoSpecial123"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password with whitespace`() {
        // Given
        val password = "Has Space123!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should reject password containing username`() {
        // Given
        val username = "testuser"
        val password = "MyTestUser123!"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `isPasswordValid should accept valid password`() {
        // Given
        val password = "ValidPass123!"
        val username = "testuser"
        
        // When
        val result = isPasswordValid(password, username)
        
        // Then
        assertEquals(true, result)
    }
    
    @Test
    fun `hashPassword should generate different hashes for same password`() {
        // Given
        val password = "TestPassword123!"
        
        // When
        val hash1 = hashPassword(password)
        val hash2 = hashPassword(password)
        
        // Then
        assertTrue(hash1.startsWith("pbkdf2_sha256$"))
        assertTrue(hash2.startsWith("pbkdf2_sha256$"))
        assertTrue(hash1 != hash2) // 不同的盐值应该产生不同的哈希
    }
    
    @Test
    fun `verifyPassword should return true for correct password`() {
        // Given
        val password = "TestPassword123!"
        val hash = hashPassword(password)
        
        // When
        val result = verifyPassword(password, hash)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `verifyPassword should return false for incorrect password`() {
        // Given
        val correctPassword = "TestPassword123!"
        val wrongPassword = "WrongPassword123!"
        val hash = hashPassword(correctPassword)
        
        // When
        val result = verifyPassword(wrongPassword, hash)
        
        // Then
        assertEquals(false, result)
    }
    
    @Test
    fun `verifyPassword should return false for old SHA256 format`() {
        // Given
        val password = "TestPassword123!"
        val oldFormatHash = "c29tZV9vbGRfaGFzaF92YWx1ZQ==" // 模拟旧格式
        
        // When
        val result = verifyPassword(password, oldFormatHash)
        
        // Then
        assertEquals(false, result)
    }
}
