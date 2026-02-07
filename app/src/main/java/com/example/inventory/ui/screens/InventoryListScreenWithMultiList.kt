package com.example.inventory.ui.screens

import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.inventory.data.model.InventoryListEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.screens.inventory.components.*
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.state.FileType
import com.example.inventory.ui.state.StockAction
import com.example.inventory.ui.state.UiMessage
import com.example.inventory.ui.viewmodel.ImportViewModel
import com.example.inventory.ui.viewmodel.InventoryListViewModel
import com.example.inventory.ui.viewmodel.InventoryViewModelRefactored
import com.example.inventory.ui.viewmodel.InventoryViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 库存列表屏幕（多列表支持版本）
 * 
 * 支持多个独立的库存列表管理
 * 
 * @param inventoryViewModel 库存视图模型
 * @param listViewModel 列表视图模型
 * @param importViewModel 导入视图模型
 * @param inventoryRepository 库存仓库（用于导入和获取商品数量）
 * @param importCoordinator 导入协调器（用于导入）
 * @param showImport 是否显示导入对话框
 * @param onNavigateCapture 导航到拍照页面
 * @param onNavigateAddItem 导航到添加商品页面
 * @param onNavigateImagePicker 导航到图片选择器页面
 * @param onNavigateSettings 导航到设置页面
 * @param onRowBoundsUpdated 行边界更新回调（用于拖拽功能）
 * @param modifier 修饰符
 */

@Composable
fun InventoryListScreenWithMultiList(
    inventoryViewModel: InventoryViewModelRefactored,
    listViewModel: InventoryListViewModel,
    importViewModel: ImportViewModel,
    inventoryRepository: InventoryRepository,
    showImport: Boolean = false,
    onNavigateCapture: () -> Unit,
    onNavigateAddItem: () -> Unit,
    onNavigateImagePicker: () -> Unit,
    onNavigateSettings: () -> Unit,
    onRowBoundsUpdated: ((Long, Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onCellBoundsUpdated: ((Long, String, Rect) -> Unit)? = null,
    highlightedCell: Pair<Long, String>? = null,
    tableState: LazyListState = rememberLazyListState(),
    horizontalScrollState: ScrollState = rememberScrollState()
) {
    val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
    val lists by listViewModel.lists.collectAsStateWithLifecycle()
    val currentListId by listViewModel.currentListId.collectAsStateWithLifecycle()
    val currentList by listViewModel.currentList.collectAsStateWithLifecycle()
    val importProgress by importViewModel.progress.collectAsStateWithLifecycle()
    val viewModeState by listViewModel.viewMode.collectAsStateWithLifecycle()
    
    val viewMode = if (isEditMode) InventoryViewMode.TABLE else viewModeState
    
    val pagingItems = inventoryViewModel.itemsFlow.collectAsLazyPagingItems()
    val uiState = rememberInventoryListScreenUiState()
    val context = LocalContext.current
    
    HandleImportDeepLink(
        showImport = showImport,
        onShowCreateListSheet = { uiState.showCreateListSheet = it }
    )
    HandleUiMessages(
        message = inventoryState.message,
        onClearMessage = inventoryViewModel::clearMessage,
        showMessage = uiState.showMessage
    )
    HandleImportProgress(
        isImporting = importProgress.isImporting,
        onShowImportProgress = { uiState.showImportProgress = it }
    )
    LaunchedEffect(listViewModel) {
        inventoryViewModel.bindCurrentListId(listViewModel.currentListId)
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            InventoryListTopBar(
                title = currentList?.name ?: "库存列表",
                viewMode = viewMode,
                isEditMode = isEditMode,
                lists = lists,
                currentListId = currentListId,
                onSearchClick = inventoryViewModel::showSearchDialog,
                onCategoryClick = inventoryViewModel::showCategoryDialog,
                onSettingsClick = onNavigateSettings,
                onToggleViewMode = {
                    if (!isEditMode) {
                        listViewModel.toggleViewMode()
                    }
                },
                onListSelect = listViewModel::switchToList,
                onCreateList = { uiState.showCreateListSheet = true },
                onRenameList = { listId, newName ->
                    uiState.scope.launch {
                        listViewModel.renameList(listId, newName)
                            .onSuccess {
                                uiState.showMessage("重命名成功")
                            }
                            .onFailure { error ->
                                uiState.showMessage(error.message ?: "重命名失败")
                            }
                    }
                },
                onDeleteList = { listId ->
                    uiState.deleteListId = listId
                    uiState.showDeleteDialog = true
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = uiState.snackbarHostState)
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { uiState.showCreateListSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "导入或新建"
                )
            }
        }
    ) { paddingValues ->
        InventoryListBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            pagingItems = pagingItems,
            inventoryState = inventoryState,
            viewMode = viewMode,
            isEditMode = isEditMode,
            onNavigateAddItem = onNavigateAddItem,
            onNavigateCapture = onNavigateCapture,
            onNavigateImagePicker = onNavigateImagePicker,
            onRowBoundsUpdated = onRowBoundsUpdated,
            onItemClick = inventoryViewModel::showStockActions,
            onItemLongPress = inventoryViewModel::showStockActions,
            onItemDelete = inventoryViewModel::requestDeleteItem,
            onCellBoundsUpdated = onCellBoundsUpdated,
            highlightedCell = highlightedCell,
            tableState = tableState,
            horizontalScrollState = horizontalScrollState
        )
    }
    
    InventoryListDialogHost(
        uiState = uiState,
        lists = lists,
        inventoryState = inventoryState,
        listViewModel = listViewModel,
        importViewModel = importViewModel,
        inventoryViewModel = inventoryViewModel,
        inventoryRepository = inventoryRepository,
        context = context,
        importProgress = importProgress
    )
}

