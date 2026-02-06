package com.example.inventory.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.inventory.data.model.InventoryItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import java.io.File
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SyncRepository 单元测试
 * 
 * 测试覆盖：
 * - 推送操作流程
 * - 拉取操作流程
 * - 合并操作流程
 * - 冲突检测和解决
 * - 同步状态管理
 * - 异常处理
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncRepositoryTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPrefs: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockExportRepository: ExportRepository
    
    @Mock
    private lateinit var mockStorageRepository: StorageRepository
    
    @Mock
    private lateinit var mockInventoryRepository: InventoryRepository
    
    private lateinit var repository: SyncRepositoryImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        
        repository = SyncRepositoryImpl(
            mockContext,
            mockExportRepository,
            mockStorageRepository,
            mockInventoryRepository,
            prefsProvider = { mockPrefs }
        )
    }
    
    @Test
    fun `pushOperations should fail when S3 not configured`() = runTest {
        // Given
        whenever(mockPrefs.getString("s3_endpoint", "")).thenReturn("")
        
        // When & Then
        try {
            repository.pushOperations()
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("未配置S3", e.message)
        }
    }
    
    @Test
    fun `pushOperations should fail when backup fails`() = runTest {
        // Given
        setupValidS3Config()
        whenever(mockInventoryRepository.getMaxLastModified()).thenReturn(1L)
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(0L)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(null)
        
        // When & Then
        try {
            repository.pushOperations()
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("备份失败", e.message)
        }
    }
    
    @Test
    fun `pushOperations should fail when upload fails`() = runTest {
        // Given
        setupValidS3Config()
        val backupFile = File("backup.db")
        whenever(mockInventoryRepository.getMaxLastModified()).thenReturn(1L)
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(0L)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(backupFile)
        whenever(mockStorageRepository.uploadBackup(any(), any())).thenReturn(null)
        
        // When & Then
        try {
            repository.pushOperations()
            fail("应该抛出异常")
        } catch (e: java.io.IOException) {
            assertEquals("上传失败", e.message)
        }
    }
    
    @Test
    fun `pushOperations should succeed with valid config`() = runTest {
        // Given
        setupValidS3Config()
        val backupFile = File("backup.db")
        val uploadKey = "backup-123.db"
        whenever(mockInventoryRepository.getMaxLastModified()).thenReturn(1L)
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(0L)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(backupFile)
        whenever(mockStorageRepository.uploadBackup(any(), any())).thenReturn(uploadKey)
        
        // When
        repository.pushOperations()
        
        // Then
        verify(mockEditor).putString("sync_last_key", uploadKey)
        verify(mockEditor).putLong(eq("sync_last_push_at"), any())
        verify(mockEditor).apply()
    }
    
    @Test
    fun `pullOperations should fail when S3 not configured`() = runTest {
        // Given
        whenever(mockPrefs.getString("s3_endpoint", "")).thenReturn("")
        
        // When & Then
        try {
            repository.pullOperations()
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("未配置S3", e.message)
        }
    }
    
    @Test
    fun `pullOperations should fail when no sync record exists`() = runTest {
        // Given
        setupValidS3Config()
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("")
        
        // When & Then
        try {
            repository.pullOperations()
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("无同步记录", e.message)
        }
    }
    
    @Test
    fun `pullOperations should fail when download fails`() = runTest {
        // Given
        setupValidS3Config()
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("backup-123.db")
        whenever(mockStorageRepository.downloadBackup(any(), any())).thenReturn(null)
        
        // When & Then
        try {
            repository.pullOperations()
            fail("应该抛出异常")
        } catch (e: java.io.IOException) {
            assertEquals("下载失败", e.message)
        }
    }
    
    @Test
    fun `pullOperations should fail when restore fails`() = runTest {
        // Given
        setupValidS3Config()
        val downloadedFile = File("downloaded.db")
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("backup-123.db")
        whenever(mockStorageRepository.downloadBackup(any(), any())).thenReturn(downloadedFile)
        whenever(mockExportRepository.restoreDatabase(downloadedFile, null)).thenReturn(false)
        
        // When & Then
        try {
            repository.pullOperations()
            fail("应该抛出异常")
        } catch (e: IllegalStateException) {
            assertEquals("数据库恢复失败", e.message)
        }
    }
    
    @Test
    fun `pullOperations should succeed with valid data`() = runTest {
        // Given
        setupValidS3Config()
        val downloadedFile = File("downloaded.db")
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("backup-123.db")
        whenever(mockStorageRepository.downloadBackup(any(), any())).thenReturn(downloadedFile)
        whenever(mockExportRepository.restoreDatabase(downloadedFile, null)).thenReturn(true)
        
        // When
        repository.pullOperations()
        
        // Then
        verify(mockEditor).putLong(eq("sync_last_pull_at"), any())
        verify(mockEditor).apply()
    }
    
    @Test
    fun `mergeOperations should pull then push`() = runTest {
        // Given
        setupValidS3Config()
        val remoteFile = File("remote.db")
        val backupFile = File("backup.db")
        val newKey = "merged-123.db"
        
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("old-key.db")
        whenever(mockStorageRepository.downloadBackup(any(), any())).thenReturn(remoteFile)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(backupFile)
        whenever(mockStorageRepository.uploadBackup(any(), any())).thenReturn(newKey)
        whenever(mockInventoryRepository.getAllItemsSnapshot()).thenReturn(emptyList())
        
        // When
        repository.mergeOperations()
        
        // Then
        verify(mockStorageRepository).downloadBackup(any(), any())
        verify(mockInventoryRepository).getAllItemsSnapshot()
        verify(mockExportRepository).backupDatabase(null)
        verify(mockStorageRepository).uploadBackup(eq(backupFile), any())
        verify(mockEditor).putString("sync_last_key", newKey)
        verify(mockEditor).putLong(eq("sync_last_merge_at"), any())
    }
    
    @Test
    fun `getSyncStatus should return correct status`() = runTest {
        // Given
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("test-key")
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(1000L)
        whenever(mockPrefs.getLong("sync_last_pull_at", 0L)).thenReturn(500L)
        whenever(mockPrefs.getLong("sync_last_merge_at", 0L)).thenReturn(0L)
        
        // When
        val status = repository.getSyncStatus()
        
        // Then
        assertEquals("test-key", status.lastKey)
        assertEquals(1000L, status.lastPushAt)
        assertEquals(500L, status.lastPullAt)
        assertEquals(0L, status.lastMergeAt)
        assertTrue(status.hasConflict) // push > pull 且未merge
    }
    
    @Test
    fun `getSyncStatus should detect no conflict when merged`() = runTest {
        // Given
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("test-key")
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(1000L)
        whenever(mockPrefs.getLong("sync_last_pull_at", 0L)).thenReturn(500L)
        whenever(mockPrefs.getLong("sync_last_merge_at", 0L)).thenReturn(1500L)
        
        // When
        val status = repository.getSyncStatus()
        
        // Then
        assertFalse(status.hasConflict) // 已经merge过了
    }
    
    @Test
    fun `getConflicts should return empty when S3 not configured`() = runTest {
        // Given
        whenever(mockPrefs.getString("s3_endpoint", "")).thenReturn("")
        
        // When
        val conflicts = repository.getConflicts()
        
        // Then
        assertTrue(conflicts.isEmpty())
    }
    
    @Test
    fun `getConflicts should return empty when no sync key`() = runTest {
        // Given
        setupValidS3Config()
        whenever(mockPrefs.getString("sync_last_key", "")).thenReturn("")
        
        // When
        val conflicts = repository.getConflicts()
        
        // Then
        assertTrue(conflicts.isEmpty())
    }
    
    @Test
    fun `resolveConflict with KeepLocal should push changes`() = runTest {
        // Given
        setupValidS3Config()
        val localItem = InventoryItemEntity(
            id = 1L,
            listId = 1L,
            name = "本地商品",
            brand = "品牌A",
            model = "型号1",
            parameters = "参数",
            barcode = "123",
            quantity = 10,
            remark = ""
        )
        val conflict = SyncConflictItem(
            id = 1L,
            localItem = localItem,
            remoteItem = null,
            type = SyncConflictType.LocalOnly
        )
        
        val backupFile = File("backup.db")
        whenever(mockInventoryRepository.getMaxLastModified()).thenReturn(1L)
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(0L)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(backupFile)
        whenever(mockStorageRepository.uploadBackup(any(), any())).thenReturn("new-key")
        
        // When
        val result = repository.resolveConflict(conflict, ConflictResolution.KeepLocal)
        
        // Then
        assertTrue(result)
        verify(mockExportRepository).backupDatabase(null)
        verify(mockStorageRepository).uploadBackup(any(), any())
    }
    
    @Test
    fun `resolveConflict with KeepRemote should update local item`() = runTest {
        // Given
        setupValidS3Config()
        val localItem = InventoryItemEntity(
            id = 1L,
            listId = 1L,
            name = "本地商品",
            brand = "品牌A",
            model = "型号1",
            parameters = "参数",
            barcode = "123",
            quantity = 10,
            remark = ""
        )
        val remoteItem = InventoryItemEntity(
            id = 1L,
            listId = 1L,
            name = "远程商品",
            brand = "品牌B",
            model = "型号2",
            parameters = "参数2",
            barcode = "456",
            quantity = 20,
            remark = ""
        )
        val conflict = SyncConflictItem(
            id = 1L,
            localItem = localItem,
            remoteItem = remoteItem,
            type = SyncConflictType.Modified
        )
        
        val backupFile = File("backup.db")
        whenever(mockInventoryRepository.getMaxLastModified()).thenReturn(1L)
        whenever(mockPrefs.getLong("sync_last_push_at", 0L)).thenReturn(0L)
        whenever(mockExportRepository.backupDatabase(null)).thenReturn(backupFile)
        whenever(mockStorageRepository.uploadBackup(any(), any())).thenReturn("new-key")
        
        // When
        val result = repository.resolveConflict(conflict, ConflictResolution.KeepRemote)
        
        // Then
        assertTrue(result)
        verify(mockInventoryRepository).updateItem(remoteItem)
        verify(mockExportRepository).backupDatabase(null)
    }
    
    private fun setupValidS3Config() {
        whenever(mockPrefs.getString("s3_endpoint", "")).thenReturn("https://s3.example.com")
        whenever(mockPrefs.getString("s3_region", "")).thenReturn("us-east-1")
        whenever(mockPrefs.getString("s3_bucket", "")).thenReturn("test-bucket")
        whenever(mockPrefs.getString("s3_access_key", "")).thenReturn("access-key")
        whenever(mockPrefs.getString("s3_secret_key", "")).thenReturn("secret-key")
    }
}
