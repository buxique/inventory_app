package com.example.inventory.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.util.AppLogger
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.SyncConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

/**
 * 同步仓库实现类
 * 
 * 负责本地数据库与 S3 云存储之间的数据同步
 * 
 * ## 核心功能
 * 1. **推送（Push）**: 将本地数据库备份上传到 S3
 * 2. **拉取（Pull）**: 从 S3 下载备份并恢复到本地
 * 3. **合并（Merge）**: 智能合并本地和远程数据
 * 4. **冲突解决**: 检测并解决数据冲突
 * 
 * ## 同步策略
 * - 使用互斥锁（Mutex）确保同步操作的原子性
 * - 设置超时时间防止长时间阻塞（Push/Pull: 30秒，Merge: 60秒）
 * - 使用加密的 SharedPreferences 存储同步状态和 S3 配置
 * - 记录最后同步时间和同步键，用于冲突检测
 * 
 * ## 冲突检测机制
 * 当满足以下条件时，认为存在冲突：
 * - 本地有推送记录（lastPushAt > 0）
 * - 远程有拉取记录（lastPullAt > 0）
 * - 本地推送时间晚于远程拉取时间（lastPushAt > lastPullAt）
 * - 最后合并时间早于本地推送时间（lastMergeAt < lastPushAt）
 * 
 * ## 错误处理
 * - 所有操作都包装在 try-catch 中
 * - 使用 AppLogger 记录详细的操作日志
 * - 超时会抛出 TimeoutCancellationException
 * - 配置缺失会抛出 IllegalStateException
 * - 网络错误会抛出 IOException
 * 
 * @param context Android 上下文
 * @param exportRepository 导出仓库，用于数据库备份和恢复
 * @param storageRepository 存储仓库，用于 S3 上传和下载
 * @param inventoryRepository 库存仓库，用于读取本地数据
 */
