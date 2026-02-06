package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.state.ManualAddForm
import com.example.inventory.ui.state.StockAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * DialogStateManager 单元测试
 * 
 * 测试对话框状态管理功能
 */
@ExperimentalCoroutinesApi
class DialogStateManagerTest {
    
    private lateinit var manager: DialogStateManager
    
    private val testItem = InventoryItemEntity(
        id = 1L,
        listId = 1L,
        name = "测试商品",
        brand = "测试品牌",
        model = "测试型号",
        parameters = "测试参数",
        barcode = "123456",
        quantity = 100,
        remark = "测试备注"
    )
    
    @Before
    fun setup() {
        manager = DialogStateManager()
    }
    
    // ==================== 通用对话框操作测试 ====================
    
    @Test
    fun `dismissDialog should set dialog to None`() = runTest {
        // Given
        manager.showSearchDialog()
        
        // When
        manager.dismissDialog()
        
        // Then
        assertEquals(DialogState.None, manager.currentDialog.first())
    }
    
    @Test
    fun `setSelectedItem should update selected item`() = runTest {
        // When
        manager.setSelectedItem(testItem)
        
        // Then
        assertEquals(testItem, manager.selectedItem.first())
    }
    
    @Test
    fun `setSelectedItem with null should clear selected item`() = runTest {
        // Given
        manager.setSelectedItem(testItem)
        
        // When
        manager.setSelectedItem(null)
        
        // Then
        assertNull(manager.selectedItem.first())
    }
    
    // ==================== 导入对话框测试 ====================
    
    @Test
    fun `showImportSheet should set dialog to ImportSheet`() = runTest {
        // When
        manager.showImportSheet()
        
        // Then
        assertEquals(DialogState.ImportSheet, manager.currentDialog.first())
    }
    
    // ==================== 搜索对话框测试 ====================
    
    @Test
    fun `showSearchDialog should set dialog to SearchDialog`() = runTest {
        // When
        manager.showSearchDialog()
        
        // Then
        assertEquals(DialogState.SearchDialog, manager.currentDialog.first())
    }
    
    // ==================== 分类对话框测试 ====================
    
    @Test
    fun `showCategoryDialog should set dialog to CategoryDialog`() = runTest {
        // When
        manager.showCategoryDialog()
        
        // Then
        assertEquals(DialogState.CategoryDialog, manager.currentDialog.first())
    }
    
    // ==================== 手动添加对话框测试 ====================
    
    @Test
    fun `showManualAddDialog should set dialog to ManualAddDialog`() = runTest {
        // When
        manager.showManualAddDialog()
        
        // Then
        assertEquals(DialogState.ManualAddDialog, manager.currentDialog.first())
    }
    
    @Test
    fun `updateManualAddForm should update form state`() = runTest {
        // When
        manager.updateManualAddForm { it.copy(name = "新商品") }
        
        // Then
        assertEquals("新商品", manager.manualAddForm.first().name)
    }
    
    @Test
    fun `resetManualAddForm should reset to default form`() = runTest {
        // Given
        manager.updateManualAddForm { it.copy(name = "测试", barcode = "123") }
        
        // When
        manager.resetManualAddForm()
        
        // Then
        assertEquals(ManualAddForm(), manager.manualAddForm.first())
    }
    
    // ==================== 编辑对话框测试 ====================
    
    @Test
    fun `showEditDialog should set dialog and update item and text`() = runTest {
        // When
        manager.showEditDialog(testItem)
        
        // Then
        assertEquals(DialogState.EditDialog, manager.currentDialog.first())
        assertEquals(testItem, manager.selectedItem.first())
        assertEquals(testItem.name, manager.editText.first())
    }
    
    @Test
    fun `updateEditText should update edit text state`() = runTest {
        // When
        manager.updateEditText("新名称")
        
        // Then
        assertEquals("新名称", manager.editText.first())
    }
    
    // ==================== 删除确认对话框测试 ====================
    
    @Test
    fun `showDeleteConfirmDialog should set dialog and pending delete item`() = runTest {
        // When
        manager.showDeleteConfirmDialog(testItem)
        
        // Then
        assertEquals(DialogState.DeleteConfirmDialog, manager.currentDialog.first())
        assertEquals(testItem, manager.pendingDeleteItem.first())
    }
    
    @Test
    fun `clearPendingDelete should reset pending delete item`() = runTest {
        // Given
        manager.showDeleteConfirmDialog(testItem)
        
        // When
        manager.clearPendingDelete()
        
        // Then
        assertNull(manager.pendingDeleteItem.first())
    }
    
