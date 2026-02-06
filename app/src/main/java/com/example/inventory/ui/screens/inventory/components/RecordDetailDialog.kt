package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.viewmodel.InventoryViewModelRefactored

@Composable
fun RecordDetailDialog(
    viewModel: InventoryViewModelRefactored
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.dialogState != DialogState.RecordDialog) return

    AlertDialog(
        onDismissRequest = { viewModel.hideRecordDialog() },
        title = { Text(text = "库存记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前库存: ${uiState.selectedItem?.quantity ?: 0}",
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "库存记录功能开发中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.hideRecordDialog() }) {
                Text(text = "关闭")
            }
        }
    )
}
