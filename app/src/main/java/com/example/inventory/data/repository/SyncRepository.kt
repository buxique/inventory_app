package com.example.inventory.data.repository

import com.example.inventory.data.model.InventoryItemEntity

interface SyncRepository {
    suspend fun pushOperations()
    suspend fun pullOperations()
    suspend fun mergeOperations()
    suspend fun getSyncStatus(): SyncStatus
    suspend fun getConflicts(): List<SyncConflictItem>
    suspend fun resolveConflict(conflict: SyncConflictItem, resolution: ConflictResolution): Boolean
}

data class SyncStatus(
    val lastKey: String = "",
    val lastPushAt: Long = 0L,
    val lastPullAt: Long = 0L,
    val lastMergeAt: Long = 0L,
    val hasConflict: Boolean = false
)

data class SyncConflictItem(
    val id: Long,
    val localItem: InventoryItemEntity?,
    val remoteItem: InventoryItemEntity?,
    val type: SyncConflictType
)

enum class SyncConflictType {
    Modified,
    LocalOnly,
    RemoteOnly
}

enum class ConflictResolution {
    KeepLocal,
    KeepRemote
}
