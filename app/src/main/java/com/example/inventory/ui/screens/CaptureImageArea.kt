package com.example.inventory.ui.screens

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.inventory.R
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.ui.state.CaptureUiState
import com.example.inventory.ui.state.ProcessingState
import java.util.concurrent.Executor

@Composable
internal fun CaptureImageArea(
    modifier: Modifier,
    state: CaptureUiState,
    hasCameraPermission: Boolean,
    imageCapture: ImageCapture,
    cameraExecutor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onGroupClick: (OcrGroup) -> Unit,
    onDragStart: (OcrGroup, Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    chipOrigins: MutableMap<String, Offset>,
    onContainerOriginChanged: (Offset) -> Unit,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onUndo: () -> Unit,
    onConfirmAutoFill: () -> Unit,
    activeAttribute: String,
    attributes: List<Pair<String, String>>,
    currentAttributeIndex: Int,
    onAttributeChange: (Int) -> Unit,
    isDragging: Boolean,
    showControls: Boolean,
    controls: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        if (state.isCameraActive && hasCameraPermission) {
            CameraPreview(
                imageCapture = imageCapture,
                executor = cameraExecutor,
                onImageCaptured = onImageCaptured
            )
        } else {
            val currentUri = state.imageUri
            if (currentUri != null) {
                ImageBrowser(
                    uri = currentUri,
                    ocrGroups = state.ocrGroups,
                    processingState = state.processingState,
                    selectedGroupIds = state.selectedGroupIds,
                    onGroupClick = onGroupClick,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDoubleTap = {},
                    chipOrigins = chipOrigins,
                    onContainerOriginChanged = onContainerOriginChanged,
                    isEditMode = isEditMode,
                    onEditModeChange = onEditModeChange,
                    onSplit = onSplit,
                    onMerge = onMerge,
                    onUndo = onUndo,
                    onConfirmAutoFill = onConfirmAutoFill,
                    activeAttribute = activeAttribute,
                    attributes = attributes,
                    currentAttributeIndex = currentAttributeIndex,
                    onAttributeChange = onAttributeChange,
                    isDragging = isDragging
                )

                if (state.processingState is ProcessingState.Processing) {
                    ScanningEffect()
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.select_import_or_camera), color = Color.White)
                }
            }
        }

        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                controls()
            }
        }
    }
}

@Composable
internal fun CaptureControlBar(
    onImportClick: () -> Unit,
    onNextImageClick: () -> Unit,
    onTakePhoto: () -> Unit,
    onEnableCamera: () -> Unit,
    isCameraActive: Boolean,
    imageQueueSize: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Text(stringResource(R.string.import_image_button), color = MaterialTheme.colorScheme.onSurface)
        }

        if (imageQueueSize > 0) {
            Button(
                onClick = onNextImageClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.next_with_count, imageQueueSize))
            }
        }

        if (isCameraActive) {
            Button(
                onClick = onTakePhoto,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White, CircleShape)
                )
            }
        } else {
            Button(
                onClick = onEnableCamera,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(stringResource(R.string.camera_add), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
