package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventory.ui.state.StockAction

@Composable
fun RecordInputDialog(
    visible: Boolean,
    action: StockAction?,
    quantity: String,
    operator: String,
    remark: String,
    onQuantityChange: (String) -> Unit,
    onOperatorChange: (String) -> Unit,
    onRemarkChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || action == null) return

    val title = when (action) {
        StockAction.Inbound -> "入库"
        StockAction.Outbound -> "出库"
        StockAction.Count -> "盘点"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = onQuantityChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(text = "数量") }
                )
                OutlinedTextField(
                    value = operator,
                    onValueChange = onOperatorChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(text = "操作员") }
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = onRemarkChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(text = "备注") }
                )
            }
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
