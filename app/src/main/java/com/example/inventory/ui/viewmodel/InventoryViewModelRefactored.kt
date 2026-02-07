package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.InventoryUiState
import com.example.inventory.ui.state.StockAction
import com.example.inventory.ui.state.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 重构后的库存列表 ViewModel
 * 
 * 职责：
 * - 协调各个子ViewModel
 * - 管理UI状态
 * - 处理用户交互
 * 
 * 将原来的职责拆分到：
 * - SearchViewModel: 搜索和过滤
 * - CategoryViewModel: 分类管理
 * - ItemOperationViewModel: 商品CRUD操作
 * - StockRecordViewModel: 库存记录管理
 * - DialogStateManager: 对话框状态管理
 */
class InventoryViewModelRefactored(
    private val inventoryRepository: InventoryRepository,
    private val importCoordinator: ImportCoordinator,
    val searchViewModel: SearchViewModel,
    val categoryViewModel: CategoryViewModel,
    val itemOperationViewModel: ItemOperationViewModel,
    val stockRecordViewModel: StockRecordViewModel,
    val dialogManager: DialogStateManager = DialogStateManager()
) : ViewModel() {
    
    private data class DialogStateBundle(
        val dialogState: com.example.inventory.ui.state.DialogState,
        val selectedItem: InventoryItemEntity?,
        val editText: String,
        val pendingDeleteItem: InventoryItemEntity?,
        val manualAddForm: com.example.inventory.ui.state.ManualAddForm,
        val stockActionState: com.example.inventory.ui.state.StockActionState
    )
    
    private val localState = MutableStateFlow(InventoryUiState())
    private val currentListId = MutableStateFlow<Long?>(null)
    private var currentListJob: Job? = null
    private val dialogActions = DialogActionsUseCase(
        dialogManager = dialogManager,
        localState = localState,
        searchViewModel = searchViewModel,
        categoryViewModel = categoryViewModel,
        itemOperationViewModel = itemOperationViewModel,
        stockRecordViewModel = stockRecordViewModel,
        scope = viewModelScope,
        currentListIdProvider = { currentListId.value },
        setMessage = ::setMessage
    )
    private val itemEditActions = ItemEditActionsUseCase(
        inventoryRepository = inventoryRepository,
        itemOperationViewModel = itemOperationViewModel,
        dialogManager = dialogManager,
        scope = viewModelScope,
        currentListIdProvider = { currentListId.value },
        setMessage = ::setMessage
    )
    private val formActions = FormActionsUseCase(
        itemOperationViewModel = itemOperationViewModel,
        currentListIdProvider = { currentListId.value },
        setMessage = ::setMessage,
        scope = viewModelScope
    )
    private val importItemsUseCase = ImportItemsUseCase(
        importCoordinator = importCoordinator,
        itemOperationViewModel = itemOperationViewModel,
        scope = viewModelScope,
        currentListIdProvider = { currentListId.value },
        setMessage = ::setMessage
    )
    private val dialogBase = combine(
        dialogManager.currentDialog,
        dialogManager.selectedItem,
        dialogManager.editText
    ) { dialogState, selectedItem, editText ->
        Triple(dialogState, selectedItem, editText)
    }
    private val dialogExtras = combine(
        dialogManager.pendingDeleteItem,
        dialogManager.manualAddForm,
        dialogManager.stockActionState
    ) { pendingDeleteItem, manualAddForm, stockActionState ->
        Triple(pendingDeleteItem, manualAddForm, stockActionState)
    }
    private val dialogStateBundle = combine(
        dialogBase,
        dialogExtras
    ) { base, extras ->
        DialogStateBundle(
            dialogState = base.first,
            selectedItem = base.second,
            editText = base.third,
            pendingDeleteItem = extras.first,
            manualAddForm = extras.second,
            stockActionState = extras.third
        )
    }
    val uiState: StateFlow<InventoryUiState> = combine(
        dialogStateBundle,
        searchViewModel.selectedCategoryId,
        categoryViewModel.categories,
        stockRecordViewModel.records,
        localState
    ) { dialogBundle, selectedCategoryId, categories, records, local ->
        local.copy(
            dialogState = dialogBundle.dialogState,
            selectedItem = dialogBundle.selectedItem,
            editText = dialogBundle.editText,
            pendingDeleteItem = dialogBundle.pendingDeleteItem,
            manualAddForm = dialogBundle.manualAddForm,
            stockActionState = dialogBundle.stockActionState,
            selectedCategoryId = selectedCategoryId,
            categories = categories,
            records = records
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = localState.value
    )
    
    // ==================== 分页数据流 ====================
    
    /**
     * 商品分页数据流
     * 支持搜索、分类过滤
     */
    val itemsFlow: Flow<PagingData<InventoryItemEntity>> = 
        searchViewModel.itemsFlow.cachedIn(viewModelScope)

    fun updateCurrentListId(listId: Long?) {
        currentListId.value = listId
    }
    
    fun bindCurrentListId(listIdFlow: Flow<Long?>) {
        if (currentListJob?.isActive == true) return
        currentListJob = listIdFlow
            .onEach { updateCurrentListId(it) }
            .launchIn(viewModelScope)
    }
    
    // ==================== 搜索相关 ====================
    
    fun showSearchDialog() {
        dialogActions.showSearchDialog()
    }
    
    fun updateSearchQuery(query: String) {
        dialogActions.updateSearchQuery(query)
    }
    
    fun applySearch() {
        dialogActions.applySearch()
    }
    
    fun clearSearch() {
        dialogActions.clearSearch()
    }
    
    // ==================== 分类相关 ====================
    
    fun showCategoryDialog() {
        dialogActions.showCategoryDialog()
    }
    
    fun selectCategory(categoryId: Long?) {
        dialogActions.selectCategory(categoryId)
    }
    
    fun addCategory(name: String) {
        dialogActions.addCategory(name)
    }
    
    // ==================== 手动添加商品 ====================
    
    fun showManualAddDialog() {
        dialogActions.showManualAddDialog()
    }
    
    fun updateManualAddForm(update: (com.example.inventory.ui.state.ManualAddForm) -> com.example.inventory.ui.state.ManualAddForm) {
        dialogActions.updateManualAddForm(update)
    }
    
    fun saveManualAdd() {
        dialogActions.saveManualAdd()
    }
    
    // ==================== 编辑商品 ====================
    
    fun showEdit(item: InventoryItemEntity) {
        dialogActions.showEdit(item)
    }
    
    fun updateEditText(text: String) {
        dialogActions.updateEditText(text)
    }
    
    fun saveEdit() {
        dialogActions.saveEdit()
    }
    
    // ==================== 删除商品 ====================
    
    fun requestDeleteItem(item: InventoryItemEntity) {
        dialogActions.requestDeleteItem(item)
    }
    
    fun confirmDeleteItem() {
        dialogActions.confirmDeleteItem()
    }
    
    fun cancelDeleteItem() {
        dialogActions.cancelDeleteItem()
    }
    
    // ==================== 库存操作 ====================
    
    fun showStockActions(item: InventoryItemEntity) {
        dialogActions.showStockActions(item)
    }
    
    fun showRecordInput(action: StockAction) {
        dialogActions.showRecordInput(action)
    }
    
    fun updateRecordQuantity(value: String) {
        dialogActions.updateRecordQuantity(value)
    }
    
    fun updateRecordOperator(value: String) {
        dialogActions.updateRecordOperator(value)
    }
    
    fun updateRecordRemark(value: String) {
        dialogActions.updateRecordRemark(value)
    }
    
    fun submitRecord() {
        dialogActions.submitRecord()
    }
    
    fun loadRecordsForSelected() {
        dialogActions.loadRecordsForSelected()
    }
    
    // ==================== 复制粘贴 ====================
    
    fun copyItem(item: InventoryItemEntity) {
        itemOperationViewModel.copyItem(item)
        setMessage(UiMessage.Info("已复制商品"))
    }
    
    fun pasteItem() {
        if (!itemOperationViewModel.hasCopiedItem()) {
            setMessage(UiMessage.Warning("没有可粘贴的商品"))
            return
        }
        
        viewModelScope.launch {
            val result = itemOperationViewModel.pasteItem()
            if (result.isSuccess) {
                setMessage(UiMessage.Success("已粘贴商品"))
            } else {
                setMessage(UiMessage.Error("粘贴失败: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
    
    // ==================== 导入 ====================
    
    fun toggleImportSheet(show: Boolean) {
        dialogActions.toggleImportSheet(show)
    }
    
    fun importItems(extension: String, stream: InputStream) {
        importItemsUseCase.importItems(extension, stream)
    }
    
    // ==================== 对话框管理 ====================
    
    fun dismissDialog() {
        dialogActions.dismissDialog()
    }
    
    // ==================== 其他 ====================
    
    fun setSelectedItem(item: InventoryItemEntity?) {
        dialogManager.setSelectedItem(item)
    }
    
    fun showItemMenu(item: InventoryItemEntity) {
        dialogManager.setSelectedItem(item)
    }
    
    fun retry() {
        localState.update { it.copy(isLoading = true, error = null) }
    }
    
    fun clearMessage() {
        localState.update { it.copy(message = null) }
    }
    
    private fun setMessage(message: UiMessage) {
        localState.update { it.copy(message = message) }
    }
    
    // ==================== OCR 文本应用 ====================
    
    /**
     * 将 OCR 识别的文本应用到商品
     * 
     * @param itemId 商品 ID
     * @param text OCR 识别的文本
     */
    fun applyOcrTextToItem(itemId: Long, text: String) {
        itemEditActions.applyOcrTextToItem(itemId, text)
    }
    
    /**
     * 确认自动填充的商品信息
     * 
     * @param item 自动填充后的商品实体
     */
    fun confirmAutoFill(item: InventoryItemEntity) {
        itemEditActions.confirmAutoFill(item)
    }

    fun showAutoFillDialogFromOcr(groups: List<OcrGroup>) {
        itemEditActions.showAutoFillDialogFromOcr(groups)
    }
    
    /**
     * 更新商品的指定字段
     * 
     * @param itemId 商品 ID
     * @param field 字段名（name, brand, model, parameters, quantity）
     * @param value 新值
     */
    fun updateItemField(itemId: Long, field: String, value: String) {
        itemEditActions.updateItemField(itemId, field, value)
    }
    
    // ==================== 表单添加 ====================
    
    /**
     * 从表单数据添加商品
     * 
     * @param formData 表单数据
     */
    fun addItemFromForm(formData: com.example.inventory.ui.screens.AddItemFormData) {
        formActions.addItemFromForm(formData)
    }
    
    // ==================== 数据检查 ====================
    
    /**
     * 检查是否有库存数据
     * 
     * @return true 表示有数据，false 表示无数据
     */
    suspend fun checkHasData(): Boolean {
        return formActions.checkHasData(inventoryRepository)
    }
}

private class ImportItemsUseCase(
    private val importCoordinator: ImportCoordinator,
    private val itemOperationViewModel: ItemOperationViewModel,
    private val scope: CoroutineScope,
    private val currentListIdProvider: () -> Long?,
    private val setMessage: (UiMessage) -> Unit
) {
    fun importItems(extension: String, stream: InputStream) {
        val listId = currentListIdProvider()
        if (listId == null) {
            setMessage(UiMessage.Warning("未选择列表"))
            return
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    stream.use { inputStream ->
                        val items = importCoordinator.importByExtension(extension, inputStream)
                        ensureActive()
                        if (items.isEmpty()) {
                            return@withContext Result.failure<List<InventoryItemEntity>>(
                                IllegalArgumentException("未找到可导入的数据")
                            )
                        }
                        val itemsWithListId = items.map { it.copy(listId = listId) }
                        val batchResult = itemOperationViewModel.batchAddItems(itemsWithListId)
                        if (batchResult.isSuccess) {
                            Result.success(items)
                        } else {
                            Result.failure(IllegalStateException("导入失败"))
                        }
                    }
                }
            }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { items ->
                    setMessage(UiMessage.Success("导入成功，共${items.size}条"))
                },
                onFailure = { e ->
                    setMessage(UiMessage.Error(e.message ?: "导入失败"))
                }
            )
        }
    }
}

private class DialogActionsUseCase(
    private val dialogManager: DialogStateManager,
    private val localState: MutableStateFlow<InventoryUiState>,
    private val searchViewModel: SearchViewModel,
    private val categoryViewModel: CategoryViewModel,
    private val itemOperationViewModel: ItemOperationViewModel,
    private val stockRecordViewModel: StockRecordViewModel,
    private val scope: CoroutineScope,
    private val currentListIdProvider: () -> Long?,
    private val setMessage: (UiMessage) -> Unit
) {
    fun showSearchDialog() {
        dialogManager.showSearchDialog()
    }
    
    fun updateSearchQuery(query: String) {
        localState.update { it.copy(searchQuery = query) }
    }
    
    fun applySearch() {
        val query = localState.value.searchQuery.trim()
        searchViewModel.updateSearchQuery(query)
        dialogManager.dismissDialog()
    }
    
    fun clearSearch() {
        searchViewModel.clearSearch()
        localState.update { it.copy(searchQuery = "") }
        dialogManager.dismissDialog()
    }
    
    fun showCategoryDialog() {
        dialogManager.showCategoryDialog()
    }
    
    fun selectCategory(categoryId: Long?) {
        searchViewModel.selectCategory(categoryId)
        dialogManager.dismissDialog()
    }
    
    fun addCategory(name: String) {
        scope.launch {
            val success = categoryViewModel.addCategory(name)
            if (success) {
                setMessage(UiMessage.Success("分类已添加"))
            } else {
                setMessage(UiMessage.Warning("分类名称不能为空"))
            }
        }
    }
    
    fun showManualAddDialog() {
        dialogManager.showManualAddDialog()
    }
    
    fun updateManualAddForm(update: (com.example.inventory.ui.state.ManualAddForm) -> com.example.inventory.ui.state.ManualAddForm) {
        dialogManager.updateManualAddForm(update)
    }
    
    fun saveManualAdd() {
        val form = dialogManager.manualAddForm.value
        val name = form.name.trim()
        val barcode = form.barcode.trim()
        if (name.isBlank() && barcode.isBlank()) {
            setMessage(UiMessage.Warning("名称或条码至少填写一项"))
            return
        }
        val listId = currentListIdProvider()
        if (listId == null) {
            setMessage(UiMessage.Warning("未选择列表"))
            return
        }
        val quantity = form.quantity.toIntOrNull() ?: 0
        val item = InventoryItemEntity(
            listId = listId,
            name = name,
            brand = form.brand.trim(),
            model = form.model.trim(),
            parameters = form.parameters.trim(),
            barcode = barcode,
            quantity = quantity.coerceAtLeast(0),
            remark = form.remark.trim()
        )
        scope.launch {
            val result = itemOperationViewModel.addItem(item)
            if (result.isSuccess) {
                dialogManager.resetManualAddForm()
                dialogManager.dismissDialog()
                setMessage(UiMessage.Success("已添加商品"))
            } else {
                setMessage(UiMessage.Error("添加失败: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
    
    fun showEdit(item: InventoryItemEntity) {
        dialogManager.showEditDialog(item)
    }
    
    fun updateEditText(text: String) {
        dialogManager.updateEditText(text)
    }
    
    fun saveEdit() {
        val current = dialogManager.selectedItem.value ?: return
        val newName = dialogManager.editText.value.trim()
        if (newName.isBlank()) {
            setMessage(UiMessage.Warning("商品名称不能为空"))
            return
        }
        scope.launch {
            val updated = current.copy(name = newName)
            val result = itemOperationViewModel.updateItem(updated)
            if (result.isSuccess) {
                dialogManager.setSelectedItem(updated)
                dialogManager.dismissDialog()
                setMessage(UiMessage.Success("已更新商品"))
            } else {
                setMessage(UiMessage.Error("更新失败: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
    
    fun requestDeleteItem(item: InventoryItemEntity) {
        dialogManager.showDeleteConfirmDialog(item)
    }
    
    fun confirmDeleteItem() {
        val item = dialogManager.pendingDeleteItem.value ?: return
        scope.launch {
            val result = itemOperationViewModel.deleteItem(item.id)
            if (result.isSuccess) {
                dialogManager.clearPendingDelete()
                dialogManager.dismissDialog()
                setMessage(UiMessage.Success("已删除商品"))
            } else {
                setMessage(UiMessage.Error("删除失败: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
    
    fun cancelDeleteItem() {
        dialogManager.clearPendingDelete()
        dialogManager.dismissDialog()
    }
    
    fun showStockActions(item: InventoryItemEntity) {
        dialogManager.showStockActionDialog(item)
    }
    
    fun showRecordInput(action: StockAction) {
        dialogManager.showRecordInputDialog(action)
        if (action == StockAction.Count) {
            loadRecordsForSelected()
        }
    }
    
    fun updateRecordQuantity(value: String) {
        dialogManager.updateStockActionQuantity(value)
    }
    
    fun updateRecordOperator(value: String) {
        dialogManager.updateStockActionOperator(value)
    }
    
    fun updateRecordRemark(value: String) {
        dialogManager.updateStockActionRemark(value)
    }
    
    fun submitRecord() {
        val item = dialogManager.selectedItem.value ?: return
        val state = dialogManager.stockActionState.value
        val quantity = state.quantity.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            setMessage(UiMessage.Warning("请输入有效的数量"))
            return
        }
        scope.launch {
            val result = stockRecordViewModel.addRecord(
                item = item,
                action = state.action,
                quantity = quantity,
                operatorName = state.operator,
                remark = state.remark
            )
            if (result.isSuccess) {
                val updatedItem = result.getOrNull()
                if (updatedItem != null) {
                    dialogManager.setSelectedItem(updatedItem)
                    dialogManager.resetStockActionState()
                    dialogManager.showRecordDialog()
                    setMessage(UiMessage.Success("操作成功"))
                } else {
                    setMessage(UiMessage.Warning("操作失败"))
                }
            } else {
                val error = result.exceptionOrNull()
                setMessage(UiMessage.Warning(error?.message ?: "操作失败"))
            }
        }
    }
    
    fun loadRecordsForSelected() {
        val item = dialogManager.selectedItem.value ?: return
        stockRecordViewModel.loadRecords(item.id)
        dialogManager.showRecordDialog()
    }
    
    fun toggleImportSheet(show: Boolean) {
        if (show) {
            dialogManager.showImportSheet()
        } else {
            dialogManager.dismissDialog()
        }
    }
    
    fun dismissDialog() {
        dialogManager.dismissDialog()
    }
}

private class ItemEditActionsUseCase(
    private val inventoryRepository: InventoryRepository,
    private val itemOperationViewModel: ItemOperationViewModel,
    private val dialogManager: DialogStateManager,
    private val scope: CoroutineScope,
    private val currentListIdProvider: () -> Long?,
    private val setMessage: (UiMessage) -> Unit
) {
    fun applyOcrTextToItem(itemId: Long, text: String) {
        if (itemId <= 0L || text.isBlank()) return
        scope.launch {
            val item = inventoryRepository.getItemsByIds(listOf(itemId)).firstOrNull()
            if (item != null) {
                dialogManager.showEditDialog(item)
                dialogManager.updateEditText(text)
            } else {
                setMessage(UiMessage.Warning("未找到对应商品"))
            }
        }
    }
    
    fun confirmAutoFill(item: InventoryItemEntity) {
        scope.launch {
            val result = itemOperationViewModel.addItem(item)
            if (result.isSuccess) {
                setMessage(UiMessage.Success("已添加商品：${item.name}"))
                dialogManager.dismissDialog()
            } else {
                setMessage(UiMessage.Error("添加失败: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
    
    fun showAutoFillDialogFromOcr(groups: List<OcrGroup>) {
        val listId = currentListIdProvider()
        if (listId == null) {
            setMessage(UiMessage.Warning("未选择列表"))
            return
        }
        val texts = groups.map { group ->
            group.tokens.joinToString("") { it.text }.trim()
        }.filter { it.isNotBlank() }
        if (texts.isEmpty()) {
            setMessage(UiMessage.Warning("未识别到可用文本"))
            return
        }
        val name = texts.first()
        val quantity = texts.firstOrNull { text -> text.all { it.isDigit() } }?.toIntOrNull() ?: 0
        val item = InventoryItemEntity(
            listId = listId,
            name = name,
            brand = texts.getOrNull(1).orEmpty(),
            model = texts.getOrNull(2).orEmpty(),
            parameters = texts.getOrNull(3).orEmpty(),
            barcode = "",
            quantity = quantity,
            unit = "个",
            location = "",
            remark = ""
        )
        dialogManager.setSelectedItem(item)
        dialogManager.showAutoFillDialog()
    }
    
    fun updateItemField(itemId: Long, field: String, value: String) {
        if (itemId <= 0L) return
        scope.launch {
            val item = inventoryRepository.getItemsByIds(listOf(itemId)).firstOrNull() ?: return@launch
            val newItem = when (field) {
                "name" -> item.copy(name = value)
                "brand" -> item.copy(brand = value)
                "model" -> item.copy(model = value)
                "parameters" -> item.copy(parameters = value)
                "quantity" -> {
                    val qty = value.toIntOrNull() ?: item.quantity
                    item.copy(quantity = qty)
                }
                else -> item
            }
            if (newItem != item) {
                val result = itemOperationViewModel.updateItem(newItem)
                if (result.isSuccess) {
                    dialogManager.setSelectedItem(newItem)
                }
            }
        }
    }
}

private class FormActionsUseCase(
    private val itemOperationViewModel: ItemOperationViewModel,
    private val currentListIdProvider: () -> Long?,
    private val setMessage: (UiMessage) -> Unit,
    private val scope: CoroutineScope
) {
    fun addItemFromForm(formData: com.example.inventory.ui.screens.AddItemFormData) {
        scope.launch {
            val listId = currentListIdProvider()
            if (listId == null) {
                setMessage(UiMessage.Warning("未选择列表"))
                return@launch
            }
            runCatching {
                InventoryItemEntity(
                    listId = listId,
                    name = formData.name.trim(),
                    brand = formData.brand.trim(),
                    model = formData.model.trim(),
                    parameters = formData.category.trim(),
                    barcode = "",
                    quantity = formData.quantity.coerceAtLeast(0),
                    unit = formData.unit,
                    location = formData.location.trim(),
                    remark = formData.remark.trim()
                )
            }.onFailure { e ->
                setMessage(UiMessage.Error("添加商品失败：${e.message}"))
            }.onSuccess { item ->
                val result = itemOperationViewModel.addItem(item)
                if (result.isSuccess) {
                    setMessage(UiMessage.Success("已添加商品：${formData.name}"))
                } else {
                    setMessage(UiMessage.Error("添加商品失败"))
                }
            }
        }
    }
    
    suspend fun checkHasData(inventoryRepository: InventoryRepository): Boolean {
        return try {
            val items = inventoryRepository.getAllItemsSnapshot()
            items.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
