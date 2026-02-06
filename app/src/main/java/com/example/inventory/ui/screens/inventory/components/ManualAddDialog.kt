package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.inventory.ui.state.ManualAddForm

@Composable
fun ManualAddDialog(
    visible: Boolean,
    form: ManualAddForm,
    onUpdate: ((ManualAddForm) -> ManualAddForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "手动添加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> onUpdate { it.copy(name = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") }
                )
                OutlinedTextField(
                    value = form.brand,
                    onValueChange = { value -> onUpdate { it.copy(brand = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("品牌") }
                )
                OutlinedTextField(
                    value = form.model,
                    onValueChange = { value -> onUpdate { it.copy(model = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("型号") }
                )
                OutlinedTextField(
                    value = form.parameters,
                    onValueChange = { value -> onUpdate { it.copy(parameters = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("参数") }
                )
                OutlinedTextField(
                    value = form.barcode,
                    onValueChange = { value -> onUpdate { it.copy(barcode = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("条码") }
                )
                OutlinedTextField(
                    value = form.quantity,
                    onValueChange = { value -> onUpdate { it.copy(quantity = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("数量") }
                )
                OutlinedTextField(
                    value = form.remark,
                    onValueChange = { value -> onUpdate { it.copy(remark = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
