package com.example.inventory.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.R
import com.example.inventory.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * 设置页面
 * 
 * 按照新设计重新实现，包含：
 * - 语言首选项
 * - S3存储设置
 * - 字体调节
 * - 预留选项1-4
 * - 帮助部分（使用指南、常见问题、联系支持）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showS3Config by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.menu_back)
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LanguagePreferenceSection(
                languageTag = state.languageTag,
                onLanguageChange = viewModel::updateLanguage
            )
            
            // S3 存储设置
            S3ConfigSection(
                visible = showS3Config,
                config = state.s3Config,
                backupDir = state.backupDir,
                autoSync = state.autoSync,
                syncInterval = state.syncInterval,
                onToggleVisibility = { showS3Config = it },
                onConfigChange = { viewModel.updateS3Config(it) },
                onBackupDirChange = { viewModel.updateBackupDir(it) },
                onAutoSyncChange = { viewModel.updateAutoSync(it) },
                onSyncIntervalChange = { viewModel.updateSyncInterval(it) }
            )
            
            FontSizeSection(
                fontScale = state.fontScale,
                onFontScaleChange = viewModel::updateFontScale
            )
            
            OcrBackendSection(
                selected = state.ocrBackend,
                onChange = viewModel::updateOcrBackend
            )
            
            // 预留选项 2
            PlaceholderSection(title = stringResource(R.string.settings_placeholder_title, 2))
            
            // 预留选项 3
            PlaceholderSection(title = stringResource(R.string.settings_placeholder_title, 3))
            
            // 预留选项 4
            PlaceholderSection(title = stringResource(R.string.settings_placeholder_title, 4))
            
            // 帮助部分
            HelpSection()
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguagePreferenceSection(
    languageTag: String,
    onLanguageChange: (String) -> Unit
) {
    SectionCard(
        header = {
            SectionTitle(text = stringResource(R.string.settings_language_preferences))
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LanguageButton(
                text = stringResource(R.string.language_zh),
                selected = languageTag == "zh",
                onClick = { onLanguageChange("zh") },
                modifier = Modifier.weight(1f)
            )
            LanguageButton(
                text = stringResource(R.string.language_en),
                selected = languageTag == "en",
                onClick = { onLanguageChange("en") },
                modifier = Modifier.weight(1f)
            )
            LanguageButton(
                text = stringResource(R.string.language_ja),
                selected = languageTag == "ja",
                onClick = { onLanguageChange("ja") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FontSizeSection(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit
) {
    SectionCard(
        header = {
            SectionTitle(text = stringResource(R.string.settings_font_size_adjust))
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A",
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )

                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChange,
                    valueRange = 0.85f..1.3f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF2196F3),
                        activeTrackColor = Color(0xFF2196F3),
                        inactiveTrackColor = Color(0xFFE0E0E0)
                    )
                )

                Text(
                    text = "A",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
            }

            Text(
                text = stringResource(
                    R.string.settings_font_size_current,
                    (fontScale * 16).roundToInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF757575),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun OcrBackendSection(
    selected: String,
    onChange: (String) -> Unit
) {
    SectionCard(
        header = {
            SectionTitle(text = stringResource(R.string.settings_ocr_engine))
        }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ToggleButton(
                text = stringResource(R.string.settings_ocr_engine_auto),
                selected = selected == com.example.inventory.util.Constants.Ocr.BACKEND_AUTO,
                onClick = { onChange(com.example.inventory.util.Constants.Ocr.BACKEND_AUTO) }
            )
            ToggleButton(
                text = stringResource(R.string.settings_ocr_engine_onnx),
                selected = selected == com.example.inventory.util.Constants.Ocr.BACKEND_ONNX,
                onClick = { onChange(com.example.inventory.util.Constants.Ocr.BACKEND_ONNX) }
            )
            ToggleButton(
                text = stringResource(R.string.settings_ocr_engine_paddle),
                selected = selected == com.example.inventory.util.Constants.Ocr.BACKEND_PADDLE,
                onClick = { onChange(com.example.inventory.util.Constants.Ocr.BACKEND_PADDLE) }
            )
            ToggleButton(
                text = stringResource(R.string.settings_ocr_engine_openocr),
                selected = selected == com.example.inventory.util.Constants.Ocr.BACKEND_OPENOCR,
                onClick = { onChange(com.example.inventory.util.Constants.Ocr.BACKEND_OPENOCR) }
            )
        }
    }
}

/**
 * 预留选项区块
 */
@Composable
private fun PlaceholderSection(title: String) {
    SectionCard(
        verticalSpacing = 12.dp,
        header = { SectionTitle(text = title) }
    ) {
        Text(
            text = stringResource(R.string.settings_placeholder_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9E9E9E),
            fontSize = 14.sp
        )
    }
}

/**
 * 帮助部分
 */
@Composable
private fun HelpSection() {
    SectionCard(
        header = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,  // 修复：使用帮助图标而非返回箭头
                    contentDescription = stringResource(R.string.settings_help),
                    tint = Color(0xFF2196F3)
                )
                SectionTitle(text = stringResource(R.string.settings_help))
            }
        }
    ) {
        HelpItem(
            title = stringResource(R.string.settings_help_guide_title),
            subtitle = stringResource(R.string.settings_help_guide_subtitle)
        )

        HelpItem(
            title = stringResource(R.string.settings_help_faq_title),
            subtitle = stringResource(R.string.settings_help_faq_subtitle)
        )

        HelpItem(
            title = stringResource(R.string.settings_help_support_title),
            subtitle = stringResource(R.string.settings_help_support_subtitle)
        )
    }
}

/**
 * 帮助项组件
 */
@Composable
private fun HelpItem(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121),
            fontSize = 16.sp
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF757575),
            fontSize = 14.sp
        )
    }
}

