package com.example.inventory.ui.screens.inventory.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 库存页面浮动操作按钮（简洁风格）
 * 
 * 用于添加新商品
 * 
 * 优化点：
 * - 使用粉色（强调色）
 * - 更大的尺寸
 * - 更明显的阴影
 * 
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun InventoryFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondary,  // 粉色
        contentColor = MaterialTheme.colorScheme.onSecondary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,  // 更明显的阴影
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加商品"
        )
    }
}
