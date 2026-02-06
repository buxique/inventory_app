package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.StockAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * StockRecordViewModel 单元测试
 * 
 * 测试库存记录管理功能
 */
@ExperimentalCoroutinesApi
class StockRecordViewModelTest {
    
    @Mock
    private lateinit var inventoryRepository: InventoryRepository
    
    private lateinit var viewModel: StockRecordViewModel
    
    private val testItem = InventoryItemEntity(
        id = 1L,
        name = "测试商品",
        brand = "测试品牌",
        model = "测试型号",
        parameters = "测试参数",
        barcode = "123456",
        quantity = 100,
        remark = "测试备注"
    )
    
    private val testRecords = listOf(
        StockRecordEntity(
            id = 1L,
            itemId = 1L,
            change = 50,
            operatorName = "张三",
            remark = "入库",
            timestamp = System.currentTimeMillis()
        ),
        StockRecordEntity(
            id = 2L,
            itemId = 1L,
            change = -20,
            operatorName = "李四",
            remark = "出库",
            timestamp = System.currentTimeMillis()
        )
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = StockRecordViewModel(inventoryRepository)
    }
    
    @Test
    fun `loadRecords should update records state`() = runTest {
        // Given
        val itemId = 1L
        whenever(inventoryRepository.getRecords(itemId)).thenReturn(testRecords)
        
        // When
        viewModel.loadRecords(itemId)
        
        // Then
        assertEquals(testRecords, viewModel.records.first())
        verify(inventoryRepository).getRecords(itemId)
    }
    
    @Test
    fun `loadRecords should set loading state`() = runTest {
        // Given
        val itemId = 1L
        whenever(inventoryRepository.getRecords(itemId)).thenReturn(testRecords)
        
        // When
        viewModel.loadRecords(itemId)
        
        // Then - 验证加载完成后 loading 为 false
        assertFalse(viewModel.isLoading.first())
    }
    
    @Test
    fun `addRecord with inbound action should increase quantity`() = runTest {
        // Given
        val quantity = 50
        val operatorName = "张三"
        val remark = "入库测试"
        whenever(inventoryRepository.getRecords(testItem.id)).thenReturn(testRecords)
        
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = quantity,
            operatorName = operatorName,
            remark = remark
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedItem = result.getOrNull()!!
        assertEquals(150, updatedItem.quantity) // 100 + 50
        
        // 验证更新商品
        verify(inventoryRepository).updateItem(
            org.mockito.kotlin.argThat { item -> item.quantity == 150 }
        )
        
        // 验证添加记录
        verify(inventoryRepository).addRecord(
            org.mockito.kotlin.argThat { record ->
                record.itemId == testItem.id && 
                record.change == 50 && 
                record.operatorName == operatorName &&
                record.remark == remark
            }
        )
    }
    
    @Test
    fun `addRecord with outbound action should decrease quantity`() = runTest {
        // Given
        val quantity = 30
        val operatorName = "李四"
        val remark = "出库测试"
        whenever(inventoryRepository.getRecords(testItem.id)).thenReturn(testRecords)
        
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Outbound,
            quantity = quantity,
            operatorName = operatorName,
            remark = remark
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedItem = result.getOrNull()!!
        assertEquals(70, updatedItem.quantity) // 100 - 30
        
        // 验证添加记录时 change 为负数
        verify(inventoryRepository).addRecord(
            org.mockito.kotlin.argThat { record -> record.change == -30 }
        )
    }
    
    @Test
    fun `addRecord with count action should not change quantity`() = runTest {
        // Given
        val quantity = 50
        val operatorName = "王五"
        val remark = "盘点测试"
        whenever(inventoryRepository.getRecords(testItem.id)).thenReturn(testRecords)
        
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Count,
            quantity = quantity,
            operatorName = operatorName,
            remark = remark
        )
        
        // Then
        assertTrue(result.isSuccess)
        val updatedItem = result.getOrNull()!!
        assertEquals(100, updatedItem.quantity) // 数量不变
        
