package com.example.inventory.ui.state

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity

/**
 * 库存页面UI状态
 */
data class InventoryUiState(
    // items 已移除，改用 PagingData
    val selectedItem: InventoryItemEntity? = null,
    val dialogState: DialogState = DialogState.None,
    val editText: String = "",
    val records: List<StockRecordEntity> = emptyList(),
    val stockActionState: StockActionState = StockActionState(),
    val filterState: FilterState = FilterState(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: UiMessage? = null,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val categories: List<com.example.inventory.data.model.CategoryEntity> = emptyList(),
    val manualAddForm: ManualAddForm = ManualAddForm(),
    val pendingDeleteItem: InventoryItemEntity? = null
)

/**
 * 对话框状态 - 使用sealed class确保互斥
 */
sealed class DialogState {
    object None : DialogState()
    
    object ImportSheet : DialogState()
    
    object EditDialog : DialogState()
    
    object RecordDialog : DialogState()
    
    object StockActionDialog : DialogState()
    
    object RecordInputDialog : DialogState()
    
    object FilterDialog : DialogState()
    
    object FilterDetailDialog : DialogState()

    object SearchDialog : DialogState()

    object CategoryDialog : DialogState()

    object ManualAddDialog : DialogState()

    object DeleteConfirmDialog : DialogState()

    object AutoFillDialog : DialogState()
}

/**
 * 库存操作状态
 */
data class StockActionState(
    val action: StockAction = StockAction.Inbound,
    val quantity: String = "",
    val operator: String = "",
    val remark: String = ""
)

/**
 * 手动添加表单状态
 */
data class ManualAddForm(
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val parameters: String = "",
    val barcode: String = "",
    val quantity: String = "",
    val remark: String = ""
)

/**
 * 筛选器状态
 */
data class FilterState(
    val expanded: Boolean = false,
    val selectedFilter: RecordFilter = RecordFilter.Date,
    val selectedOption: String = "",
    val selectedDetail: String = ""
)

enum class StockAction {
    Inbound,
    Outbound,
    Count
}

enum class RecordFilter {
    Date,
    Quantity,
    Operator
}
