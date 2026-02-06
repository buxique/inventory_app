package com.example.inventory.data.repository

import android.content.Context
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.PutObjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class S3StorageRepository(
    private val context: Context
) : StorageRepository {
    override suspend fun uploadBackup(file: File, config: S3Config): String? = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.bucket.isBlank() || config.accessKey.isBlank() || config.secretKey.isBlank()) {
            return@withContext null
        }
        val client = createClient(config)
        val key = "backup/${System.currentTimeMillis()}-${file.name}"
        client.putObject(PutObjectRequest(config.bucket, key, file))
        key
    }

    override suspend fun downloadBackup(key: String, config: S3Config): File? = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.bucket.isBlank() || config.accessKey.isBlank() || config.secretKey.isBlank()) {
            return@withContext null
        }
        val client = createClient(config)
        val target = File(context.cacheDir, key.substringAfterLast("/"))
        client.getObject(GetObjectRequest(config.bucket, key), target)
        target
    }

    @Suppress("DEPRECATION")
    private fun createClient(config: S3Config): AmazonS3Client {
        val credentials = BasicAWSCredentials(config.accessKey, config.secretKey)
        val clientConfig = ClientConfiguration().apply {
            connectionTimeout = 15000  // 15秒连接超时
            socketTimeout = 30000      // 30秒读取超时
            maxErrorRetry = 3          // 重试3次
        }
        val client = AmazonS3Client(credentials, clientConfig)
        if (config.endpoint.isNotBlank()) {
            client.setEndpoint(config.endpoint)
        }
        if (config.region.isNotBlank()) {
            client.setRegion(Region.getRegion(Regions.fromName(config.region)))
        }
        return client
    }
}
