package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.ui.theme.InventoryAppTheme

/**
 * 空库存屏幕（简洁风格）
 * 
 * 当没有商品时显示的空状态界面
 * 
 * 优化点：
 * - 更大的图标（120dp）
 * - 使用品牌色（半透明蓝色）
 * - 更大的间距（24dp）
 * - 粉色按钮
 * - 更友好的文案
 * 
 * @param onAddClick 添加商品回调
 * @param modifier 修饰符
 */
@Composable
fun EmptyInventoryScreen(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),  // 添加背景色
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),  // 更大间距
            modifier = Modifier.padding(48.dp)  // 更大内边距
        ) {
            // 空状态图标 - 使用主色调
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "空库存",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),  // 半透明蓝色
                modifier = Modifier.size(120.dp)  // 更大图标
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 标题 - 更大更粗
            Text(
                text = "还没有商品",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // 提示文本 - 浅色
            Text(
                text = "点击下方按钮添加第一个商品\n开始管理您的库存",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 添加按钮 - 使用粉色
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,  // 粉色
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = RoundedCornerShape(12.dp),  // 圆角
                modifier = Modifier.height(56.dp)  // 更高的按钮
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "添加商品",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyInventoryScreenPreview() {
    InventoryAppTheme {
        EmptyInventoryScreen(onAddClick = {})
    }
}