/**
 * 语言按钮组件
 */
@Composable
private fun LanguageButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),  // 绿色
                contentColor = Color.White
            )
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF212121)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color(0xFFE0E0E0)
            )
        ) {
            Text(
                text = text,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 切换按钮组件（隐藏/显示）
 */
@Composable
private fun ToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .width(60.dp)
                .height(32.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE0E0E0),
                contentColor = Color(0xFF212121)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .width(60.dp)
                .height(32.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF757575)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color(0xFFE0E0E0)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * S3 存储配置区块
 */
@Composable
private fun S3ConfigSection(
    visible: Boolean,
    config: com.example.inventory.data.repository.S3Config,
    backupDir: String,
    autoSync: Boolean,
    syncInterval: String,
    onToggleVisibility: (Boolean) -> Unit,
    onConfigChange: (com.example.inventory.data.repository.S3Config) -> Unit,
    onBackupDirChange: (String) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncIntervalChange: (String) -> Unit
) {
    var endpoint by remember(config) { mutableStateOf(config.endpoint) }
    var region by remember(config) { mutableStateOf(config.region) }
    var bucket by remember(config) { mutableStateOf(config.bucket) }
    var accessKey by remember(config) { mutableStateOf(config.accessKey) }
    var secretKey by remember(config) { mutableStateOf(config.secretKey) }
    
    var currentBackupDir by remember(backupDir) { mutableStateOf(backupDir) }
    var currentAutoSync by remember(autoSync) { mutableStateOf(autoSync) }
    var currentSyncInterval by remember(syncInterval) { mutableStateOf(syncInterval) }
    
    var customInterval by remember { mutableStateOf("") }
    var showIntervalMenu by remember { mutableStateOf(false) }
    
    val defaultInterval = stringResource(R.string.settings_sync_interval_30_min)
    val customIntervalLabel = stringResource(R.string.settings_sync_interval_custom)
    val minutesLabel = stringResource(R.string.settings_sync_interval_minutes)
    
    val intervals = listOf(
        defaultInterval,
        stringResource(R.string.settings_sync_interval_1_hour),
        stringResource(R.string.settings_sync_interval_2_hours),
        stringResource(R.string.settings_sync_interval_6_hours),
        customIntervalLabel
    )
    
    SectionCard(
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(text = stringResource(R.string.settings_s3_title))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToggleButton(
                        text = stringResource(R.string.settings_toggle_hide),
                        selected = !visible,
                        onClick = { onToggleVisibility(false) }
                    )
                    ToggleButton(
                        text = stringResource(R.string.settings_toggle_show),
                        selected = visible,
                        onClick = { onToggleVisibility(true) }
                    )
                }
            }
        }
    ) {
        if (visible) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                S3ConfigField(
                    label = stringResource(R.string.settings_s3_api_address),
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    placeholder = stringResource(R.string.settings_s3_api_address_hint)
                )

                S3ConfigField(
                    label = stringResource(R.string.s3_region),
                    value = region,
                    onValueChange = { region = it },
                    placeholder = stringResource(R.string.settings_s3_region_hint)
                )

                S3ConfigField(
                    label = stringResource(R.string.s3_bucket),
                    value = bucket,
                    onValueChange = { bucket = it },
                    placeholder = stringResource(R.string.settings_s3_bucket_hint)
                )

                S3ConfigField(
                    label = stringResource(R.string.settings_s3_access_key_id),
                    value = accessKey,
                    onValueChange = { accessKey = it },
                    placeholder = stringResource(R.string.settings_s3_access_key_id_hint)
                )

                S3ConfigField(
                    label = stringResource(R.string.settings_s3_secret_access_key),
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    placeholder = stringResource(R.string.settings_s3_secret_access_key_hint),
                    isPassword = true
                )

                S3ConfigField(
                    label = stringResource(R.string.settings_s3_backup_dir),
                    value = currentBackupDir,
                    onValueChange = { currentBackupDir = it },
                    placeholder = stringResource(R.string.settings_s3_backup_dir_hint)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_auto_sync),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF212121),
                        fontSize = 16.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyncToggleButton(
                            text = stringResource(R.string.settings_sync_off),
                            selected = !currentAutoSync,
                            onClick = { currentAutoSync = false },
                            isActiveStyle = false
                        )
                        SyncToggleButton(
                            text = stringResource(R.string.settings_sync_on),
                            selected = currentAutoSync,
                            onClick = { currentAutoSync = true },
                            isActiveStyle = true
                        )
                    }
                }

                if (currentAutoSync) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_sync_interval),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575),
                            fontSize = 14.sp
                        )

                        Box {
                            OutlinedTextField(
                                value = if (currentSyncInterval == customIntervalLabel) "$customInterval $minutesLabel" else currentSyncInterval,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { showIntervalMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = stringResource(R.string.settings_sync_interval_choose)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFAFAFA)
                                ),
                                shape = MaterialTheme.shapes.medium
                            )

                            DropdownMenu(
                                expanded = showIntervalMenu,
                                onDismissRequest = { showIntervalMenu = false }
                            ) {
                                intervals.forEach { interval ->
                                    DropdownMenuItem(
                                        text = { Text(interval) },
                                        onClick = {
                                            currentSyncInterval = interval
                                            showIntervalMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (currentSyncInterval == customIntervalLabel) {
                            OutlinedTextField(
                                value = customInterval,
                                onValueChange = { customInterval = it.filter { char -> char.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.settings_sync_interval_input_hint),
                                        color = Color(0xFFBDBDBD)
                                    )
                                },
                                suffix = {
                                    Text(
                                        text = stringResource(R.string.settings_sync_interval_minutes),
                                        color = Color(0xFF757575)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFAFAFA)
                                ),
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            endpoint = config.endpoint
                            region = config.region
                            bucket = config.bucket
                            accessKey = config.accessKey
                            secretKey = config.secretKey

                            currentBackupDir = backupDir
                            currentAutoSync = autoSync
                            currentSyncInterval = syncInterval
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF757575)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFFE0E0E0)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp
                        )
                    }

                    Button(
                        onClick = {
                            onConfigChange(
                                com.example.inventory.data.repository.S3Config(
                                    endpoint = endpoint,
                                    region = region,
                                    bucket = bucket,
                                    accessKey = accessKey,
                                    secretKey = secretKey
                                )
                            )
                            onBackupDirChange(currentBackupDir)
                            onAutoSyncChange(currentAutoSync)
                            onSyncIntervalChange(
                                if (currentSyncInterval == customIntervalLabel) customInterval
                                else currentSyncInterval
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    padding: androidx.compose.ui.unit.Dp = 20.dp,
    verticalSpacing: androidx.compose.ui.unit.Dp = 16.dp,
    header: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
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
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            header?.invoke()
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF212121),
        fontSize = 18.sp
    )
}

/**
 * S3 配置字段组件
 */
@Composable
private fun S3ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF212121),
            fontSize = 14.sp
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color(0xFFBDBDBD),
                    fontSize = 14.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color(0xFFFAFAFA)
            ),
            shape = MaterialTheme.shapes.medium,
            visualTransformation = if (isPassword) 
                androidx.compose.ui.text.input.PasswordVisualTransformation() 
            else 
                androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}

/**
 * 同步开关按钮组件
 */
@Composable
private fun SyncToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    isActiveStyle: Boolean
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .width(70.dp)
                .height(36.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActiveStyle) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                contentColor = if (isActiveStyle) Color.White else Color(0xFF212121)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .width(70.dp)
                .height(36.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF757575)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = Color(0xFFE0E0E0)
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp
            )
        }
    }
}

// ==================== Preview 预览 ====================

/**
 * 预览：设置屏幕（简化版）
 * 注意：由于依赖 ViewModel，此预览仅作为布局参考
 */
@Preview(
    name = "设置屏幕",
    showBackground = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
private fun SettingsScreenPreview() {
    // 注意：此预览无法完全展示功能，因为需要 ViewModel
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.settings_preview_note),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
