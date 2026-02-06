package com.example.inventory.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全偏好设置管理器（单例）
 * 
 * 统一管理 EncryptedSharedPreferences 的创建和访问，
 * 避免在多个地方重复创建实例。
 * 
 * 使用方式：
 * ```kotlin
 * val prefs = SecurePreferencesManager.getInstance(context)
 * prefs.edit().putString("key", "value").apply()
 * ```
 */
object SecurePreferencesManager {
    
    private const val PREFS_NAME = "secure_settings"
    private var instance: SharedPreferences? = null
    private val lock = Any()
    private var fallbackLogged = false
    
    /**
     * 获取加密 SharedPreferences 实例
     * 
     * @param context 上下文
     * @return SharedPreferences 实例（加密或普通）
     */
    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(lock) {
            instance ?: createSecurePreferences(context).also {
                instance = it
            }
        }
    }
    
    /**
     * 清除单例（用于测试）
     */
    fun clearInstance() {
        synchronized(lock) {
            instance = null
        }
    }
    
    private fun createSecurePreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = createMasterKey(context)
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            if (!fallbackLogged) {
                fallbackLogged = true
                AppLogger.e("EncryptedSharedPreferences unavailable: ${e.message}", "Prefs", e)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun createMasterKey(context: Context): MasterKey {
        return try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (e: Exception) {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }
}