@Composable
private fun InventoryListTopBar(
    title: String,
    viewMode: InventoryViewMode,
    isEditMode: Boolean,
    lists: List<InventoryListEntity>,
    currentListId: Long?,
    onSearchClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onListSelect: (Long) -> Unit,
    onCreateList: () -> Unit,
    onRenameList: (Long, String) -> Unit,
    onDeleteList: (Long) -> Unit
) {
    InventoryTopBar(
        title = title,
        onSearchClick = onSearchClick,
        onCategoryClick = onCategoryClick,
        onSettingsClick = onSettingsClick,
        viewMode = viewMode,
        onToggleViewMode = onToggleViewMode,
        isToggleEnabled = !isEditMode,
        titleContent = {
            if (lists.isNotEmpty()) {
                InventoryListDropdown(
                    lists = lists,
                    currentListId = currentListId,
                    onListSelect = onListSelect,
                    onCreateList = onCreateList,
                    onRenameList = onRenameList,
                    onDeleteList = onDeleteList
                )
            }
        }
    )
}

@Composable
private fun InventoryListBody(
    modifier: Modifier,
    pagingItems: androidx.paging.compose.LazyPagingItems<com.example.inventory.data.model.InventoryItemEntity>,
    inventoryState: com.example.inventory.ui.state.InventoryUiState,
    viewMode: InventoryViewMode,
    isEditMode: Boolean,
    onNavigateAddItem: () -> Unit,
    onNavigateCapture: () -> Unit,
    onNavigateImagePicker: () -> Unit,
    onRowBoundsUpdated: ((Long, Rect) -> Unit)?,
    onItemClick: (com.example.inventory.data.model.InventoryItemEntity) -> Unit,
    onItemLongPress: (com.example.inventory.data.model.InventoryItemEntity) -> Unit,
    onItemDelete: (com.example.inventory.data.model.InventoryItemEntity) -> Unit,
    onCellBoundsUpdated: ((Long, String, Rect) -> Unit)?,
    highlightedCell: Pair<Long, String>?,
    tableState: LazyListState,
    horizontalScrollState: ScrollState
) {
    BoxWithConstraints(modifier = modifier) {
        val refreshState = pagingItems.loadState.refresh
        when {
            refreshState is LoadState.Loading -> {
                LoadingScreen(message = "加载商品列表...")
            }

            refreshState is LoadState.Error -> {
                val error = (refreshState as LoadState.Error).error
                ErrorScreen(
                    error = error.message ?: "未知错误",
                    onRetry = { pagingItems.retry() }
                )
            }

            pagingItems.itemCount == 0 && refreshState is LoadState.NotLoading -> {
                EmptyInventoryScreen(
                    onAddClick = onNavigateAddItem
                )
            }

            else -> {
                when (viewMode) {
                    InventoryViewMode.CARD -> {
                        InventoryListContent(
                            items = pagingItems,
                            selectedItemId = inventoryState.selectedItem?.id,
                            onItemClick = onItemClick,
                            onItemLongPress = { item, _ ->
                                onItemLongPress(item)
                            },
                            onItemDelete = onItemDelete,
                            onRowBoundsUpdated = onRowBoundsUpdated,
                            bottomContent = {
                                AddItemButton(
                                    onManualAdd = onNavigateAddItem,
                                    onCameraAdd = onNavigateCapture,
                                    onImageImport = onNavigateImagePicker
                                )
                            }
                        )
                    }
                    InventoryViewMode.TABLE -> {
                        InventoryTableContent(
                            items = pagingItems,
                            onItemClick = onItemClick,
                            onItemLongPress = onItemLongPress,
                            onItemDelete = onItemDelete,
                            isEditMode = isEditMode,
                            onCellBoundsUpdated = onCellBoundsUpdated,
                            highlightedCell = highlightedCell,
                            state = tableState,
                            horizontalScrollState = horizontalScrollState,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryListDialogHost(
    uiState: InventoryListScreenUiState,
    lists: List<InventoryListEntity>,
    inventoryState: com.example.inventory.ui.state.InventoryUiState,
    listViewModel: InventoryListViewModel,
    importViewModel: ImportViewModel,
    inventoryViewModel: InventoryViewModelRefactored,
    inventoryRepository: InventoryRepository,
    context: android.content.Context,
    importProgress: com.example.inventory.ui.state.ImportProgress
) {
    val startImport: (Uri, FileType) -> Unit = { uri, fileType ->
        uiState.scope.launch {
            val listId = listViewModel.createList()
            listViewModel.switchToList(listId)
            importViewModel.startImportFromUri(
                listId = listId,
                uri = uri,
                fileType = fileType,
                contentResolver = context.contentResolver
            )
        }
    }

    CreateListBottomSheet(
        visible = uiState.showCreateListSheet,
        onDismiss = { uiState.showCreateListSheet = false },
        onManualCreate = {
            uiState.scope.launch {
                val listId = listViewModel.createList()
                listViewModel.switchToList(listId)
                uiState.showMessage("已创建新列表")
            }
        },
        onImportExcel = { uri -> startImport(uri, FileType.EXCEL) },
        onImportAccess = { uri -> startImport(uri, FileType.ACCESS) },
        onImportDatabase = { uri -> startImport(uri, FileType.DATABASE) }
    )

    uiState.deleteListId?.let { listId ->
        val list = lists.find { it.id == listId }
        var itemCount by remember { mutableStateOf(0) }

        LaunchedEffect(listId) {
            itemCount = inventoryRepository.getItemCountByListId(listId)
        }

        DeleteListConfirmDialog(
            visible = uiState.showDeleteDialog,
            listName = list?.name ?: "",
            itemCount = itemCount,
            onConfirm = {
                uiState.scope.launch {
                    listViewModel.deleteList(listId)
                        .onSuccess {
                            uiState.showMessage("删除成功")
                            uiState.showDeleteDialog = false
                            uiState.deleteListId = null
                        }
                        .onFailure { error ->
                            uiState.showMessage(error.message ?: "删除失败")
                        }
                }
            },
            onDismiss = {
                uiState.showDeleteDialog = false
                uiState.deleteListId = null
            }
        )
    }

    ImportProgressDialog(
        visible = uiState.showImportProgress,
        progress = importProgress,
        onCancel = {
            importViewModel.cancelImport()
            uiState.showImportProgress = false
        }
    )

    InventoryActionDialogs(
        inventoryState = inventoryState,
        inventoryViewModel = inventoryViewModel
    )
}

@Composable
private fun InventoryActionDialogs(
    inventoryState: com.example.inventory.ui.state.InventoryUiState,
    inventoryViewModel: InventoryViewModelRefactored
) {
    EditItemDialog(
        visible = inventoryState.dialogState == DialogState.EditDialog,
        item = inventoryState.selectedItem,
        editText = inventoryState.editText,
        onTextChange = inventoryViewModel::updateEditText,
        onSave = { inventoryViewModel.saveEdit() },
        onDismiss = inventoryViewModel::dismissDialog
    )

    StockActionDialog(
        visible = inventoryState.dialogState == DialogState.StockActionDialog,
        item = inventoryState.selectedItem,
        onEdit = { inventoryState.selectedItem?.let { inventoryViewModel.showEdit(it) } },
        onInbound = { inventoryViewModel.showRecordInput(StockAction.Inbound) },
        onOutbound = { inventoryViewModel.showRecordInput(StockAction.Outbound) },
        onCount = { inventoryViewModel.showRecordInput(StockAction.Count) },
        onDismiss = inventoryViewModel::dismissDialog
    )

    RecordInputDialog(
        visible = inventoryState.dialogState == DialogState.RecordInputDialog,
        action = inventoryState.stockActionState.action,
        quantity = inventoryState.stockActionState.quantity,
        operator = inventoryState.stockActionState.operator,
        remark = inventoryState.stockActionState.remark,
        onQuantityChange = inventoryViewModel::updateRecordQuantity,
        onOperatorChange = inventoryViewModel::updateRecordOperator,
        onRemarkChange = inventoryViewModel::updateRecordRemark,
        onSave = { inventoryViewModel.submitRecord() },
        onDismiss = inventoryViewModel::dismissDialog
    )

    RecordDetailDialog(viewModel = inventoryViewModel)

    SearchDialog(
        visible = inventoryState.dialogState == DialogState.SearchDialog,
        query = inventoryState.searchQuery,
        onQueryChange = inventoryViewModel::updateSearchQuery,
        onSearch = inventoryViewModel::applySearch,
        onClear = inventoryViewModel::clearSearch,
        onDismiss = inventoryViewModel::dismissDialog
    )

    CategoryDialog(
        visible = inventoryState.dialogState == DialogState.CategoryDialog,
        categories = inventoryState.categories,
        selectedCategoryId = inventoryState.selectedCategoryId,
        onSelect = inventoryViewModel::selectCategory,
        onAddCategory = inventoryViewModel::addCategory,
        onDismiss = inventoryViewModel::dismissDialog
    )

    ManualAddDialog(
        visible = inventoryState.dialogState == DialogState.ManualAddDialog,
        form = inventoryState.manualAddForm,
        onUpdate = inventoryViewModel::updateManualAddForm,
        onSave = { inventoryViewModel.saveManualAdd() },
        onDismiss = inventoryViewModel::dismissDialog
    )

    ConfirmDeleteDialog(
        visible = inventoryState.dialogState == DialogState.DeleteConfirmDialog,
        item = inventoryState.pendingDeleteItem,
        onConfirm = inventoryViewModel::confirmDeleteItem,
        onDismiss = inventoryViewModel::cancelDeleteItem
    )

    inventoryState.selectedItem?.let { item ->
        if (inventoryState.dialogState == DialogState.AutoFillDialog) {
            AutoFillDialog(
                visible = true,
                initialData = item,
                onConfirm = inventoryViewModel::confirmAutoFill,
                onDismiss = inventoryViewModel::dismissDialog
            )
        }
    }
}

@Composable
private fun HandleImportDeepLink(
    showImport: Boolean,
    onShowCreateListSheet: (Boolean) -> Unit
) {
    val onShowCreateListSheetState by rememberUpdatedState(onShowCreateListSheet)
    LaunchedEffect(showImport) {
        if (showImport) {
            onShowCreateListSheetState(true)
        }
    }
}

@Composable
private fun HandleUiMessages(
    message: UiMessage?,
    onClearMessage: () -> Unit,
    showMessage: (String) -> Unit
) {
    val showMessageState by rememberUpdatedState(showMessage)
    val onClearMessageState by rememberUpdatedState(onClearMessage)
    LaunchedEffect(message) {
        message?.let { uiMessage ->
            val text = when (uiMessage) {
                is UiMessage.Success -> uiMessage.text
                is UiMessage.Error -> uiMessage.text
                is UiMessage.Info -> uiMessage.text
                is UiMessage.Warning -> uiMessage.text
            }
            showMessageState(text)
            onClearMessageState()
        }
    }
}

@Composable
private fun HandleImportProgress(
    isImporting: Boolean,
    onShowImportProgress: (Boolean) -> Unit
) {
    val onShowImportProgressState by rememberUpdatedState(onShowImportProgress)
    LaunchedEffect(isImporting) {
        onShowImportProgressState(isImporting)
    }
}

private class InventoryListScreenUiState(
    val snackbarHostState: SnackbarHostState,
    val scope: kotlinx.coroutines.CoroutineScope
) {
    var showCreateListSheet by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    var showImportProgress by mutableStateOf(false)
    var deleteListId by mutableStateOf<Long?>(null)
    private var messageJob: Job? = null

    val showMessage: (String) -> Unit = { message ->
        messageJob?.cancel()
        messageJob = scope.launch { snackbarHostState.showSnackbar(message) }
    }
}

@Composable
private fun rememberInventoryListScreenUiState(): InventoryListScreenUiState {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(snackbarHostState, scope) {
        InventoryListScreenUiState(snackbarHostState, scope)
    }
}

/**
 * 处理文件导入
 * 
 * @param uri 文件URI
 * @param fileType 文件类型
 * @param listViewModel 列表视图模型
 * @param importViewModel 导入视图模型
 * @param inventoryRepository 库存仓库
 * @param importCoordinator 导入协调器
 * @param context 上下文
 * @param scope 协程作用域
 * @param showMessage 消息显示回调
 */
// ==================== Preview 预览 ====================

/**
 * 预览说明：
 * InventoryListScreenWithMultiList 依赖复杂的 ViewModel，
 * 完整预览需要模拟整个 ViewModel 状态。
 * 建议在实际开发中使用组件级别的预览（如 InventoryItemCard）。
 * 
 * 如需预览完整屏幕，请在设备或模拟器上运行应用。
 */

/**
 * 预览：库存列表屏幕（简化版）
 * 注意：由于依赖 ViewModel，此预览仅作为布局参考
 */
@Preview(
    name = "库存列表屏幕",
    showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
private fun InventoryListScreenPreview() {
    // 注意：此预览无法完全展示功能，因为需要 ViewModel
    // 建议使用组件级别的预览或在设备上测试
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "库存列表屏幕\n（需要在设备上查看完整功能）",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