        // 验证添加记录时 change 为 0
        verify(inventoryRepository).addRecord(
            org.mockito.kotlin.argThat { record -> record.change == 0 }
        )
    }
    
    @Test
    fun `addRecord with zero quantity should return failure`() = runTest {
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = 0,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("数量必须大于0", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `addRecord with negative quantity should return failure`() = runTest {
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = -10,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("数量必须大于0", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `addRecord with blank operator name should return failure`() = runTest {
        // Given
        val blankNames = listOf("", "   ", "\t", "\n")
        
        // When & Then
        blankNames.forEach { name ->
            val result = viewModel.addRecord(
                item = testItem,
                action = StockAction.Inbound,
                quantity = 10,
                operatorName = name,
                remark = "测试"
            )
            
            assertTrue("空白操作员名称 '$name' 应该返回失败", result.isFailure)
            assertEquals("操作员姓名不能为空", result.exceptionOrNull()?.message)
        }
    }
    
    @Test
    fun `addRecord outbound with quantity exceeding stock should return failure`() = runTest {
        // Given - 当前库存 100，尝试出库 150
        val quantity = 150
        
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Outbound,
            quantity = quantity,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertEquals("出库数量不能大于当前库存(100)", result.exceptionOrNull()?.message)
    }
    
    @Test
    fun `addRecord should trim operator name and remark`() = runTest {
        // Given
        val operatorName = "  张三  "
        val remark = "  测试备注  "
        whenever(inventoryRepository.getRecords(testItem.id)).thenReturn(testRecords)
        
        // When
        viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = 10,
            operatorName = operatorName,
            remark = remark
        )
        
        // Then
        verify(inventoryRepository).addRecord(
            org.mockito.kotlin.argThat { record ->
                record.operatorName == "张三" && record.remark == "测试备注"
            }
        )
    }
    
    @Test
    fun `addRecord should reload records after success`() = runTest {
        // Given
        whenever(inventoryRepository.getRecords(testItem.id)).thenReturn(testRecords)
        
        // When
        viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = 10,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then
        verify(inventoryRepository).getRecords(testItem.id)
        assertEquals(testRecords, viewModel.records.first())
    }
    
    @Test
    fun `addRecord with repository exception should return failure`() = runTest {
        // Given
        val exception = RuntimeException("数据库错误")
        whenever(inventoryRepository.updateItem(any())).thenThrow(exception)
        
        // When
        val result = viewModel.addRecord(
            item = testItem,
            action = StockAction.Inbound,
            quantity = 10,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `clearRecords should reset records to empty list`() = runTest {
        // Given
        whenever(inventoryRepository.getRecords(any())).thenReturn(testRecords)
        viewModel.loadRecords(1L)
        assertEquals(testRecords, viewModel.records.first())
        
        // When
        viewModel.clearRecords()
        
        // Then
        assertEquals(emptyList<StockRecordEntity>(), viewModel.records.first())
    }
    
    @Test
    fun `initial state should have empty records`() = runTest {
        // Then
        assertEquals(emptyList<StockRecordEntity>(), viewModel.records.first())
        assertFalse(viewModel.isLoading.first())
    }
    
    @Test
    fun `addRecord should prevent negative quantity after outbound`() = runTest {
        // Given - 商品库存为 10
        val lowStockItem = testItem.copy(quantity = 10)
        whenever(inventoryRepository.getRecords(lowStockItem.id)).thenReturn(testRecords)
        
        // When - 出库 10
        val result = viewModel.addRecord(
            item = lowStockItem,
            action = StockAction.Outbound,
            quantity = 10,
            operatorName = "张三",
            remark = "测试"
        )
        
        // Then - 库存应该变为 0，不会是负数
        assertTrue(result.isSuccess)
        val updatedItem = result.getOrNull()!!
        assertEquals(0, updatedItem.quantity)
        assertTrue(updatedItem.quantity >= 0)
    }
}
