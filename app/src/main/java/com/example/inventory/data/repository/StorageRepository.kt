package com.example.inventory.data.repository

import java.io.File

interface StorageRepository {
    suspend fun uploadBackup(file: File, config: S3Config): String?
    suspend fun downloadBackup(key: String, config: S3Config): File?
}
