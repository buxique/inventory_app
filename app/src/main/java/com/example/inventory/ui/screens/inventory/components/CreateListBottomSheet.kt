package com.example.inventory.ui.screens.inventory.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 创建列表底部弹窗
 * 
 * 用于创建新的库存列表，支持手动创建或导入文件
 * 
 * @param visible 是否显示
 * @param onDismiss 关闭回调
 * @param onManualCreate 手动创建回调
 * @param onImportExcel 导入Excel回调
 * @param onImportAccess 导入Access回调
 * @param onImportDatabase 导入数据库回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onManualCreate: () -> Unit,
    onImportExcel: (Uri) -> Unit,
    onImportAccess: (Uri) -> Unit,
    onImportDatabase: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Excel 导入
    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { onImportExcel(it) }
            onDismiss()
        }
    )
    
    // Access 导入
    val accessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { onImportAccess(it) }
            onDismiss()
        }
    )
    
    // 数据库导入
    val dbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { onImportDatabase(it) }
            onDismiss()
        }
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "导入或新建库存列表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 手动添加（创建空列表）
            ImportOption(
                icon = Icons.Default.Add,
                title = "手动添加",
                description = "创建空白列表，手动添加商品",
                onClick = {
                    onManualCreate()
                    onDismiss()
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 导入 Excel
            ImportOption(
                icon = Icons.Default.Add,
                title = "导入 Excel",
                description = "从 .xlsx 或 .xls 文件导入",
                onClick = {
                    excelLauncher.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel"
                    ))
                }
            )
            
            // 导入 Access
            ImportOption(
                icon = Icons.Default.Add,
                title = "导入 Access",
                description = "从 .mdb 或 .accdb 文件导入",
                onClick = {
                    accessLauncher.launch(arrayOf(
                        "application/msaccess",
                        "application/x-msaccess"
                    ))
                }
            )
            
            // 导入数据库文件
            ImportOption(
                icon = Icons.Default.Add,
                title = "导入数据库文件",
                description = "从 .db 文件导入",
                onClick = {
                    dbLauncher.launch(arrayOf("*/*"))
                }
            )
            
            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

/**
 * 导入选项组件
 * 
 * @param icon 图标
 * @param title 标题
 * @param description 描述
 * @param onClick 点击回调
 */
@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