    // ==================== 库存操作对话框测试 ====================
    
    @Test
    fun `showStockActionDialog should set dialog and selected item`() = runTest {
        // When
        manager.showStockActionDialog(testItem)
        
        // Then
        assertEquals(DialogState.StockActionDialog, manager.currentDialog.first())
        assertEquals(testItem, manager.selectedItem.first())
    }
    
    @Test
    fun `showRecordInputDialog should set dialog and action`() = runTest {
        // When
        manager.showRecordInputDialog(StockAction.Inbound)
        
        // Then
        assertEquals(DialogState.RecordInputDialog, manager.currentDialog.first())
        assertEquals(StockAction.Inbound, manager.stockActionState.first().action)
    }
    
    @Test
    fun `showRecordDialog should set dialog to RecordDialog`() = runTest {
        // When
        manager.showRecordDialog()
        
        // Then
        assertEquals(DialogState.RecordDialog, manager.currentDialog.first())
    }
    
    @Test
    fun `updateStockActionQuantity should update quantity state`() = runTest {
        // When
        manager.updateStockActionQuantity("50")
        
        // Then
        assertEquals("50", manager.stockActionState.first().quantity)
    }
    
    @Test
    fun `updateStockActionOperator should update operator state`() = runTest {
        // When
        manager.updateStockActionOperator("张三")
        
        // Then
        assertEquals("张三", manager.stockActionState.first().operator)
    }
    
    @Test
    fun `updateStockActionRemark should update remark state`() = runTest {
        // When
        manager.updateStockActionRemark("测试备注")
        
        // Then
        assertEquals("测试备注", manager.stockActionState.first().remark)
    }
    
    @Test
    fun `resetStockActionState should reset to default state`() = runTest {
        // Given
        manager.updateStockActionQuantity("50")
        manager.updateStockActionOperator("张三")
        manager.updateStockActionRemark("测试")
        
        // When
        manager.resetStockActionState()
        
        // Then
        val state = manager.stockActionState.first()
        assertEquals("", state.quantity)
        assertEquals("", state.operator)
        assertEquals("", state.remark)
    }
    
    // ==================== 过滤对话框测试 ====================
    
    @Test
    fun `showFilterDialog should set dialog to FilterDialog`() = runTest {
        // When
        manager.showFilterDialog()
        
        // Then
        assertEquals(DialogState.FilterDialog, manager.currentDialog.first())
    }
    
    @Test
    fun `showFilterDetailDialog should set dialog to FilterDetailDialog`() = runTest {
        // When
        manager.showFilterDetailDialog()
        
        // Then
        assertEquals(DialogState.FilterDetailDialog, manager.currentDialog.first())
    }
    
    // ==================== 初始状态测试 ====================
    
    @Test
    fun `initial state should be None dialog`() = runTest {
        // Then
        assertEquals(DialogState.None, manager.currentDialog.first())
    }
    
    @Test
    fun `initial state should have no selected item`() = runTest {
        // Then
        assertNull(manager.selectedItem.first())
    }
    
    @Test
    fun `initial state should have no pending delete item`() = runTest {
        // Then
        assertNull(manager.pendingDeleteItem.first())
    }
    
    @Test
    fun `initial state should have default manual add form`() = runTest {
        // Then
        assertEquals(ManualAddForm(), manager.manualAddForm.first())
    }
    
    @Test
    fun `initial state should have empty edit text`() = runTest {
        // Then
        assertEquals("", manager.editText.first())
    }
    
    // ==================== 对话框切换测试 ====================
    
    @Test
    fun `switching between dialogs should work correctly`() = runTest {
        // 搜索对话框
        manager.showSearchDialog()
        assertEquals(DialogState.SearchDialog, manager.currentDialog.first())
        
        // 切换到分类对话框
        manager.showCategoryDialog()
        assertEquals(DialogState.CategoryDialog, manager.currentDialog.first())
        
        // 切换到编辑对话框
        manager.showEditDialog(testItem)
        assertEquals(DialogState.EditDialog, manager.currentDialog.first())
        
        // 关闭对话框
        manager.dismissDialog()
        assertEquals(DialogState.None, manager.currentDialog.first())
    }
    
    @Test
    fun `multiple updates to same state should work correctly`() = runTest {
        // 多次更新编辑文本
        manager.updateEditText("文本1")
        assertEquals("文本1", manager.editText.first())
        
        manager.updateEditText("文本2")
        assertEquals("文本2", manager.editText.first())
        
        manager.updateEditText("文本3")
        assertEquals("文本3", manager.editText.first())
    }
}
