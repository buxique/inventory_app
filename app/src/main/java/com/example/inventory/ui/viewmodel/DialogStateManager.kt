package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.state.ManualAddForm
import com.example.inventory.ui.state.StockAction
import com.example.inventory.ui.state.StockActionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 对话框状态管理器
 * 
 * 职责：
 * - 管理所有对话框的显示/隐藏状态
 * - 管理对话框相关的表单数据
 * - 提供统一的对话框操作接口
 */
class DialogStateManager {
    
    private val _currentDialog = MutableStateFlow<DialogState>(DialogState.None)
    val currentDialog: StateFlow<DialogState> = _currentDialog.asStateFlow()
    
    private val _selectedItem = MutableStateFlow<InventoryItemEntity?>(null)
    val selectedItem: StateFlow<InventoryItemEntity?> = _selectedItem.asStateFlow()
    
    private val _pendingDeleteItem = MutableStateFlow<InventoryItemEntity?>(null)
    val pendingDeleteItem: StateFlow<InventoryItemEntity?> = _pendingDeleteItem.asStateFlow()
    
    private val _manualAddForm = MutableStateFlow(ManualAddForm())
    val manualAddForm: StateFlow<ManualAddForm> = _manualAddForm.asStateFlow()
    
    private val _editText = MutableStateFlow("")
    val editText: StateFlow<String> = _editText.asStateFlow()
    
    private val _stockActionState = MutableStateFlow(StockActionState())
    val stockActionState: StateFlow<StockActionState> = _stockActionState.asStateFlow()
    
    // ==================== 通用对话框操作 ====================
    
    /**
     * 关闭当前对话框
     */
    fun dismissDialog() {
        _currentDialog.value = DialogState.None
    }
    
    /**
     * 设置选中的商品
     */
    fun setSelectedItem(item: InventoryItemEntity?) {
        _selectedItem.value = item
    }
    
    // ==================== 导入对话框 ====================
    
    fun showImportSheet() {
        _currentDialog.value = DialogState.ImportSheet
    }
    
    // ==================== 搜索对话框 ====================
    
    fun showSearchDialog() {
        _currentDialog.value = DialogState.SearchDialog
    }
    
    // ==================== 分类对话框 ====================
    
    fun showCategoryDialog() {
        _currentDialog.value = DialogState.CategoryDialog
    }
    
    // ==================== 手动添加对话框 ====================
    
    fun showManualAddDialog() {
        _currentDialog.value = DialogState.ManualAddDialog
    }
    
    fun updateManualAddForm(update: (ManualAddForm) -> ManualAddForm) {
        _manualAddForm.update(update)
    }
    
    fun resetManualAddForm() {
        _manualAddForm.value = ManualAddForm()
    }
    
    // ==================== 编辑对话框 ====================
    
    fun showEditDialog(item: InventoryItemEntity) {
        _selectedItem.value = item
        _editText.value = item.name
        _currentDialog.value = DialogState.EditDialog
    }
    
    fun updateEditText(text: String) {
        _editText.value = text
    }
    
    // ==================== 删除确认对话框 ====================
    
    fun showDeleteConfirmDialog(item: InventoryItemEntity) {
        _pendingDeleteItem.value = item
        _currentDialog.value = DialogState.DeleteConfirmDialog
    }
    
    fun clearPendingDelete() {
        _pendingDeleteItem.value = null
    }
    
    // ==================== 库存操作对话框 ====================
    
    fun showStockActionDialog(item: InventoryItemEntity) {
        _selectedItem.value = item
        _currentDialog.value = DialogState.StockActionDialog
    }
    
    fun showRecordInputDialog(action: StockAction) {
        _stockActionState.update { it.copy(action = action) }
        _currentDialog.value = DialogState.RecordInputDialog
    }
    
    fun showRecordDialog() {
        _currentDialog.value = DialogState.RecordDialog
    }

    fun showAutoFillDialog() {
        _currentDialog.value = DialogState.AutoFillDialog
    }
    
    fun updateStockActionQuantity(value: String) {
        _stockActionState.update { it.copy(quantity = value) }
    }
    
    fun updateStockActionOperator(value: String) {
        _stockActionState.update { it.copy(operator = value) }
    }
    
    fun updateStockActionRemark(value: String) {
        _stockActionState.update { it.copy(remark = value) }
    }
    
    fun resetStockActionState() {
        _stockActionState.value = StockActionState()
    }
    
    // ==================== 过滤对话框 ====================
    
    fun showFilterDialog() {
        _currentDialog.value = DialogState.FilterDialog
    }
    
    fun showFilterDetailDialog() {
        _currentDialog.value = DialogState.FilterDetailDialog
    }
}
