package com.example.inventory.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    onBack: () -> Unit,
    onSave: (AddItemFormData) -> Unit,
    modifier: Modifier = Modifier
) {
    var itemName by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("0") }
    var unit by remember { mutableStateOf("个") }
    var location by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    
    var unitMenuExpanded by remember { mutableStateOf(false) }
    val units = listOf("个", "件", "台", "箱", "包", "瓶", "盒", "袋", "桶", "组")
    
    // 添加保存状态指示器
    var isSaving by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.add_inventory),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = Color(0xFF2196F3),
                            fontSize = 16.sp
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            // 保存操作可能是异步的，设置 isSaving 后不应立即重置
                            // 保存成功后会导航回前一页，屏幕自动销毁
                            // 如果保存失败，需要外部通过回调重置状态
                            if (itemName.isNotBlank() && !isSaving) {
                                isSaving = true
                                onSave(
                                    AddItemFormData(
                                        name = itemName,
                                        model = model,
                                        brand = brand,
                                        category = category,
                                        quantity = quantity.toIntOrNull() ?: 0,
                                        unit = unit,
                                        location = location,
                                        remark = remark
                                    )
                                )
                                // 注意：不在此处重置 isSaving，避免异步操作中重复提交
                            }
                        },
                        enabled = itemName.isNotBlank() && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = Color(0xFF2196F3),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = if (isSaving) stringResource(R.string.saving) else stringResource(R.string.complete),
                            color = if (itemName.isNotBlank() && !isSaving) Color(0xFF2196F3) else Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = stringResource(R.string.basic_info)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = stringResource(R.string.item_name),
                        placeholder = stringResource(R.string.item_name_hint),
                        required = true
                    )
                    
                    FormTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = stringResource(R.string.model),
                        placeholder = stringResource(R.string.model_hint)
                    )
                    
                    FormTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = stringResource(R.string.brand),
                        placeholder = stringResource(R.string.brand_hint)
                    )
                    
                    FormTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = stringResource(R.string.category_label),
                        placeholder = stringResource(R.string.category_hint)
                    )
                }
            }
            
            SectionCard(title = stringResource(R.string.inventory_info)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FormTextField(
                                value = quantity,
                                onValueChange = { newValue ->
                                    // 只允许输入数字，并验证为正整数
                                    if (newValue.isEmpty()) {
                                        quantity = ""
                                    } else {
                                        // 过滤非数字字符
                                        val filtered = newValue.filter { it.isDigit() }
                                        // 验证为正整数（不允许以0开头，除非就是"0"）
                                        if (filtered.isNotEmpty()) {
                                            val intValue = filtered.toIntOrNull()
                                            if (intValue != null && intValue >= 0) {
                                                quantity = filtered
                                            }
                                        }
                                    }
                                },
                                label = stringResource(R.string.quantity),
                                placeholder = stringResource(R.string.quantity_hint),
                                required = true,
                                keyboardType = KeyboardType.Number,
                                isError = quantity.isNotEmpty() && (quantity.toIntOrNull() == null || quantity.toInt() < 0),
                                supportingText = if (quantity.isNotEmpty() && (quantity.toIntOrNull() == null || quantity.toInt() < 0)) {
                                    stringResource(R.string.quantity_error)
                                } else null
                            )
                        }
                        
                        Box(modifier = Modifier.weight(1f)) {
                            Column {
                                Text(
                                    text = stringResource(R.string.unit_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF757575),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { unitMenuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.unit_select)
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2196F3),
                                        unfocusedBorderColor = Color(0xFFE0E0E0)
                                    ),
                                    shape = MaterialTheme.shapes.medium
                                )
                                
                                DropdownMenu(
                                    expanded = unitMenuExpanded,
                                    onDismissRequest = { unitMenuExpanded = false }
                                ) {
                                    units.forEach { unitOption ->
                                        DropdownMenuItem(
                                            text = { Text(unitOption) },
                                            onClick = {
                                                unit = unitOption
                                                unitMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            SectionCard(title = stringResource(R.string.additional_info)) {
                FormTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = stringResource(R.string.location_label),
                    placeholder = stringResource(R.string.location_hint)
                )
            }
            
            SectionCard(title = stringResource(R.string.remark)) {
                FormTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = "",
                    placeholder = stringResource(R.string.remark_hint),
                    minLines = 4,
                    maxLines = 6
                )
            }
            
            SectionCard(
                title = stringResource(R.string.custom_fields),
                action = {
                    TextButton(onClick = {}) {
                        Text(
                            text = stringResource(R.string.add_field),
                            color = Color(0xFF2196F3),
                            fontSize = 14.sp
                        )
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.no_custom_fields),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                
                action?.invoke()
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            content()
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    required: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    maxLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null
) {
    // 必填字段为空时显示警告
    val showRequiredWarning = required && value.isBlank()
    val effectiveIsError = isError || showRequiredWarning
    
    Column {
        if (label.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (showRequiredWarning) Color(0xFFFF6B6B) else Color(0xFF757575),
                    fontWeight = if (showRequiredWarning) FontWeight.Medium else FontWeight.Normal
                )
                if (required) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = if (showRequiredWarning) Color(0xFFFFB3B3) else Color(0xFFBDBDBD)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (effectiveIsError) Color.Red else Color(0xFF2196F3),
                unfocusedBorderColor = if (effectiveIsError) Color.Red else if (showRequiredWarning) Color(0xFFFFB3B3) else Color(0xFFE0E0E0),
                focusedContainerColor = if (showRequiredWarning) Color(0xFFFFFAFA) else Color.White,
                unfocusedContainerColor = if (showRequiredWarning) Color(0xFFFFF5F5) else Color(0xFFFAFAFA),
                errorBorderColor = Color.Red,
                errorContainerColor = Color(0xFFFFF5F5)
            ),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            minLines = minLines,
            maxLines = maxLines,
            isError = effectiveIsError,
            supportingText = if (supportingText != null) {
                { Text(text = supportingText, color = Color.Red) }
            } else null
        )
    }
}

data class AddItemFormData(
    val name: String,
    val model: String,
    val brand: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val location: String,
    val remark: String
)

// ==================== Preview 预览 ====================

/**
 * 预览：空表单状态
 */
@Preview(
    name = "空表单",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun AddItemScreenEmptyPreview() {
    MaterialTheme {
        AddItemScreen(
            onBack = {},
            onSave = {}
        )
    }
}

/**
 * 预览：部分填写状态
 */
@Preview(
    name = "部分填写",
    showBackground = true,
    backgroundColor = 0xFFF5F5F5
)
@Composable
private fun AddItemScreenPartialPreview() {
    MaterialTheme {
        AddItemScreen(
            onBack = {},
            onSave = {}
        )
    }
}

/**
 * 预览：深色模式
 */
@Preview(
    name = "深色模式",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun AddItemScreenDarkPreview() {
    MaterialTheme {
        AddItemScreen(
            onBack = {},
            onSave = {}
        )
    }
}
