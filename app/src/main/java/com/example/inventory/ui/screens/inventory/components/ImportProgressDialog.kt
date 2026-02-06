package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.ui.state.ImportProgress

/**
 * 导入进度对话框
 * 
 * 显示文件导入的进度信息
 * 
 * @param visible 是否显示
 * @param progress 导入进度
 * @param onCancel 取消回调
 * @param modifier 修饰符
 */
@Composable
fun ImportProgressDialog(
    visible: Boolean,
    progress: ImportProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    AlertDialog(
        onDismissRequest = { /* 导入时不允许点击外部关闭 */ },
        title = { Text("正在导入...") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 进度条
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 进度百分比
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 数量统计
                Text(
                    text = "已导入: ${progress.currentCount} / ${progress.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 错误信息
                progress.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (progress.isImporting) {
                TextButton(onClick = onCancel) {
                    Text("取消导入")
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text("关闭")
                }
            }
        }
    )
}