class SyncRepositoryImpl(
    context: Context,
    private val exportRepository: ExportRepository,
    private val storageRepository: StorageRepository,
    private val inventoryRepository: InventoryRepository,
    private val prefsProvider: (Context) -> SharedPreferences = ::securePreferences
) : SyncRepository {
    private val appContext = context.applicationContext
    private val prefs by lazy { prefsProvider(appContext) }
    
    /**
     * 互斥锁，确保同步操作的原子性
     * 
     * 防止多个同步操作同时执行，避免数据不一致
     */
    private val mutex = Mutex()

    override suspend fun pushOperations() = withContext(Dispatchers.IO) {
        try {
            // 使用配置的超时时间
            withTimeout(SyncConfig.PUSH_TIMEOUT_MS) {
                mutex.withLock {
                    val config = loadS3Config() 
                        ?: throw IllegalStateException("未配置S3")

                    val lastPushAt = prefs.getLong(KEY_LAST_PUSH_AT, 0L)
                    val maxLastModified = inventoryRepository.getMaxLastModified() ?: 0L
                    
                    if (maxLastModified <= lastPushAt) {
                        AppLogger.logSync("push", true, "无数据变更，跳过上传")
                        return@withLock
                    }

                    val backup = exportRepository.backupDatabase()
                        ?: throw IllegalStateException("备份失败")
                    val key = storageRepository.uploadBackup(backup, config) 
                        ?: throw java.io.IOException("上传失败")
                    val mergedItems = inventoryRepository.getAllItemsSnapshot()
                    val syncHash = computeItemsHash(mergedItems)
                    
                    prefs.edit()
                        .putString(KEY_LAST_SYNC_KEY, key)
                        .putLong(KEY_LAST_PUSH_AT, System.currentTimeMillis())
                        .putString(KEY_LAST_SYNC_HASH, syncHash)
                        .apply()
                    
                    AppLogger.logSync("push", true, "key=$key")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logSync("push", false, e.message ?: "未知错误")
            throw e
        }
    }

    override suspend fun pullOperations() = withContext(Dispatchers.IO) {
        try {
            // 使用配置的超时时间
            withTimeout(SyncConfig.PULL_TIMEOUT_MS) {
                mutex.withLock {
                    val config = loadS3Config() 
                        ?: throw IllegalStateException("未配置S3")
                    val key = prefs.getString(KEY_LAST_SYNC_KEY, "").orEmpty()
                    if (key.isBlank()) throw IllegalStateException("无同步记录")
                    
                    val file = storageRepository.downloadBackup(key, config) 
                        ?: throw java.io.IOException("下载失败")
                    val success = exportRepository.restoreDatabase(file)
                    
                    if (!success) {
                        throw IllegalStateException("数据库恢复失败")
                    }
                    val mergedItems = inventoryRepository.getAllItemsSnapshot()
                    val syncHash = computeItemsHash(mergedItems)
                    
                    prefs.edit()
                        .putLong(KEY_LAST_PULL_AT, System.currentTimeMillis())
                        .putString(KEY_LAST_SYNC_HASH, syncHash)
                        .apply()
                    
                    AppLogger.logSync("pull", true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logSync("pull", false, e.message ?: "未知错误")
            throw e
        }
    }

    override suspend fun mergeOperations() = withContext(Dispatchers.IO) {
        try {
            // 使用配置的超时时间
            withTimeout(SyncConfig.MERGE_TIMEOUT_MS) {
                mutex.withLock {
                    val config = loadS3Config() 
                        ?: throw IllegalStateException("未配置S3")
                    val key = prefs.getString(KEY_LAST_SYNC_KEY, "").orEmpty()
                    
                    // 获取本地数据
                    val localItems = inventoryRepository.getAllItemsSnapshot()
                    
                    // 如果有远程备份，下载并读取
                    val remoteItems = if (key.isNotBlank()) {
                        val file = storageRepository.downloadBackup(key, config)
                        if (file != null) readInventoryItems(file) else emptyList()
                    } else {
                        emptyList()
                    }
                    
                    // 智能合并：基于 lastModified 时间戳
                    val (itemsToUpdate, itemsToInsert) = mergeItemsByTimestamp(localItems, remoteItems)
                    
                    // 更新本地数据库
                    if (itemsToUpdate.isNotEmpty()) {
                        inventoryRepository.batchUpdateItems(itemsToUpdate)
                    }
                    
                    // 插入新商品
                    if (itemsToInsert.isNotEmpty()) {
                        inventoryRepository.batchAddItems(itemsToInsert)
                    }
                    
                    // 推送合并后的数据
                    val backup = exportRepository.backupDatabase()
                        ?: throw IllegalStateException("备份失败")
                    val newKey = storageRepository.uploadBackup(backup, config) 
                        ?: throw java.io.IOException("上传失败")
                    val mergedItems = inventoryRepository.getAllItemsSnapshot()
                    val syncHash = computeItemsHash(mergedItems)
                    
                    prefs.edit()
                        .putString(KEY_LAST_SYNC_KEY, newKey)
                        .putLong(KEY_LAST_MERGE_AT, System.currentTimeMillis())
                        .putString(KEY_LAST_SYNC_HASH, syncHash)
                        .apply()
                    
                    AppLogger.logSync("merge", true, "key=$newKey, updated=${itemsToUpdate.size}, inserted=${itemsToInsert.size} items")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logSync("merge", false, e.message ?: "未知错误")
            throw e
        }
    }
    
    /**
     * 智能合并商品数据（基于 lastModified 时间戳）
     * 
     * 合并策略：
     * 1. 如果商品只存在于本地，保留本地版本（无需操作）
     * 2. 如果商品只存在于远程，插入到本地
     * 3. 如果商品同时存在于两方，比较 lastModified：
     *    - 如果远程较新，更新本地版本
     *    - 如果本地较新或相同，保留本地版本（无需操作）
     * 
     * @param localItems 本地商品列表
     * @param remoteItems 远程商品列表
     * @return Pair<需要更新的商品列表, 需要插入的商品列表>
     */
    private fun mergeItemsByTimestamp(
        localItems: List<InventoryItemEntity>,
        remoteItems: List<InventoryItemEntity>
    ): Pair<List<InventoryItemEntity>, List<InventoryItemEntity>> {
        val localById = localItems.associateBy { it.id }
        val remoteById = remoteItems.associateBy { it.id }
        val allIds = (localById.keys + remoteById.keys).toSet()
        
        val itemsToUpdate = mutableListOf<InventoryItemEntity>()
        val itemsToInsert = mutableListOf<InventoryItemEntity>()
        
        allIds.forEach { id ->
            val local = localById[id]
            val remote = remoteById[id]
            
            when {
                // 只存在于本地：保留本地版本（无需操作）
                local != null && remote == null -> {
                    // 不需要任何操作
                }
                // 只存在于远程：插入到本地
                local == null && remote != null -> {
                    itemsToInsert.add(remote)
                }
                // 同时存在：比较 lastModified
                local != null && remote != null -> {
                    // 如果远程较新，更新本地版本
                    if (remote.lastModified > local.lastModified) {
                        itemsToUpdate.add(remote)
                    }
                    // 如果本地较新或相同，保留本地版本（无需操作）
                }
            }
        }
        
        return Pair(itemsToUpdate, itemsToInsert)
    }

    override suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            val lastKey = prefs.getString(KEY_LAST_SYNC_KEY, "").orEmpty()
            val lastPushAt = prefs.getLong(KEY_LAST_PUSH_AT, 0L)
            val lastPullAt = prefs.getLong(KEY_LAST_PULL_AT, 0L)
            val lastMergeAt = prefs.getLong(KEY_LAST_MERGE_AT, 0L)
            val lastSyncHash = prefs.getString(KEY_LAST_SYNC_HASH, "").orEmpty()
            val legacyConflict = lastPushAt > 0L && lastPullAt > 0L && lastPushAt > lastPullAt && lastMergeAt < lastPushAt
            val localItems = inventoryRepository.getAllItemsSnapshot()
            val localHash = computeItemsHash(localItems)
            val localChanged = lastSyncHash.isNotBlank() && localHash != lastSyncHash
            val remoteChanged = if (lastSyncHash.isBlank() || lastKey.isBlank()) {
                false
            } else {
                val config = loadS3Config()
                if (config != null) {
                    val file = storageRepository.downloadBackup(lastKey, config)
                    if (file != null) {
                        val remoteItems = readInventoryItems(file)
                        val remoteHash = computeItemsHash(remoteItems)
                        remoteHash != lastSyncHash
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            val hasConflict = if (lastSyncHash.isBlank()) legacyConflict else (localChanged && remoteChanged)
            SyncStatus(
                lastKey = lastKey,
                lastPushAt = lastPushAt,
                lastPullAt = lastPullAt,
                lastMergeAt = lastMergeAt,
                hasConflict = hasConflict
            )
        }
    }

    override suspend fun getConflicts(): List<SyncConflictItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val config = loadS3Config() ?: return@withLock emptyList()
            val key = prefs.getString(KEY_LAST_SYNC_KEY, "").orEmpty()
            if (key.isBlank()) return@withLock emptyList()
            val file = storageRepository.downloadBackup(key, config) ?: return@withLock emptyList()
            val localItems = inventoryRepository.getAllItemsSnapshot()
            val remoteItems = readInventoryItems(file)
            buildConflicts(localItems, remoteItems)
        }
    }

    override suspend fun resolveConflict(
        conflict: SyncConflictItem,
        resolution: ConflictResolution
    ): Boolean = withContext(Dispatchers.IO) {
        val shouldPush = mutex.withLock {
            when (resolution) {
                ConflictResolution.KeepLocal -> {
                    if (conflict.localItem == null) return@withLock false
                    true
                }
                ConflictResolution.KeepRemote -> {
                    if (conflict.remoteItem == null) {
                        if (conflict.localItem != null) {
                            inventoryRepository.deleteItem(conflict.id)
                            return@withLock true
                        }
                        return@withLock false
                    }
                    val remote = conflict.remoteItem
                    if (conflict.localItem == null) {
                        inventoryRepository.addItem(remote)
                    } else {
                        inventoryRepository.updateItem(remote)
                    }
                    true
                }
            }
        }
        if (shouldPush) {
            pushOperations()
        }
        shouldPush
    }

    private fun loadS3Config(): S3Config? {
        val endpoint = prefs.getString(KEY_S3_ENDPOINT, "").orEmpty()
        val region = prefs.getString(KEY_S3_REGION, "").orEmpty()
        val bucket = prefs.getString(KEY_S3_BUCKET, "").orEmpty()
        val accessKey = prefs.getString(KEY_S3_ACCESS_KEY, "").orEmpty()
        val secretKey = prefs.getString(KEY_S3_SECRET_KEY, "").orEmpty()
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) return null
        return S3Config(
            endpoint = endpoint,
            region = region,
            bucket = bucket,
            accessKey = accessKey,
            secretKey = secretKey
        )
    }

    private fun buildConflicts(
        localItems: List<InventoryItemEntity>,
        remoteItems: List<InventoryItemEntity>
    ): List<SyncConflictItem> {
        val localById = localItems.associateBy { it.id }
        val remoteById = remoteItems.associateBy { it.id }
        val allIds = (localById.keys + remoteById.keys).toSet()
        return allIds.mapNotNull { id ->
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local != null && remote != null && local != remote ->
                    SyncConflictItem(id = id, localItem = local, remoteItem = remote, type = SyncConflictType.Modified)
                local != null && remote == null ->
                    SyncConflictItem(id = id, localItem = local, remoteItem = null, type = SyncConflictType.LocalOnly)
                local == null && remote != null ->
                    SyncConflictItem(id = id, localItem = null, remoteItem = remote, type = SyncConflictType.RemoteOnly)
                else -> null
            }
        }
    }

    private fun readInventoryItems(file: File): List<InventoryItemEntity> {
        if (!file.exists()) return emptyList()
        val db = runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return emptyList()
        return try {
            val items = mutableListOf<InventoryItemEntity>()
            db.rawQuery(
                "SELECT id, name, brand, model, parameters, barcode, quantity, unit, location, remark, listId, lastModified FROM inventory_items",
                null
            ).use { cursor ->
                val idIndex = cursor.getColumnIndex("id")
                val nameIndex = cursor.getColumnIndex("name")
                val brandIndex = cursor.getColumnIndex("brand")
                val modelIndex = cursor.getColumnIndex("model")
                val parametersIndex = cursor.getColumnIndex("parameters")
                val barcodeIndex = cursor.getColumnIndex("barcode")
                val quantityIndex = cursor.getColumnIndex("quantity")
                val unitIndex = cursor.getColumnIndex("unit")
                val locationIndex = cursor.getColumnIndex("location")
                val remarkIndex = cursor.getColumnIndex("remark")
                val listIdIndex = cursor.getColumnIndex("listId")
                val lastModifiedIndex = cursor.getColumnIndex("lastModified")
                
                while (cursor.moveToNext()) {
                    items.add(
                        InventoryItemEntity(
                            id = cursor.getLong(idIndex),
                            name = cursor.getString(nameIndex).orEmpty(),
                            brand = cursor.getString(brandIndex).orEmpty(),
                            model = cursor.getString(modelIndex).orEmpty(),
                            parameters = cursor.getString(parametersIndex).orEmpty(),
                            barcode = cursor.getString(barcodeIndex).orEmpty(),
                            quantity = cursor.getInt(quantityIndex),
                            unit = cursor.getString(unitIndex) ?: "个",
                            location = cursor.getString(locationIndex).orEmpty(),
                            remark = cursor.getString(remarkIndex).orEmpty(),
                            listId = cursor.getLong(listIdIndex),
                            lastModified = cursor.getLong(lastModifiedIndex)
                        )
                    )
                }
            }
            items
        } finally {
            db.close()
        }
    }

    private fun computeItemsHash(items: List<InventoryItemEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        items.sortedBy { it.id }.forEach { item ->
            digest.update(item.id.toString().toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.listId.toString().toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.name.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.brand.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.model.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.parameters.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.barcode.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.quantity.toString().toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.unit.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.location.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.remark.toByteArray())
            digest.update(byteArrayOf(0))
            digest.update(item.lastModified.toString().toByteArray())
            digest.update(byteArrayOf(0))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

}

private fun securePreferences(context: Context): SharedPreferences {
    val masterKey = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
    } catch (e: Exception) {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    return EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private const val PREFS_NAME = PrefsKeys.SETTINGS_PREFS_NAME
private const val KEY_S3_ENDPOINT = PrefsKeys.KEY_S3_ENDPOINT
private const val KEY_S3_REGION = PrefsKeys.KEY_S3_REGION
private const val KEY_S3_BUCKET = PrefsKeys.KEY_S3_BUCKET
private const val KEY_S3_ACCESS_KEY = PrefsKeys.KEY_S3_ACCESS_KEY
private const val KEY_S3_SECRET_KEY = PrefsKeys.KEY_S3_SECRET_KEY
private const val KEY_LAST_SYNC_KEY = PrefsKeys.KEY_LAST_SYNC_KEY
private const val KEY_LAST_PUSH_AT = PrefsKeys.KEY_LAST_PUSH_AT
private const val KEY_LAST_PULL_AT = PrefsKeys.KEY_LAST_PULL_AT
private const val KEY_LAST_MERGE_AT = PrefsKeys.KEY_LAST_MERGE_AT
private const val KEY_LAST_SYNC_HASH = PrefsKeys.KEY_LAST_SYNC_HASH
