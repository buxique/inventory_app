package com.example.inventory.ui.screens

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.R
import android.net.Uri
import com.example.inventory.data.model.InventoryListEntity
import com.example.inventory.ui.screens.inventory.components.CreateListBottomSheet
import com.example.inventory.ui.viewmodel.InventoryListViewModel
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.settingsDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    listViewModel: InventoryListViewModel,
    onCreateNew: () -> Unit,
    onImportExcel: (Uri) -> Unit,
    onImportAccess: (Uri) -> Unit,
    onImportDatabase: (Uri) -> Unit,
    onSelectList: (Long) -> Unit
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 获取所有列表
    val lists by listViewModel.lists.collectAsStateWithLifecycle()
    
    // 检查是否首次启动（是否已选择过语言）
    val languageFlow = remember(context) {
        context.settingsDataStore.data.map { prefs -> prefs[PrefsKeys.LANGUAGE_PREF_KEY] }
    }
    val languageTag by languageFlow.collectAsStateWithLifecycle(initialValue = null)
    val showLanguageSelector by remember(languageTag) {
        derivedStateOf { languageTag == null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (showLanguageSelector) {
            // 首次启动：显示语言选择界面
            var selectedLanguage by rememberSaveable(languageTag) { mutableStateOf(languageTag ?: "zh") }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp)
            ) {
                Spacer(modifier = Modifier.weight(0.5f))
                
                // 顶部地球图标
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.language),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 标题
                Text(
                    text = stringResource(R.string.select_preferred_language),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // 副标题
                Text(
                    text = stringResource(R.string.language_change_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 语言选项卡片
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 中文选项
                    LanguageOptionCard(
                        title = stringResource(R.string.language_zh),
                        subtitle = stringResource(R.string.language_zh_subtitle),
                        isSelected = selectedLanguage == "zh",
                        onClick = { selectedLanguage = "zh" }
                    )
                    
                    // 英文选项
                    LanguageOptionCard(
                        title = stringResource(R.string.language_en),
                        subtitle = stringResource(R.string.language_en_subtitle),
                        isSelected = selectedLanguage == "en",
                        onClick = { selectedLanguage = "en" }
                    )
                    
                    // 日文选项
                    LanguageOptionCard(
                        title = stringResource(R.string.language_ja),
                        subtitle = stringResource(R.string.language_ja_subtitle),
                        isSelected = selectedLanguage == "ja",
                        onClick = { selectedLanguage = "ja" }
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 确认按钮
                Button(
                    onClick = {
                        scope.launch {
                            saveLanguagePreference(context, selectedLanguage)
                            if (selectedLanguage != "zh") {
                                recreateActivity(context)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // 正常启动页面
            if (lists.isEmpty()) {
                // 暂无库存列表 - 显示创建提示
                EmptyListScreen(onCreateClick = { showSheet = true })
            } else {
                // 有库存列表 - 显示列表选择界面
                ListSelectionScreen(
                    lists = lists,
                    onSelectList = onSelectList,
                    onCreateClick = { showSheet = true }
                )
            }
        }
    }

    CreateListBottomSheet(
        visible = showSheet,
        onDismiss = { showSheet = false },
        onManualCreate = {
            showSheet = false
            onCreateNew()
        },
        onImportExcel = onImportExcel,
        onImportAccess = onImportAccess,
        onImportDatabase = onImportDatabase
    )
}

/**
 * 空列表屏幕 - 暂无库存列表
 */
@Composable
private fun EmptyListScreen(onCreateClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        // 顶部手机图标/插图区域
        Surface(
            modifier = Modifier.size(200.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // 简化的手机图标表示
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 模拟手机屏幕的线条
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 标题
        Text(
            text = stringResource(R.string.empty_inventory_list_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // 提示语
        Text(
            text = stringResource(R.string.empty_inventory_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 底部圆形 + 按钮
        Surface(
            modifier = Modifier
                .size(80.dp)
                .clickable { onCreateClick() },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = stringResource(R.string.action_add),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = 48.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * 列表选择屏幕 - 显示所有库存列表
 */
@Composable
private fun ListSelectionScreen(
    lists: List<InventoryListEntity>,
    onSelectList: (Long) -> Unit,
    onCreateClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        // 标题
        Text(
            text = stringResource(R.string.select_inventory_list),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 副标题
        Text(
            text = stringResource(R.string.select_inventory_list_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // 列表卡片
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            lists.forEach { list ->
                ListItemCard(
                    list = list,
                    onClick = { onSelectList(list.id) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 底部新建按钮
        Button(
            onClick = onCreateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.create_inventory_list_with_icon),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 列表项卡片
 */
@Composable
private fun ListItemCard(
    list: InventoryListEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)  // 增加高度从 80dp 到 90dp
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),  // 增加垂直内边距从 16dp 到 20dp
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)  // 增加间距从 4dp 到 6dp
            ) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    maxLines = 1  // 限制为单行
                )
                Text(
                    text = if (list.isDefault) stringResource(R.string.default_list) else stringResource(R.string.inventory_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1  // 限制为单行
                )
            }
        }
    }
}

/**
 * 语言选项卡片
 * 
 * @param title 主标题
 * @param subtitle 副标题
 * @param isSelected 是否被选中
 * @param onClick 点击回调
 */
@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium
                    )
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 保存语言偏好设置
 * 使用 SecurePreferencesManager 单例避免重复创建
 * 
 * @param context 上下文
 * @param languageCode 语言代码（"zh"、"en" 或 "ja"）
 */
private suspend fun saveLanguagePreference(context: Context, languageCode: String) {
    context.settingsDataStore.edit { prefs ->
        prefs[PrefsKeys.LANGUAGE_PREF_KEY] = languageCode
    }
}

/**
 * 重启 Activity 以应用新语言
 * 
 * @param context 上下文
 */
private fun recreateActivity(context: Context) {
    (context as? Activity)?.recreate()
}
