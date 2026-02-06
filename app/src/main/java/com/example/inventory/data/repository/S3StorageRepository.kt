package com.example.inventory.data.repository

import android.content.Context
import com.example.inventory.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.time.Duration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

class S3StorageRepository(
    context: Context
) : StorageRepository {
    private val appContext = context.applicationContext
    override suspend fun uploadBackup(file: File, config: S3Config): String? = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.bucket.isBlank() || config.accessKey.isBlank() || config.secretKey.isBlank()) {
            return@withContext null
        }
        val key = "backup/${System.currentTimeMillis()}-${file.name}"
        createClient(config).use { client ->
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(key)
                    .build(),
                RequestBody.fromFile(file)
            )
            key
        }
    }

    override suspend fun downloadBackup(key: String, config: S3Config): File? = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.bucket.isBlank() || config.accessKey.isBlank() || config.secretKey.isBlank()) {
            return@withContext null
        }
        val target = File(appContext.cacheDir, key.substringAfterLast("/"))
        createClient(config).use { client ->
            client.getObject(
                GetObjectRequest.builder()
                    .bucket(config.bucket)
                    .key(key)
                    .build(),
                ResponseTransformer.toFile(target.toPath())
            )
            target
        }
    }

    private fun createClient(config: S3Config): S3Client {
        val credentials = AwsBasicCredentials.create(config.accessKey, config.secretKey)
        val region = if (config.region.isNotBlank()) {
            Region.of(config.region)
        } else {
            Region.US_EAST_1
        }
        val httpClient = UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofMillis(Constants.Network.CONNECTION_TIMEOUT.toLong()))
            .socketTimeout(Duration.ofMillis(Constants.Network.SOCKET_TIMEOUT.toLong()))
            .build()
        val overrideConfig = ClientOverrideConfiguration.builder()
            .retryPolicy(RetryPolicy.builder().numRetries(Constants.Network.MAX_ERROR_RETRY).build())
            .build()
        val builder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(region)
            .httpClient(httpClient)
            .overrideConfiguration(overrideConfig)
        if (config.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(config.endpoint))
        }
        return builder.build()
    }
}
