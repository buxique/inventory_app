package com.example.inventory.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.inventory.R
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.components.CustomImagePicker
import com.example.inventory.ui.state.ProcessingState
import com.example.inventory.ui.state.ViewState
import com.example.inventory.ui.viewmodel.CaptureViewModel
import com.example.inventory.ui.viewmodel.ImportViewModel
import com.example.inventory.ui.viewmodel.InventoryListViewModel
import com.example.inventory.ui.viewmodel.InventoryViewModelRefactored
import com.example.inventory.util.AppLogger
import com.example.inventory.util.Constants
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    inventoryViewModel: InventoryViewModelRefactored,
    listViewModel: InventoryListViewModel,
    importViewModel: ImportViewModel,
    inventoryRepository: InventoryRepository,
    importCoordinator: ImportCoordinator,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // 相机与权限
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { ContextCompat.getMainExecutor(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.setCameraActive(true)
        }
    }
    
    var showImagePicker by remember { mutableStateOf(false) }
    var showNextImageDialog by remember { mutableStateOf(false) }
    val lastOcrUri = remember { mutableStateOf<Uri?>(null) }
    val lastPromptedUri = remember { mutableStateOf<Uri?>(null) }
    
    val permissionLauncherStorage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImagePicker = true
        }
    }

    val dropTargets = remember { mutableStateMapOf<Long, Rect>() }
    val cellDropTargets = remember { mutableStateMapOf<Long, MutableMap<String, Rect>>() }
    val draggingGroup = remember { mutableStateOf<OcrGroup?>(null) }
    val dragOffset = remember { mutableStateOf(Offset.Zero) }
    val chipOrigins = remember { mutableStateMapOf<String, Offset>() }
    val imageContainerOrigin = remember { mutableStateOf(Offset.Zero) }

    // 清理机制：当图片 URI 改变时，清理拖拽相关的状态
    LaunchedEffect(state.imageUri) {
        // 清理拖拽目标位置缓存
        dropTargets.clear()
        cellDropTargets.clear()
        chipOrigins.clear()
        
        // 重置拖拽状态
        draggingGroup.value = null
        dragOffset.value = Offset.Zero
        imageContainerOrigin.value = Offset.Zero
    }

    // 编辑模式状态
    var isEditMode by remember { mutableStateOf(false) }
    
    // 拖拽目标高亮单元格
    var highlightedCell by remember { mutableStateOf<Pair<Long, String>?>(null) }
    
    // 当前属性选择（品名、品牌、型号、规格、数量）
    val attributes = remember { listOf(
        "name" to "品名",
        "brand" to "品牌",
        "model" to "型号",
        "parameters" to "规格",
        "quantity" to "数量"
    ) }
    var currentAttributeIndex by remember { mutableIntStateOf(0) }
    val activeAttribute = attributes[currentAttributeIndex].first
    
    // 用于控制可见性的拖拽状态
    var isDragging by remember { mutableStateOf(false) }

    val tableState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
    val scrollThreshold = Constants.UI.SCROLL_THRESHOLD // 距离底部边缘触发滚动的像素值

    if (showImagePicker) {
        CustomImagePicker(
            onDismiss = { showImagePicker = false },
            onImagesSelected = { uris ->
                viewModel.addImagesToQueue(uris)
                showImagePicker = false
            }
        )
    }
    
    if (showNextImageDialog) {
        AlertDialog(
            onDismissRequest = { showNextImageDialog = false },
            title = { Text(stringResource(R.string.continue_processing_title)) },
            text = { Text(stringResource(R.string.continue_processing_message, state.imageQueue.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.processNextImage()
                    showNextImageDialog = false
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNextImageDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    LaunchedEffect(state.imageUri, state.processingState) {
        val currentUri = state.imageUri
        if (currentUri != null &&
            currentUri != lastOcrUri.value &&
            state.processingState is ProcessingState.Idle
        ) {
            val file = uriToFile(context, currentUri)
            if (file != null) {
                lastOcrUri.value = currentUri
                viewModel.runOcr(file)
            }
        }
    }

    LaunchedEffect(state.processingState, state.imageQueue.size, state.imageUri) {
        if (state.processingState is ProcessingState.Success &&
            state.imageQueue.isNotEmpty() &&
            state.imageUri != null &&
            state.imageUri != lastPromptedUri.value
        ) {
            showNextImageDialog = true
            lastPromptedUri.value = state.imageUri
        }
    }

    val onImageCaptured: (Uri) -> Unit = { uri ->
        viewModel.setImageUri(uri)
        lastOcrUri.value = uri
        val file = uriToFile(context, uri)
        if (file != null) {
            viewModel.runOcr(file)
        }
    }

    val onDragStart: (OcrGroup, Offset) -> Unit = { group, offset ->
        isDragging = true
        draggingGroup.value = group
        val origin = chipOrigins[group.id] ?: Offset.Zero
        dragOffset.value = origin + offset
        if (state.viewState == ViewState.Normal && !isEditMode) {
            viewModel.toggleImageMinimize()
        }
    }

    val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, dragAmount ->
        dragOffset.value += dragAmount
        change.consume()

        val dropPoint = imageContainerOrigin.value + dragOffset.value

        if (isEditMode) {
            var found = false
            for ((itemId, fields) in cellDropTargets) {
                for ((field, rect) in fields) {
                    if (rect.contains(dropPoint)) {
                        highlightedCell = itemId to field
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (!found) highlightedCell = null
        }

        if (dropPoint.y > screenHeight - scrollThreshold) {
            scope.launch {
                tableState.scrollBy(Constants.UI.VERTICAL_SCROLL_SPEED)
            }
        } else if (dropPoint.y < (screenHeight / 2) + 50 && dropPoint.y > (screenHeight / 2) - 50) {
            scope.launch {
                tableState.scrollBy(-Constants.UI.VERTICAL_SCROLL_SPEED)
            }
        }

        if (dropPoint.x < scrollThreshold / 2) {
            scope.launch {
                horizontalScrollState.scrollBy(-Constants.UI.HORIZONTAL_SCROLL_SPEED)
            }
        } else if (dropPoint.x > screenWidth - (scrollThreshold / 2)) {
            scope.launch {
                horizontalScrollState.scrollBy(Constants.UI.HORIZONTAL_SCROLL_SPEED)
            }
        }
    }

    val onDragEnd: () -> Unit = {
        isDragging = false
        val dropPoint = imageContainerOrigin.value + dragOffset.value

        if (isEditMode) {
            var handled = false
            for ((itemId, fields) in cellDropTargets) {
                for ((field, rect) in fields) {
                    if (rect.contains(dropPoint)) {
                        val group = draggingGroup.value
                        if (group != null) {
                            val text = group.tokens.joinToString("") { it.text }
                            inventoryViewModel.updateItemField(itemId, field, text)
                            handled = true
                        }
                        break
                    }
                }
                if (handled) break
            }
        } else {
            val targetId = dropTargets.entries.firstOrNull { it.value.contains(dropPoint) }?.key
            if (targetId != null) {
                val group = draggingGroup.value
                if (group != null) {
                    val text = group.tokens.joinToString("") { it.text }
                    inventoryViewModel.applyOcrTextToItem(targetId, text)
                }
            }
        }
        draggingGroup.value = null
    }

    val onImportClick = {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncherStorage.launch(permission)
    }

    val onTakePhoto = {
        takePhoto(
            context = context,
            imageCapture = imageCapture,
            executor = cameraExecutor,
            onImageSaved = onImageCaptured,
            onError = { AppLogger.e("Error: $it", "Capture") }
        )
    }

    val onEnableCamera = {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val imageWeight = if (isEditMode) {
            if (isDragging) 0.5f else 1f
        } else {
            if (state.viewState == ViewState.ImageMinimized) 0.5f else 1f
        }

        CaptureImageArea(
            modifier = Modifier
                .weight(imageWeight)
                .fillMaxWidth()
                .background(Color.Black),
            state = state,
            hasCameraPermission = hasCameraPermission,
            imageCapture = imageCapture,
            cameraExecutor = cameraExecutor,
            onImageCaptured = onImageCaptured,
            onGroupClick = { viewModel.toggleGroupSelection(it) },
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            chipOrigins = chipOrigins,
            onContainerOriginChanged = { imageContainerOrigin.value = it },
            isEditMode = isEditMode,
            onEditModeChange = { isEditMode = it },
            onSplit = { viewModel.splitSelectedGroups() },
            onMerge = { viewModel.mergeSelectedGroups() },
            onUndo = { viewModel.undo() },
            onConfirmAutoFill = { inventoryViewModel.showAutoFillDialogFromOcr(state.ocrGroups) },
            activeAttribute = activeAttribute,
            attributes = attributes,
            currentAttributeIndex = currentAttributeIndex,
            onAttributeChange = { index -> currentAttributeIndex = index },
            isDragging = isDragging,
            showControls = state.viewState == ViewState.Normal
        ) {
            CaptureControlBar(
                onImportClick = onImportClick,
                onNextImageClick = { showNextImageDialog = true },
                onTakePhoto = onTakePhoto,
                onEnableCamera = onEnableCamera,
                isCameraActive = state.isCameraActive,
                imageQueueSize = state.imageQueue.size
            )
        }

        if (state.viewState == ViewState.ImageMinimized || state.viewState == ViewState.BothMinimized || (isEditMode && isDragging)) {
            InventoryPanel(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
                inventoryViewModel = inventoryViewModel,
                listViewModel = listViewModel,
                importViewModel = importViewModel,
                inventoryRepository = inventoryRepository,
                importCoordinator = importCoordinator,
                dropTargets = dropTargets,
                cellDropTargets = cellDropTargets,
                highlightedCell = highlightedCell,
                isEditMode = isEditMode,
                tableState = tableState,
                draggingGroup = draggingGroup.value
            )
        }
    }
}
