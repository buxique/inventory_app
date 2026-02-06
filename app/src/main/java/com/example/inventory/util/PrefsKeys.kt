package com.example.inventory.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object PrefsKeys {
    // ==================== Auth 相关 ====================
    const val AUTH_PREFS_NAME = "secure_auth"
    const val KEY_USERS = "users"
    
    // ==================== Settings 相关 ====================
    const val SETTINGS_PREFS_NAME = "secure_settings"
    
    // 语言设置
    const val KEY_LANGUAGE = "app_language"
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_FONT_SCALE = "font_scale"
    const val KEY_OCR_BACKEND = "ocr_backend"
    
    // S3 配置
    const val KEY_S3_ENDPOINT = "s3_endpoint"
    const val KEY_S3_REGION = "s3_region"
    const val KEY_S3_BUCKET = "s3_bucket"
    const val KEY_S3_ACCESS_KEY = "s3_access_key"
    const val KEY_S3_SECRET_KEY = "s3_secret_key"
    
    // ==================== Sync 相关 ====================
    const val KEY_LAST_SYNC_KEY = "sync_last_key"
    const val KEY_LAST_PUSH_AT = "sync_last_push_at"
    const val KEY_LAST_PULL_AT = "sync_last_pull_at"
    const val KEY_LAST_MERGE_AT = "sync_last_merge_at"

    val LANGUAGE_PREF_KEY = stringPreferencesKey(KEY_LANGUAGE)
    val DARK_MODE_PREF_KEY = booleanPreferencesKey(KEY_DARK_MODE)
    val FONT_SCALE_PREF_KEY = floatPreferencesKey(KEY_FONT_SCALE)
    val OCR_BACKEND_PREF_KEY = stringPreferencesKey(KEY_OCR_BACKEND)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PrefsKeys.SETTINGS_PREFS_NAME
)
