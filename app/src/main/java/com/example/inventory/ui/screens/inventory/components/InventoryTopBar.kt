package com.example.inventory.ui.screens.inventory.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.inventory.ui.viewmodel.InventoryViewMode

/**
 * 库存页面顶部应用栏（简洁风格）
 * 
 * 显示标题和操作按钮
 * 
 * 优化点：
 * - 使用蓝色背景
 * - 白色图标和文字
 * - 更大的标题字号
 * - 粗体标题
 * - 支持自定义标题内容
 * 
 * @param title 标题文本
 * @param onSearchClick 搜索按钮点击回调
 * @param onCategoryClick 分类按钮点击回调
 * @param onSettingsClick 设置按钮点击回调
 * @param viewMode 当前视图模式
 * @param onToggleViewMode 切换视图模式按钮点击回调
 * @param isToggleEnabled 是否启用切换按钮
 * @param titleContent 自定义标题内容（可选）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryTopBar(
    title: String = "库存列表",
    onSearchClick: () -> Unit = {},
    onCategoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    viewMode: InventoryViewMode,
    onToggleViewMode: () -> Unit,
    isToggleEnabled: Boolean = true,
    titleContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,  // 更大字号
                    fontWeight = FontWeight.Bold,  // 粗体
                    color = Color.White  // 白色文字
                )
            }
        },
        actions = {
            // 切换视图按钮（暂时禁用图标切换，使用文本）
            IconButton(
                onClick = onToggleViewMode,
                enabled = isToggleEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = if (viewMode == InventoryViewMode.CARD) "切换到表格视图" else "切换到卡片视图",
                    tint = if (isToggleEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }

            // 搜索按钮
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color.White  // 白色图标
                )
            }
            
            // 分类按钮
            IconButton(onClick = onCategoryClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "分类",
                    tint = Color.White
                )
            }
            
            // 设置按钮
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,  // 蓝色背景
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        modifier = modifier
    )
}
