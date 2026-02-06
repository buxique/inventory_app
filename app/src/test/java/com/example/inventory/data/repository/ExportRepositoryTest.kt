package com.example.inventory.data.repository

import android.content.Context
import com.example.inventory.data.model.InventoryItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import java.io.File

/**
 * ExportRepository 单元测试
 * 
 * 测试覆盖：
 * - CSV导出功能
 * - Excel导出功能
 * - 数据库备份功能
 * - 数据库恢复功能
 * - 进度回调
 * - 异常处理
 */
class ExportRepositoryTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var repository: ExportRepositoryImpl
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testDir = File(System.getProperty("java.io.tmpdir"), "test_export")
        testDir.mkdirs()
        
        `when`(mockContext.filesDir).thenReturn(testDir)
        `when`(mockContext.getExternalFilesDir(null)).thenReturn(testDir)
        
        repository = ExportRepositoryImpl(mockContext)
    }
    
    @Test
    fun `exportCsv should create valid CSV file`() = runTest {
        // Given
        val items = listOf(
            InventoryItemEntity(
                id = 1L,
                listId = 1L,
                name = "商品A",
                brand = "品牌A",
                model = "型号1",
                parameters = "参数1",
                barcode = "123456",
                quantity = 10,
                remark = "备注1"
            ),
            InventoryItemEntity(
                id = 2L,
                listId = 1L,
                name = "商品B",
                brand = "品牌B",
                model = "型号2",
                parameters = "参数2",
                barcode = "789012",
                quantity = 20,
                remark = "备注2"
            )
        )
        
        var progressCalled = false
        val onProgress: ProgressCallback = { current, total, message ->
            progressCalled = true
        }
        
        // When
        val file = repository.exportCsv(items, onProgress)
        
        // Then
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".csv"))
        assertTrue(progressCalled)
        
        val content = file.readText()
        assertTrue(content.contains("name,brand,model"))
        assertTrue(content.contains("商品A"))
        assertTrue(content.contains("商品B"))
        
        file.delete()
    }
    
    @Test
    fun `exportCsv should handle empty list`() = runTest {
        // Given
        val items = emptyList<InventoryItemEntity>()
        
        // When
        val file = repository.exportCsv(items)
        
        // Then
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("name,brand,model"))
        assertEquals(1, content.lines().filter { it.isNotBlank() }.size) // 只有表头
        
        file.delete()
    }
    
    @Test
    fun `exportCsv should escape special characters`() = runTest {
        // Given
        val items = listOf(
            InventoryItemEntity(
                id = 1L,
                listId = 1L,
                name = "商品,带逗号",
                brand = "品牌\"带引号\"",
                model = "型号\n带换行",
                parameters = "参数",
                barcode = "123",
                quantity = 10,
                remark = "备注"
            )
        )
        
        // When
        val file = repository.exportCsv(items)
        
        // Then
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"商品,带逗号\""))
        assertTrue(content.contains("\"品牌\"\"带引号\"\"\""))
        
        file.delete()
    }
    
    @Test
    fun `exportXlsx should create valid Excel file`() = runTest {
        // Given
        val items = listOf(
            InventoryItemEntity(
                id = 1L,
                listId = 1L,
                name = "商品A",
                brand = "品牌A",
                model = "型号1",
                parameters = "参数1",
                barcode = "123456",
                quantity = 10,
                remark = "备注1"
            )
        )
        
        var progressCalled = false
        val onProgress: ProgressCallback = { current, total, message ->
            progressCalled = true
        }
        
        // When
        val file = repository.exportXlsx(items, onProgress)
        
        // Then
        assertTrue(file.exists())
        assertTrue(file.name.endsWith(".xlsx"))
        assertTrue(progressCalled)
        assertTrue(file.length() > 0)
        
        file.delete()
    }
    
    @Test
    fun `exportXlsx should handle empty list`() = runTest {
        // Given
        val items = emptyList<InventoryItemEntity>()
        
        // When
        val file = repository.exportXlsx(items)
        
        // Then
        assertTrue(file.exists())
        assertTrue(file.length() > 0) // Excel文件即使没有数据也有结构
        
        file.delete()
    }
    
    @Test
    fun `backupDatabase should return null when database does not exist`() = runTest {
        // Given
        val dbPath = File(testDir, "inventory.db")
        `when`(mockContext.getDatabasePath("inventory.db")).thenReturn(dbPath)
        
        // When
        val backup = repository.backupDatabase()
        
        // Then
        assertNull(backup)
    }
    
    @Test
    fun `backupDatabase should create backup file when database exists`() = runTest {
        // Given
        val dbPath = File(testDir, "inventory.db")
        dbPath.writeText("fake database content")
        `when`(mockContext.getDatabasePath("inventory.db")).thenReturn(dbPath)
        
        var progressCalled = false
        val onProgress: ProgressCallback = { current, total, message ->
            progressCalled = true
        }
        
        // When
        val backup = repository.backupDatabase(onProgress)
        
        // Then
        assertNotNull(backup)
        assertTrue(backup!!.exists())
        assertTrue(backup.name.startsWith("inventory-backup-"))
        assertTrue(backup.name.endsWith(".db"))
        assertTrue(progressCalled)
        assertEquals("fake database content", backup.readText())
        
        dbPath.delete()
        backup.delete()
    }
    
    @Test
    fun `restoreDatabase should return false when backup file does not exist`() = runTest {
        // Given
        val backupFile = File(testDir, "nonexistent.db")
        val dbPath = File(testDir, "inventory.db")
        `when`(mockContext.getDatabasePath("inventory.db")).thenReturn(dbPath)
        
        // When
        val result = repository.restoreDatabase(backupFile)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `restoreDatabase should return false when backup file is invalid`() = runTest {
        // Given
        val backupFile = File(testDir, "invalid.db")
        backupFile.writeText("not a valid database")
        val dbPath = File(testDir, "inventory.db")
        `when`(mockContext.getDatabasePath("inventory.db")).thenReturn(dbPath)
        
        // When
        val result = repository.restoreDatabase(backupFile)
        
        // Then
        assertFalse(result)
        
        backupFile.delete()
    }
    
    @Test
    fun `progress callback should be invoked during export`() = runTest {
        // Given
        val items = (1..50).map { i ->
            InventoryItemEntity(
                id = i.toLong(),
                listId = 1L,
                name = "商品$i",
                brand = "品牌",
                model = "型号",
                parameters = "参数",
                barcode = "123",
                quantity = 10,
                remark = ""
            )
        }
        
        val progressValues = mutableListOf<Int>()
        val onProgress: ProgressCallback = { current, total, message ->
            progressValues.add(current)
        }
        
        // When
        val file = repository.exportCsv(items, onProgress)
        
        // Then
        assertTrue(progressValues.isNotEmpty())
        assertTrue(progressValues.last() == items.size) // 最后一次应该是总数
        
        file.delete()
    }
}
