package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import com.example.inventory.data.model.InventoryItemEntity

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryTableContent(
    items: LazyPagingItems<InventoryItemEntity>,
    onItemClick: (InventoryItemEntity) -> Unit,
    onItemLongPress: (InventoryItemEntity) -> Unit,
    onItemDelete: (InventoryItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onCellBoundsUpdated: ((Long, String, Rect) -> Unit)? = null,
    highlightedCell: Pair<Long, String>? = null,
    state: LazyListState = rememberLazyListState(),
    horizontalScrollState: ScrollState = rememberScrollState(),
    bottomContent: (@Composable () -> Unit)? = null
) {
    // Define total width for table to enable horizontal scrolling
    // If not in EditMode, we can still allow scrolling if content is wide, or just fill width
    // But user requested "universal horizontal scroll"
    val tableWidth = if (isEditMode) 700.dp else 600.dp // Ensure minimum width for scrolling even in normal mode
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState) // Always enable horizontal scroll
    ) {
        LazyColumn(
            modifier = Modifier
                .width(tableWidth) // Always use fixed width to trigger scroll
                .padding(horizontal = 8.dp),
            state = state
        ) {
            stickyHeader {
                TableHeader(isEditMode = isEditMode)
            }

            items(
                count = items.itemCount,
                key = { index -> items.peek(index)?.id ?: index }
            ) { index ->
                val item = items[index]
                if (item != null) {
                    TableRow(
                        item = item,
                        onRowClick = { onItemClick(item) },
                        onRowLongPress = { onItemLongPress(item) },
                        onDeleteClick = { onItemDelete(item) },
                        isEditMode = isEditMode,
                        onCellBoundsUpdated = onCellBoundsUpdated,
                        highlightedCell = highlightedCell
                    )
                }
            }
            
            // 底部自定义内容 (例如 AddItemButton)
            if (bottomContent != null) {
                item {
                    // 如果在横向滚动容器中，需要确保底部按钮也能正常显示（可能不需要固定宽度）
                    // 在表格模式下，底部按钮通常希望是占满全宽的，或者至少居中
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        bottomContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader(isEditMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            // Use fixed width or weight depending on if we are in a scrollable container
            // Since we set fixed width for the table, we can use weights relative to that total width
            // Or better, use fixed widths to ensure consistency
            // But TableCell helper uses weight. Let's adjust TableCell to support fixed width or weight.
            
            // Re-using weight for simplicity as the container has fixed width 700.dp
            TableCell(text = "品名", weight = 0.2f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "品牌", weight = 0.2f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "型号", weight = 0.2f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "规格", weight = 0.2f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "数量", weight = 0.1f, isHeader = true, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(48.dp)) // Spacer for delete button
        } else {
            TableCell(text = "品名", weight = 0.3f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "品牌/型号", weight = 0.3f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "数量", weight = 0.15f, isHeader = true, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TableCell(text = "条码", weight = 0.25f, isHeader = true, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(48.dp)) // Spacer for delete button
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableRow(
    item: InventoryItemEntity,
    onRowClick: () -> Unit,
    onRowLongPress: () -> Unit,
    onDeleteClick: () -> Unit,
    isEditMode: Boolean,
    onCellBoundsUpdated: ((Long, String, Rect) -> Unit)?,
    highlightedCell: Pair<Long, String>? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // 如果不是编辑模式，使用行级点击；如果是编辑模式，行级点击逻辑可能会被单元格覆盖或禁用
                if (!isEditMode) {
                    Modifier.combinedClickable(
                        onClick = onRowClick,
                        onLongClick = onRowLongPress
                    )
                } else {
                    Modifier
                }
            )
            .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant) // 使用 outlineVariant
            .background(MaterialTheme.colorScheme.surface) // 明确背景色
            .padding(vertical = 12.dp, horizontal = 4.dp), // 增加垂直内边距
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            TableCell(text = item.name, weight = 0.2f, style = MaterialTheme.typography.bodyMedium)
            
            // Brand Cell with bounds reporting and individual interaction
            TableCell(
                text = item.brand, 
                weight = 0.2f, 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onCellBoundsUpdated?.invoke(item.id, "brand", coordinates.boundsInWindow())
                    }
                    .background(
                        if (highlightedCell?.first == item.id && highlightedCell?.second == "brand") 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                        else Color.Transparent
                    )
                    // 可以在这里添加 clickable 以支持字段级编辑，例如弹出修改对话框
                    .combinedClickable(
                        onClick = { /* Field click action */ },
                        onLongClick = { /* Field long press action - could trigger field edit */ }
                    )
            )
            
            // Model Cell with bounds reporting
            TableCell(
                text = item.model, 
                weight = 0.2f, 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onCellBoundsUpdated?.invoke(item.id, "model", coordinates.boundsInWindow())
                    }
                    .background(
                        if (highlightedCell?.first == item.id && highlightedCell?.second == "model") 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                        else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { /* Field click action */ },
                        onLongClick = { /* Field long press action */ }
                    )
            )
            
            // Specs (Parameters) Cell with bounds reporting
            TableCell(
                text = item.parameters, 
                weight = 0.2f, 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onCellBoundsUpdated?.invoke(item.id, "parameters", coordinates.boundsInWindow())
                    }
                    .background(
                        if (highlightedCell?.first == item.id && highlightedCell?.second == "parameters") 
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                        else Color.Transparent
                    )
                    .combinedClickable(
                        onClick = { /* Field click action */ },
                        onLongClick = { /* Field long press action */ }
                    )
            )
            
            TableCell(text = item.quantity.toString(), weight = 0.1f, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        } else {
            TableCell(text = item.name, weight = 0.3f, style = MaterialTheme.typography.bodyMedium)
            TableCell(text = "${item.brand} ${item.model}".trim(), weight = 0.3f, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TableCell(text = item.quantity.toString(), weight = 0.15f, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            TableCell(text = item.barcode, weight = 0.25f, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        IconButton(onClick = onDeleteClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    isHeader: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .weight(weight)
            .padding(horizontal = 4.dp), // 减小水平内边距以适应更多内容
        fontWeight = fontWeight ?: if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = if (isHeader) 14.sp else 14.sp, // 统一字体大小
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        color = color,
        style = style
    )
}
