package com.example.inventory.ui.state

import com.example.inventory.data.repository.S3Config
import com.example.inventory.data.repository.SyncConflictItem
import com.example.inventory.data.repository.SyncStatus

/**
 * 设置页面UI状态
 */
data class SettingsUiState(
    val darkMode: Boolean = false,
    val languageTag: String = "system",
    val fontScale: Float = 1.0f,  // 字号缩放比例：0.85=小，1.0=标准，1.15=大，1.3=特大
    val ocrBackend: String = com.example.inventory.util.Constants.Ocr.BACKEND_AUTO,
    val s3Config: S3Config = S3Config(
        endpoint = "",
        region = "",
        bucket = "",
        accessKey = "",
        secretKey = ""
    ),
    val backupDir: String = "",
    val autoSync: Boolean = false,
    val syncInterval: String = "30 min",
    val lastBackupKey: String = "",
    val lastExportPath: String = "",
    val restoreStatus: String = "",
    val s3ErrorMessage: String = "",
    val s3ErrorDetail: String = "",
    val showS3ErrorDialog: Boolean = false,
    val showS3DetailDialog: Boolean = false,
    val userErrorMessage: String = "",
    val showUserErrorDialog: Boolean = false,
    val exportState: ExportState = ExportState.Idle,
    val restoreState: RestoreState = RestoreState.Idle,
    val users: List<UserRoleEntry> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus(),
    val syncConflicts: List<SyncConflictItem> = emptyList(),
    val errorState: ErrorState = ErrorState.None,
    val isOnline: Boolean = true,  // 网络连接状态
    val offlineMode: Boolean = false  // 是否启用离线模式
)

/**
 * 导出状态
 */
sealed class ExportState {
    object Idle : ExportState()
    
    data class Exporting(val progress: Int, val total: Int, val message: String) : ExportState()
    
    data class Success(val path: String) : ExportState()
    
    data class Error(val message: String) : ExportState()
}

/**
 * 恢复状态
 */
sealed class RestoreState {
    object Idle : RestoreState()
    
    data class Restoring(val progress: Int, val total: Int, val message: String) : RestoreState()
    
    data class Success(val backupKey: String) : RestoreState()
    
    data class Error(val message: String) : RestoreState()
}

/**
 * 错误状态 - 使用sealed class管理各种错误
 */
sealed class ErrorState {
    object None : ErrorState()
    
    data class UserError(val message: String) : ErrorState()
    
    data class S3Error(val message: String, val detail: String = "") : ErrorState()
}

data class UserRoleEntry(
    val username: String,
    val role: UserRole
)

enum class UserRole {
    Admin,
    User
}
