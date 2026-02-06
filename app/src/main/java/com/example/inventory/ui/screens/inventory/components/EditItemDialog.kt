package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.inventory.data.model.InventoryItemEntity

@Composable
fun EditItemDialog(
    visible: Boolean,
    item: InventoryItemEntity?,
    editText: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || item == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "编辑商品") },
        text = {
            OutlinedTextField(
                value = editText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                label = { Text("商品名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}
