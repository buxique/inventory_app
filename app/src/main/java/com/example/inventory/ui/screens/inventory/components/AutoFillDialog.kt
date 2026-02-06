package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventory.data.model.InventoryItemEntity

/**
 * 自动填充确认对话框
 * 
 * 用于展示 OCR 自动识别并映射后的商品信息，允许用户在保存前进行核对和修改。
 */
@Composable
fun AutoFillDialog(
    visible: Boolean,
    initialData: InventoryItemEntity,
    onConfirm: (InventoryItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    // 使用 initialData 作为 key，当外部数据变化时重置状态
    var name by remember(initialData) { mutableStateOf(initialData.name) }
    var brand by remember(initialData) { mutableStateOf(initialData.brand) }
    var model by remember(initialData) { mutableStateOf(initialData.model) }
    var parameters by remember(initialData) { mutableStateOf(initialData.parameters) }
    var quantity by remember(initialData) { mutableStateOf(initialData.quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认自动填充信息") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "OCR 已自动识别以下信息，请确认：")
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("品名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("品牌") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("型号") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                OutlinedTextField(
                    value = parameters,
                    onValueChange = { parameters = it },
                    label = { Text("规格/参数") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) {
                            quantity = it
                        }
                    },
                    label = { Text("数量") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        initialData.copy(
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            parameters = parameters.trim(),
                            quantity = quantity.toIntOrNull() ?: 0
                        )
                    )
                }
            ) {
                Text("确认填入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
