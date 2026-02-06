package com.example.inventory.ui.screens.inventory.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.inventory.data.model.InventoryItemEntity
import kotlin.math.roundToInt

/**
 * 库存商品卡片组件
 * 
 * 简洁现代风格设计：
 * - 更大的内边距（20dp）
 * - 更大的圆角（16dp）
 * - 更浅的阴影（1dp）
 * - 粉色徽章强调
 * - 清晰的文字层级
 * - 按压动画反馈
 * - 支持左滑删除
 * 
 * @param item 商品实体
 * @param onClick 点击回调
 * @param onLongPress 长按回调，传递长按位置
 * @param onDelete 删除回调
 * @param isSelected 是否被选中
 * @param onBoundsUpdated 边界更新回调（用于拖拽功能）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryItemCard(
    item: InventoryItemEntity,
    onClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onBoundsUpdated: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 按压状态
    var isPressed by remember { mutableStateOf(false) }
    
    // 滑动偏移量
    var offsetX by remember { mutableFloatStateOf(0f) }
    
    // 卡片宽度
    var cardWidth by remember { mutableFloatStateOf(0f) }
    
    // 最大滑动距离（卡片宽度的 1/6）
    val maxSwipeDistance = cardWidth / 6f
    
    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "cardScale"
    )
    
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                cardWidth = size.width.toFloat()
            }
            .onGloballyPositioned { coordinates ->
                // 报告卡片的边界位置（用于拖拽功能）
                onBoundsUpdated?.invoke(coordinates.boundsInWindow())
            }
    ) {
        // 背景层：红色删除区域
        if (offsetX < 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        color = Color.Red,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                // 删除按钮 - 位于右侧 1/6 区域的中心
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(with(density) { maxSwipeDistance.toDp() })
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(0.6f)
                        )
                    }
                }
            }
        }
        
        // 前景层：卡片内容
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // 拖动结束后，如果滑动距离超过一半，则保持展开状态
                            // 否则回弹到初始位置
                            if (offsetX < -maxSwipeDistance / 2) {
                                offsetX = -maxSwipeDistance
                            } else {
                                offsetX = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // 只允许向左滑动
                            val newOffset = offsetX + dragAmount
                            offsetX = newOffset.coerceIn(-maxSwipeDistance, 0f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isPressed = false
                            // 如果已经滑动，点击时收回
                            if (offsetX < 0) {
                                offsetX = 0f
                            } else {
                                onClick()
                            }
                        },
                        onLongPress = { offset ->
                            isPressed = false
                            // 长按时收回滑动
                            if (offsetX < 0) {
                                offsetX = 0f
                            } else {
                                onLongPress(offset)
                            }
                        },
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            shape = MaterialTheme.shapes.medium,  // 16dp 圆角
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 1.dp  // 更浅的阴影
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),  // 更大的内边距
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：商品信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)  // 增加间距
                ) {
                    // 商品名称 - 大字号、粗体
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,  // 18sp
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    // 品牌和型号 - 中字号、浅色
                    if (item.brand.isNotBlank() || item.model.isNotBlank()) {
                        Text(
                            text = "${item.brand} ${item.model}".trim(),
                            style = MaterialTheme.typography.bodyMedium,  // 14sp
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    // 条码 - 小字号、更浅色
                    if (item.barcode.isNotBlank()) {
                        Text(
                            text = "条码: ${item.barcode}",
                            style = MaterialTheme.typography.bodySmall,  // 12sp
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            }
                        )
                    }
                }
                
                // 右侧：库存数量徽章
                QuantityBadge(
                    quantity = item.quantity,
                    isSelected = isSelected
                )
            }
        }
    }
}

// ==================== Preview 预览 ====================

/**
 * 库存商品数据提供者（用于预览）
 */
private class InventoryItemProvider : PreviewParameterProvider<InventoryItemEntity> {
    override val values = sequenceOf(
        // 正常商品
        InventoryItemEntity(
            id = 1,
            listId = 1,
            name = "iPhone 15 Pro",
            brand = "Apple",
            model = "A2848",
            parameters = "256GB 钛金属",
            barcode = "1234567890123",
            quantity = 15,
            unit = "台",
            location = "A区-01货架-03层",
            remark = "热销商品"
        ),
        // 库存不足
        InventoryItemEntity(
            id = 2,
            listId = 1,
            name = "MacBook Pro 14",
            brand = "Apple",
            model = "M3",
            parameters = "16GB+512GB",
            barcode = "9876543210987",
            quantity = 2,
            unit = "台",
            location = "A区-02货架-01层",
            remark = "库存不足"
        ),
        // 库存为零
        InventoryItemEntity(
            id = 3,
            listId = 1,
            name = "AirPods Pro",
            brand = "Apple",
            model = "第二代",
            parameters = "USB-C 充电",
            barcode = "5555555555555",
            quantity = 0,
            unit = "个",
            location = "B区-05货架-02层",
            remark = "已售罄"
        ),
        // 大库存
        InventoryItemEntity(
            id = 4,
            listId = 1,
            name = "USB-C 数据线",
            brand = "Anker",
            model = "PowerLine III",
            parameters = "1米 黑色",
            barcode = "7777777777777",
            quantity = 999,
            unit = "条",
            location = "C区-10货架-05层",
            remark = "库存充足"
        ),
        // 无品牌型号
        InventoryItemEntity(
            id = 5,
            listId = 1,
            name = "办公椅",
            brand = "",
            model = "",
            parameters = "",
            barcode = "",
            quantity = 50,
            unit = "把",
            location = "D区-15货架",
            remark = ""
        )
    )
}

