package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ItemOperationViewModel 单元测试
 * 
 * 测试商品操作功能
 */
@ExperimentalCoroutinesApi
class ItemOperationViewModelTest {
    
    @Mock
    private lateinit var inventoryRepository: InventoryRepository
    
    private lateinit var viewModel: ItemOperationViewModel
    
    private val testItem = InventoryItemEntity(
        id = 1L,
        name = "测试商品",
        brand = "测试品牌",
        model = "测试型号",
        parameters = "测试参数",
        barcode = "123456",
        quantity = 10,
        remark = "测试备注"
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = ItemOperationViewModel(inventoryRepository)
    }
    
    @Test
    fun `addItem should return success with item id`() = runTest {
        // Given
        val expectedId = 123L
        whenever(inventoryRepository.addItem(any())).thenAnswer { expectedId }
        
        // When
        val result = viewModel.addItem(testItem)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedId, result.getOrNull())
        verify(inventoryRepository).addItem(testItem)
    }
    
    @Test
    fun `addItem should return failure on exception`() = runTest {
        // Given
        val exception = RuntimeException("添加失败")
        whenever(inventoryRepository.addItem(any())).thenThrow(exception)
        
        // When
        val result = viewModel.addItem(testItem)
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `updateItem should return success`() = runTest {
        // Given
        whenever(inventoryRepository.updateItem(any())).thenAnswer { }
        
        // When
        val result = viewModel.updateItem(testItem)
        
        // Then
        assertTrue(result.isSuccess)
        verify(inventoryRepository).updateItem(testItem)
    }
    
    @Test
    fun `deleteItem should return success`() = runTest {
        // Given
        val itemId = 123L
        whenever(inventoryRepository.deleteItem(itemId)).thenAnswer { }
        
        // When
        val result = viewModel.deleteItem(itemId)
        
        // Then
        assertTrue(result.isSuccess)
        verify(inventoryRepository).deleteItem(itemId)
    }
    
    @Test
    fun `batchAddItems should return success`() = runTest {
        // Given
        val items = listOf(testItem, testItem.copy(id = 2L))
        whenever(inventoryRepository.batchAddItems(items)).thenAnswer { }
        
        // When
        val result = viewModel.batchAddItems(items)
        
        // Then
        assertTrue(result.isSuccess)
        verify(inventoryRepository).batchAddItems(items)
    }
    
    @Test
    fun `copyItem should store item in state`() = runTest {
        // When
        viewModel.copyItem(testItem)
        
        // Then
        assertEquals(testItem, viewModel.copiedItem.first())
    }
    
    @Test
    fun `pasteItem should create copy with new id and modified name`() = runTest {
        // Given
        viewModel.copyItem(testItem)
        val expectedId = 456L
        whenever(inventoryRepository.addItem(any())).thenAnswer { expectedId }
        
        // When
        val result = viewModel.pasteItem()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedId, result.getOrNull())
        
        // 验证添加的商品名称包含"-副本"
        verify(inventoryRepository).addItem(
            org.mockito.kotlin.argThat { item ->
                item.name.contains("-副本") && item.id == 0L
            }
        )
    }
    
    @Test
    fun `pasteItem without copied item should return failure`() = runTest {
        // When
        val result = viewModel.pasteItem()
        
        // Then
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
    
    @Test
    fun `clearCopiedItem should reset copied item to null`() = runTest {
        // Given
        viewModel.copyItem(testItem)
        
        // When
        viewModel.clearCopiedItem()
        
        // Then
        assertNull(viewModel.copiedItem.first())
    }
    
    @Test
    fun `hasCopiedItem should return true when item is copied`() {
        // Given
        viewModel.copyItem(testItem)
        
        // Then
        assertTrue(viewModel.hasCopiedItem())
    }
    
    @Test
    fun `hasCopiedItem should return false when no item is copied`() {
        // Then
        assertFalse(viewModel.hasCopiedItem())
    }
    
    @Test
    fun `initial state should have no copied item`() = runTest {
        // Then
        assertNull(viewModel.copiedItem.first())
        assertFalse(viewModel.hasCopiedItem())
    }
}
