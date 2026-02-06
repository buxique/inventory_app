package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 列表底部新增按钮
 * 
 * 显示在商品列表底部，点击后弹出底部菜单提供三个选项：
 * - 手动添加：跳转到添加商品页面
 * - 拍照导入：调用系统相机
 * - 图片导入：打开图片选择器
 * 
 * @param onManualAdd 手动添加回调
 * @param onCameraAdd 拍照导入回调
 * @param onImageImport 图片导入回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemButton(
    onManualAdd: () -> Unit,
    onCameraAdd: () -> Unit,
    onImageImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = { showSheet = true },
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("新增")
        }
    }
    
    // 底部弹窗
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题
                Text(
                    text = "添加物品",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                // 手动添加
                AddItemOption(
                    icon = Icons.Default.Add,
                    iconColor = Color(0xFF9C27B0),  // 紫色
                    text = "手动添加",
                    onClick = {
                        showSheet = false
                        onManualAdd()
                    }
                )
                
                // 拍照导入
                AddItemOption(
                    icon = Icons.Default.Add,  // 使用相机图标的替代
                    iconColor = Color(0xFF2196F3),  // 蓝色
                    text = "拍照导入",
                    onClick = {
                        showSheet = false
                        onCameraAdd()
                    }
                )
                
                // 图片导入
                AddItemOption(
                    icon = Icons.Default.Add,  // 使用图片图标的替代
                    iconColor = Color(0xFF4CAF50),  // 绿色
                    text = "图片导入",
                    onClick = {
                        showSheet = false
                        onImageImport()
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 添加物品选项组件
 */
@Composable
private fun AddItemOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
            color = Color(0xFF212121)
        )
    }
}
