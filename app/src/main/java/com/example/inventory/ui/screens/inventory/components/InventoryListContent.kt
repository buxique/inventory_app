package com.example.inventory.ui.screens.inventory.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.paging.compose.LazyPagingItems
import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.inventory.data.model.InventoryItemEntity

/**
 * 库存列表内容组件
 * 
 * 显示商品列表，支持动画和交互
 * 
 * 设计特点：
 * - 更大的内边距（20dp）
 * - 更大的卡片间距（16dp）
 * - 添加背景色
 * - 简洁现代风格
 * - 支持底部自定义内容
 * - 支持滑动删除
 * 
 * @param items 商品列表（分页）
 * @param selectedItemId 当前选中的商品ID
 * @param onItemClick 商品点击回调
 * @param onItemLongPress 商品长按回调，传递商品和长按位置
 * @param onItemDelete 商品删除回调
 * @param onRowBoundsUpdated 行边界更新回调（用于拖拽功能）
 * @param bottomContent 底部自定义内容（可选）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryListContent(
    items: LazyPagingItems<InventoryItemEntity>,
    selectedItemId: Long? = null,
    onItemClick: (InventoryItemEntity) -> Unit,
    onItemLongPress: (InventoryItemEntity, Offset) -> Unit,
    onItemDelete: (InventoryItemEntity) -> Unit,
    onRowBoundsUpdated: ((Long, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),  // 添加背景色
        contentPadding = PaddingValues(20.dp),  // 更大的内边距
        verticalArrangement = Arrangement.spacedBy(16.dp)  // 更大的间距
    ) {
        items(
            count = items.itemCount,
            key = { index -> items[index]?.id ?: index }
        ) { index ->
            val item = items[index]
            if (item != null) {
                InventoryItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { position -> onItemLongPress(item, position) },
                    onDelete = { onItemDelete(item) },
                    isSelected = item.id == selectedItemId,
                    onBoundsUpdated = if (onRowBoundsUpdated != null) {
                        { rect -> onRowBoundsUpdated(item.id, rect) }
                    } else null,
                    modifier = Modifier
                )
            }
        }
        
        // 底部自定义内容
        if (bottomContent != null) {
            item {
                bottomContent()
            }
        }
    }

}
