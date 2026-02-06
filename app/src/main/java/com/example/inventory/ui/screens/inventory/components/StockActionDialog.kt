package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventory.data.model.InventoryItemEntity

@Composable
fun StockActionDialog(
    visible: Boolean,
    item: InventoryItemEntity?,
    onEdit: () -> Unit,
    onInbound: () -> Unit,
    onOutbound: () -> Unit,
    onCount: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || item == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onEdit()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "编辑")
                }
                Button(
                    onClick = {
                        onInbound()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "入库")
                }
                Button(
                    onClick = {
                        onOutbound()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "出库")
                }
                Button(
                    onClick = {
                        onCount()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "盘点")
                }
            }
        },
        confirmButton = {}
    )
}
