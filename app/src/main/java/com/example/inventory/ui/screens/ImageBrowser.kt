package com.example.inventory.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import com.example.inventory.util.Constants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.ui.state.ProcessingState
import kotlin.math.min

@Composable
internal fun ImageBrowser(
    uri: android.net.Uri,
    ocrGroups: List<OcrGroup>,
    processingState: ProcessingState,
    selectedGroupIds: Set<String>,
    onGroupClick: (OcrGroup) -> Unit,
    onDragStart: (OcrGroup, Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    chipOrigins: MutableMap<String, Offset>,
    onContainerOriginChanged: (Offset) -> Unit,
    isEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
    onUndo: () -> Unit,
    onConfirmAutoFill: () -> Unit,
    activeAttribute: String = "name",
    attributes: List<Pair<String, String>> = emptyList(),
    currentAttributeIndex: Int = 0,
    onAttributeChange: (Int) -> Unit = {},
    isDragging: Boolean = false
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    if (isEditMode) {
        val attrName = when (activeAttribute) {
            "name" -> "品名"
            "brand" -> "品牌"
            "model" -> "型号"
            "parameters" -> "规格"
            "quantity" -> "数量"
            else -> "未知"
        }
        val attrColor = when (activeAttribute) {
            "brand" -> Color.Red
            "model" -> Color.Blue
            "parameters" -> Color.Green
            else -> Color(0xFFFFA500)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .zIndex(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .background(attrColor.copy(alpha = 0.9f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "正在编辑: $attrName",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    val painter = rememberAsyncImagePainter(uri)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .onGloballyPositioned { coords ->
                onContainerOriginChanged(coords.boundsInWindow().topLeft)
            }
            .transformable(state = transformState)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            Image(
                painter = painter,
                contentDescription = "Scanned Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            OcrChipsOverlay(
                groups = ocrGroups,
                selectedGroupIds = selectedGroupIds,
                onGroupClick = onGroupClick,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                    onDoubleTap()
                },
                chipOrigins = chipOrigins,
                painter = painter,
                showLabels = !isEditMode,
                activeAttribute = activeAttribute,
                onSplit = onSplit,
                onMerge = onMerge
            )
        }

        if (processingState is ProcessingState.Processing) {
            ScanningEffect()
        }

        if (ocrGroups.isNotEmpty() && processingState is ProcessingState.Success) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .padding(8.dp)
            ) {
                if (!isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onConfirmAutoFill() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("确认自动填充")
                        }

                        Button(
                            onClick = { onEditModeChange(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("自行编辑")
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("请拖拽文字到下方表格", color = Color.White)
                        Button(
                            onClick = { onUndo() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = true
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("撤销")
                        }

                        Button(
                            onClick = { onEditModeChange(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("退出")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScanningEffect() {
    val infiniteTransition = rememberInfiniteTransition()
    val dy by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Constants.UI.SCANNING_ANIMATION_DURATION, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val height = maxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Green)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = dy * height.toPx()
                }
        )
    }
}

/**
 * OCR 叠加层
 *
 * groups.box 使用与图片像素一致的坐标系（left, top, right, bottom）。
 * 这里通过图片显示的缩放与偏移将 bbox 映射到屏幕坐标，支持点击与拖拽。
 */
@Composable
internal fun OcrChipsOverlay(
    groups: List<OcrGroup>,
    selectedGroupIds: Set<String>,
    onGroupClick: (OcrGroup) -> Unit,
    onDragStart: (OcrGroup, Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: () -> Unit,
    chipOrigins: MutableMap<String, Offset>,
    painter: AsyncImagePainter,
    showLabels: Boolean = false,
    activeAttribute: String = "name",
    onSplit: () -> Unit,
    onMerge: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val state = painter.state
        if (state is AsyncImagePainter.State.Success) {
            val imgSize = state.painter.intrinsicSize
            if (imgSize.width > 0 && imgSize.height > 0) {
                val scale = min(
                    containerWidthPx / imgSize.width,
                    containerHeightPx / imgSize.height
                )

                val displayedWidth = imgSize.width * scale
                val displayedHeight = imgSize.height * scale

                val offsetX = (containerWidthPx - displayedWidth) / 2
                val offsetY = (containerHeightPx - displayedHeight) / 2

                val layoutInfos = remember(groups, scale, offsetX, offsetY) {
                    groups.map { group ->
                        if (group.box.size == 4) {
                            val left = group.box[0] * scale + offsetX
                            val top = group.box[1] * scale + offsetY
                            val right = group.box[2] * scale + offsetX
                            val bottom = group.box[3] * scale + offsetY

                            chipOrigins[group.id] = Offset(left, top)

                            Triple(group, Rect(left, top, right, bottom), selectedGroupIds.contains(group.id))
                        } else null
                    }.filterNotNull()
                }

                var activeGroup by remember { mutableStateOf<OcrGroup?>(null) }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(layoutInfos) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onDoubleTap()
                                },
                                onTap = { tapOffset ->
                                    val hit = layoutInfos.firstOrNull { (_, rect, _) ->
                                        rect.contains(tapOffset)
                                    }
                                    if (hit != null) {
                                        onGroupClick(hit.first)
                                    }
                                },
                                onLongPress = { longPressOffset ->
                                    val hit = layoutInfos.firstOrNull { (_, rect, _) ->
                                        rect.contains(longPressOffset)
                                    }
                                    if (hit != null) {
                                        contextMenuPosition = Offset(
                                            longPressOffset.x,
                                            hit.second.bottom + 8.dp.toPx()
                                        )
                                        showContextMenu = true
                                    }
                                }
                            )
                        }
                        .pointerInput(layoutInfos) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val hit = layoutInfos.firstOrNull { (_, rect, _) ->
                                        rect.contains(startOffset)
                                    }
                                    if (hit != null) {
                                        activeGroup = hit.first
                                        onDragStart(hit.first, startOffset - Offset(hit.second.left, hit.second.top))
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (activeGroup != null) {
                                        onDrag(change, dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    activeGroup = null
                                    onDragEnd()
                                },
                                onDragCancel = {
                                    activeGroup = null
                                    onDragEnd()
                                }
                            )
                        }
                ) {
                    layoutInfos.forEach { (group, rect, isSelected) ->
                        val cornerRadius = CornerRadius(rect.height / 2, rect.height / 2)

                        if (isSelected) {
                            val fillColor = when (activeAttribute) {
                                "brand" -> Color.Red.copy(alpha = 0.3f)
                                "model" -> Color.Blue.copy(alpha = 0.3f)
                                "parameters" -> Color.Green.copy(alpha = 0.3f)
                                else -> Color(0xFFFFA500).copy(alpha = 0.3f)
                            }

                            drawRoundRect(
                                color = fillColor,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                cornerRadius = cornerRadius
                            )
                        }

                        val strokeColor = if (isSelected) {
                            when (activeAttribute) {
                                "brand" -> Color.Red
                                "model" -> Color.Blue
                                "parameters" -> Color.Green
                                else -> Color(0xFFFFA500)
                            }
                        } else Color.White

                        drawRoundRect(
                            color = strokeColor,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = cornerRadius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )

                        if (showLabels) {
                            val text = group.tokens.joinToString("") { it.text }
                            val (underlineColor, _) = when {
                                text.contains("品牌", ignoreCase = true) || text.length < 4 -> Color.Red to "品牌"
                                text.matches(Regex(".*[A-Za-z0-9]+.*")) -> Color.Blue to "型号"
                                else -> Color.Green to "规格"
                            }

                            drawLine(
                                color = underlineColor,
                                start = Offset(rect.left, rect.bottom + 2.dp.toPx()),
                                end = Offset(rect.right, rect.bottom + 2.dp.toPx()),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }

                if (showContextMenu) {
                    val isMultiSelect = selectedGroupIds.size > 1

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    showContextMenu = false
                                }
                            }
                    )

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(contextMenuPosition.x.toInt(), contextMenuPosition.y.toInt()) }
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable {
                                if (isMultiSelect) {
                                    onMerge()
                                } else {
                                    onSplit()
                                }
                                showContextMenu = false
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isMultiSelect) Icons.Default.Add else Icons.Default.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isMultiSelect) "合并选中的文本块" else "拆分此文本块",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
