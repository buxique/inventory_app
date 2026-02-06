package com.example.inventory.ui.screens.inventory.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.data.model.InventoryListEntity

/**
 * 库存列表下拉菜单
 * 
 * 显示当前列表名称，点击后弹出列表选择菜单
 * 支持内联编辑列表名称
 * 
 * @param lists 所有列表
 * @param currentListId 当前列表ID
 * @param onListSelect 选择列表回调
 * @param onCreateList 创建新列表回调
 * @param onRenameList 重命名列表回调
 * @param onDeleteList 删除列表回调
 * @param modifier 修饰符
 */
@Composable
fun InventoryListDropdown(
    lists: List<InventoryListEntity>,
    currentListId: Long?,
    onListSelect: (Long) -> Unit,
    onCreateList: () -> Unit,
    onRenameList: (Long, String) -> Unit,
    onDeleteList: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var editingListId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }
    
    Box(modifier = modifier) {
        // 当前列表名称 + 下拉图标
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lists.find { it.id == currentListId }?.name ?: "未选择",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "选择列表"
            )
        }
        
        // 下拉菜单
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { 
                expanded = false
                editingListId = null
            }
        ) {
            // 列表选项
            lists.forEach { list ->
                if (editingListId == list.id) {
                    // 编辑模式
                    EditableListItem(
                        name = editingName,
                        onNameChange = { editingName = it },
                        onSave = {
                            if (editingName.isNotBlank()) {
                                onRenameList(list.id, editingName)
                                editingListId = null
                            }
                        },
                        onCancel = {
                            editingListId = null
                        }
                    )
                } else {
                    // 显示模式
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(list.name)
                                if (list.id == currentListId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "当前列表",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        onClick = {
                            // 如果点击的是当前列表，进入编辑模式
                            if (list.id == currentListId) {
                                editingListId = list.id
                                editingName = list.name
                            } else {
                                // 否则切换列表
                                onListSelect(list.id)
                                expanded = false
                            }
                        }
                    )
                }
            }
            
            HorizontalDivider()
            
            // 新建仓库
            DropdownMenuItem(
                text = { Text("+ 新建仓库") },
                onClick = {
                    onCreateList()
                    expanded = false
                }
            )
        }
    }
}

/**
 * 可编辑的列表项
 * 
 * 显示文本输入框和保存/取消按钮
 * 
 * @param name 当前名称
 * @param onNameChange 名称变化回调
 * @param onSave 保存回调
 * @param onCancel 取消回调
 */
@Composable
private fun EditableListItem(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 自动聚焦并显示键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 文本输入框
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = "输入仓库名称",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // 保存和取消按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // 取消按钮
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    onCancel()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 保存按钮
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    onSave()
                },
                enabled = name.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "保存",
                    tint = if (name.isNotBlank()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

