package com.example.inventory.ui.screens.inventory.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.inventory.data.model.InventoryItemEntity

@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    item: InventoryItemEntity?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || item == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认删除") },
        text = { Text(text = "确定删除 ${item.name} 吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
