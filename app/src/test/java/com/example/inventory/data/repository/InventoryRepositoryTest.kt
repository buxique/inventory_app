package com.example.inventory.data.repository

import androidx.paging.PagingSource
import com.example.inventory.data.db.CategoryDao
import com.example.inventory.data.db.InventoryDao
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * InventoryRepository 单元测试
 * 
 * 测试 InventoryRepositoryImpl 的基本功能
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InventoryRepositoryTest {
    
    @Mock
    private lateinit var mockDao: InventoryDao
    
    @Mock
    private lateinit var mockCategoryDao: CategoryDao

    @Mock
    private lateinit var mockDatabase: InventoryDatabase
    
    private lateinit var repository: InventoryRepositoryImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = InventoryRepositoryImpl(mockDao, mockCategoryDao, mockDatabase)
    }
    
    @Test
    fun `getItems should return PagingSource from dao`() = runTest {
        // Given
        @Suppress("UNCHECKED_CAST")
        val mockPagingSource = mock(PagingSource::class.java) as PagingSource<Int, InventoryItemEntity>
        `when`(mockDao.getAllItems()).thenReturn(mockPagingSource)
        
        // When
        repository.getItems().first()
        
        // Then
        verify(mockDao).getAllItems()
    }
    
    @Test
    fun `addItem should call dao insert`() = runTest {
        // Given
        val item = InventoryItemEntity(
            listId = 1L,
            name = "新商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 0,
            remark = ""
        )
        val expectedId = 1L
        `when`(mockDao.insertItem(org.mockito.kotlin.any())).thenReturn(expectedId)
        
        // When
        val result = repository.addItem(item)
        
        // Then
        assertEquals(expectedId, result)
        verify(mockDao).insertItem(
            org.mockito.kotlin.argThat {
                listId == 1L &&
                    name == "新商品" &&
                    brand == "品牌" &&
                    model == "型号" &&
                    quantity == 0 &&
                    lastModified > 0L
            }
        )
    }
    
    @Test
    fun `updateItem should call dao update`() = runTest {
        // Given
        val item = InventoryItemEntity(
            id = 1L,
            listId = 1L,
            name = "更新商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )
        
        // When
        repository.updateItem(item)
        
        // Then
        verify(mockDao).updateItem(
            org.mockito.kotlin.argThat {
                id == 1L &&
                    listId == 1L &&
                    name == "更新商品" &&
                    quantity == 10 &&
                    lastModified > 0L
            }
        )
    }
    
    @Test
    fun `deleteItem should call dao delete`() = runTest {
        // Given
        val itemId = 1L
        
        // When
        repository.deleteItem(itemId)
        
        // Then
        verify(mockDao).deleteItem(itemId)
    }
    
    @Test
    fun `getRecords with valid itemId should return records`() = runTest {
        // Given
        val itemId = 1L
        val expectedRecords = listOf(
            StockRecordEntity(
                itemId = itemId,
                change = 5,
                operatorName = "操作员",
                remark = "入库",
                timestamp = System.currentTimeMillis()
            )
        )
        `when`(mockDao.getRecordsForItem(itemId)).thenReturn(expectedRecords)
        
        // When
        val result = repository.getRecords(itemId)
        
        // Then
        assertEquals(expectedRecords, result)
        verify(mockDao).getRecordsForItem(itemId)
    }
    
    @Test
    fun `getRecords with invalid itemId should return empty list`() = runTest {
        // Given
        val invalidItemId = 0L
        
        // When
        val result = repository.getRecords(invalidItemId)
        
        // Then
        assertEquals(emptyList<StockRecordEntity>(), result)
        verify(mockDao, never()).getRecordsForItem(anyLong())
    }
    
    @Test
    fun `getAllItemsSnapshot should return all items`() = runTest {
        // Given
        val expectedItems = listOf(
            InventoryItemEntity(
                id = 1,
                listId = 1L,
                name = "商品1",
                brand = "品牌A",
                model = "型号A",
                parameters = "参数",
                barcode = "123456",
                quantity = 10,
                remark = "备注"
            ),
            InventoryItemEntity(
                id = 2,
                listId = 1L,
                name = "商品2",
                brand = "品牌B",
                model = "型号B",
                parameters = "参数",
                barcode = "789012",
                quantity = 20,
                remark = "备注"
            )
        )
        `when`(mockDao.getAllItemsSnapshot()).thenReturn(expectedItems)
        
        // When
        val result = repository.getAllItemsSnapshot()
        
        // Then
        assertEquals(expectedItems, result)
        verify(mockDao).getAllItemsSnapshot()
    }
}