/**
 * 预览：正常状态
 */
@Preview(
    name = "正常状态",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardNormalPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 1,
                    listId = 1,
                    name = "iPhone 15 Pro",
                    brand = "Apple",
                    model = "A2848",
                    parameters = "256GB 钛金属",
                    barcode = "1234567890123",
                    quantity = 15,
                    unit = "台",
                    location = "A区-01货架-03层",
                    remark = "热销商品"
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = false
            )
        }
    }
}

/**
 * 预览：选中状态
 */
@Preview(
    name = "选中状态",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardSelectedPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 1,
                    listId = 1,
                    name = "iPhone 15 Pro",
                    brand = "Apple",
                    model = "A2848",
                    parameters = "256GB 钛金属",
                    barcode = "1234567890123",
                    quantity = 15,
                    unit = "台",
                    location = "A区-01货架-03层",
                    remark = "热销商品"
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = true
            )
        }
    }
}

/**
 * 预览：库存不足
 */
@Preview(
    name = "库存不足",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardLowStockPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 2,
                    listId = 1,
                    name = "MacBook Pro 14",
                    brand = "Apple",
                    model = "M3",
                    parameters = "16GB+512GB",
                    barcode = "9876543210987",
                    quantity = 2,
                    unit = "台",
                    location = "A区-02货架-01层",
                    remark = "库存不足"
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = false
            )
        }
    }
}

/**
 * 预览：库存为零
 */
@Preview(
    name = "库存为零",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardZeroStockPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 3,
                    listId = 1,
                    name = "AirPods Pro",
                    brand = "Apple",
                    model = "第二代",
                    parameters = "USB-C 充电",
                    barcode = "5555555555555",
                    quantity = 0,
                    unit = "个",
                    location = "B区-05货架-02层",
                    remark = "已售罄"
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = false
            )
        }
    }
}

/**
 * 预览：大库存
 */
@Preview(
    name = "大库存",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardLargeStockPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 4,
                    listId = 1,
                    name = "USB-C 数据线",
                    brand = "Anker",
                    model = "PowerLine III",
                    parameters = "1米 黑色",
                    barcode = "7777777777777",
                    quantity = 999,
                    unit = "条",
                    location = "C区-10货架-05层",
                    remark = "库存充足"
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = false
            )
        }
    }
}

/**
 * 预览：无品牌型号
 */
@Preview(
    name = "无品牌型号",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardNoBrandPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 5,
                    listId = 1,
                    name = "办公椅",
                    brand = "",
                    model = "",
                    parameters = "",
                    barcode = "",
                    quantity = 50,
                    unit = "把",
                    location = "D区-15货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = false
            )
        }
    }
}

/**
 * 预览：多个卡片列表
 */
@Preview(
    name = "卡片列表",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5,
    heightDp = 800
)
@Composable
private fun InventoryItemCardListPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 正常商品
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 1,
                    listId = 1,
                    name = "iPhone 15 Pro",
                    brand = "Apple",
                    model = "A2848",
                    parameters = "256GB 钛金属",
                    barcode = "1234567890123",
                    quantity = 15,
                    unit = "台",
                    location = "A区-01货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {}
            )
            
            // 选中状态
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 2,
                    listId = 1,
                    name = "MacBook Pro 14",
                    brand = "Apple",
                    model = "M3",
                    parameters = "16GB+512GB",
                    barcode = "9876543210987",
                    quantity = 8,
                    unit = "台",
                    location = "A区-02货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {},
                isSelected = true
            )
            
            // 库存不足
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 3,
                    listId = 1,
                    name = "AirPods Pro",
                    brand = "Apple",
                    model = "第二代",
                    parameters = "USB-C",
                    barcode = "5555555555555",
                    quantity = 2,
                    unit = "个",
                    location = "B区-05货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {}
            )
            
            // 无品牌型号
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 4,
                    listId = 1,
                    name = "办公椅",
                    brand = "",
                    model = "",
                    parameters = "",
                    barcode = "",
                    quantity = 50,
                    unit = "把",
                    location = "D区-15货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {}
            )
        }
    }
}

/**
 * 预览：深色模式
 */
@Preview(
    name = "深色模式",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun InventoryItemCardDarkPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .padding(16.dp)
        ) {
            InventoryItemCard(
                item = InventoryItemEntity(
                    id = 1,
                    listId = 1,
                    name = "iPhone 15 Pro",
                    brand = "Apple",
                    model = "A2848",
                    parameters = "256GB 钛金属",
                    barcode = "1234567890123",
                    quantity = 15,
                    unit = "台",
                    location = "A区-01货架",
                    remark = ""
                ),
                onClick = {},
                onLongPress = {},
                onDelete = {}
            )
        }
    }
}

/**
 * 预览：使用 PreviewParameter
 */
@Preview(
    name = "多状态预览",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun InventoryItemCardParameterPreview(
    @PreviewParameter(InventoryItemProvider::class) item: InventoryItemEntity
) {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InventoryItemCard(
                item = item,
                onClick = {},
                onLongPress = {},
                onDelete = {}
            )
        }
    }
}
