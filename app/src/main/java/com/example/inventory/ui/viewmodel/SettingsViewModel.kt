package com.example.inventory.ui.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.repository.AuthRepository
import com.example.inventory.data.repository.ExportRepository
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.data.repository.S3Config
import com.example.inventory.data.repository.StorageRepository
import com.example.inventory.data.repository.ConflictResolution
import com.example.inventory.data.repository.SyncConflictItem
import com.example.inventory.data.repository.SyncRepository
import com.example.inventory.domain.util.mapToAppException
import com.example.inventory.ui.state.ErrorState
import com.example.inventory.ui.state.ExportState
import com.example.inventory.ui.state.RestoreState
import com.example.inventory.ui.state.SettingsUiState
import com.example.inventory.ui.state.UserRole
import com.example.inventory.ui.state.UserRoleEntry
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.SecurePreferencesManager
import com.example.inventory.util.settingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 设置页面 ViewModel
 * 
 * 继承 AndroidViewModel 以避免内存泄漏
 * 使用 getApplication() 获取 Application Context，而不是持有 Activity Context
 */
class SettingsViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val exportRepository: ExportRepository,
    private val storageRepository: StorageRepository,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository
) : AndroidViewModel(application) {
    private val s3Prefs by lazy { SecurePreferencesManager.getInstance(getApplication()) }
    private val settingsDataStore by lazy { getApplication<Application>().settingsDataStore }
    private val _state = MutableStateFlow(
        SettingsUiState(
            s3Config = loadS3Config(),
            backupDir = s3Prefs.getString(PrefsKeys.KEY_BACKUP_DIR, "") ?: "",
            autoSync = s3Prefs.getBoolean(PrefsKeys.KEY_AUTO_SYNC, false),
            syncInterval = s3Prefs.getString(PrefsKeys.KEY_SYNC_INTERVAL, "30 min") ?: "30 min",
            ocrBackend = s3Prefs.getString(PrefsKeys.KEY_OCR_BACKEND, com.example.inventory.util.Constants.Ocr.BACKEND_AUTO)
                ?: com.example.inventory.util.Constants.Ocr.BACKEND_AUTO
        )
    )
    val state: StateFlow<SettingsUiState> = _state

    init {
        refreshUsers()
        refreshSyncStatus()
        refreshSyncConflicts()
        loadFontScale()
        loadDarkMode()
        loadLanguage()
        loadOcrBackend()
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.edit { prefs ->
                prefs[PrefsKeys.DARK_MODE_PREF_KEY] = enabled
            }
        }
        _state.update { it.copy(darkMode = enabled) }
    }

    private fun loadDarkMode() {
        viewModelScope.launch {
            val darkMode = settingsDataStore.data.first()[PrefsKeys.DARK_MODE_PREF_KEY] ?: false
            _state.update { it.copy(darkMode = darkMode) }
        }
    }

    private fun loadLanguage() {
        viewModelScope.launch {
            val languageTag = settingsDataStore.data.first()[PrefsKeys.LANGUAGE_PREF_KEY] ?: "zh"
            _state.update { it.copy(languageTag = languageTag) }
        }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch {
            settingsDataStore.edit { prefs ->
                prefs[PrefsKeys.FONT_SCALE_PREF_KEY] = scale
            }
        }
        _state.update { it.copy(fontScale = scale) }
    }

    private fun loadFontScale() {
        viewModelScope.launch {
            val scale = settingsDataStore.data.first()[PrefsKeys.FONT_SCALE_PREF_KEY] ?: 1.0f
            _state.update { it.copy(fontScale = scale) }
        }
    }

    fun updateLanguage(tag: String) {
        viewModelScope.launch {
            settingsDataStore.edit { prefs ->
                prefs[PrefsKeys.LANGUAGE_PREF_KEY] = tag
            }
        }
        
        // 使用 AndroidX 的语言切换 API（Android 13+）
        val locale = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locale)
        
        _state.update { it.copy(languageTag = tag) }
    }

    fun updateS3Config(config: S3Config) {
        _state.update { it.copy(s3Config = config) }
        persistS3Config(config)
    }

    fun updateBackupDir(dir: String) {
        _state.update { it.copy(backupDir = dir) }
        s3Prefs.edit().putString(PrefsKeys.KEY_BACKUP_DIR, dir).apply()
    }

    fun updateAutoSync(enabled: Boolean) {
        _state.update { it.copy(autoSync = enabled) }
        s3Prefs.edit().putBoolean(PrefsKeys.KEY_AUTO_SYNC, enabled).apply()
    }

    fun updateSyncInterval(interval: String) {
        _state.update { it.copy(syncInterval = interval) }
        s3Prefs.edit().putString(PrefsKeys.KEY_SYNC_INTERVAL, interval).apply()
    }

    fun updateOcrBackend(mode: String) {
        _state.update { it.copy(ocrBackend = mode) }
        // 统一使用 DataStore 存储 OCR 后端设置，移除重复的 SharedPreferences 写入
        viewModelScope.launch {
            settingsDataStore.edit { prefs ->
                prefs[PrefsKeys.OCR_BACKEND_PREF_KEY] = mode
            }
        }
    }

    private fun loadOcrBackend() {
        viewModelScope.launch {
            val fromStore = settingsDataStore.data.first()[PrefsKeys.OCR_BACKEND_PREF_KEY]
            val mode = fromStore ?: s3Prefs.getString(PrefsKeys.KEY_OCR_BACKEND, com.example.inventory.util.Constants.Ocr.BACKEND_AUTO)
            _state.update { it.copy(ocrBackend = mode ?: com.example.inventory.util.Constants.Ocr.BACKEND_AUTO) }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _state.update { it.copy(exportState = ExportState.Exporting(0, 0, "正在导出")) }
            try {
                val items = inventoryRepository.getAllItemsSnapshot()
                val file = exportRepository.exportCsv(items)
                _state.update {
                    it.copy(
                        lastExportPath = file.absolutePath,
                        exportState = ExportState.Success(file.absolutePath)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(exportState = ExportState.Error(errorMessage(e, "导出失败")))
                }
            }
        }
    }

    fun exportXlsx() {
        viewModelScope.launch {
            _state.update { it.copy(exportState = ExportState.Exporting(0, 0, "正在导出")) }
            try {
                val items = inventoryRepository.getAllItemsSnapshot()
                val file = exportRepository.exportXlsx(items)
                _state.update {
                    it.copy(
                        lastExportPath = file.absolutePath,
                        exportState = ExportState.Success(file.absolutePath)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(exportState = ExportState.Error(errorMessage(e, "导出失败")))
                }
            }
        }
    }

    fun backupToS3() {
        viewModelScope.launch {
            val key: String
            try {
                val backup = exportRepository.backupDatabase()
                if (backup == null) {
                    throw IllegalStateException("未找到可备份数据库文件")
                }
                val uploadedKey = storageRepository.uploadBackup(backup, _state.value.s3Config)
                if (uploadedKey.isNullOrBlank()) {
                    throw IllegalStateException("上传返回空Key")
                }
                key = uploadedKey
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("上传失败", errorMessage(e, "未知错误"))
                return@launch
            }
            _state.update { it.copy(lastBackupKey = key, s3ErrorMessage = "", showS3ErrorDialog = false) }
        }
    }

    fun restoreFromS3() {
        viewModelScope.launch {
            _state.update { it.copy(restoreState = RestoreState.Restoring(0, 0, "正在恢复")) }
            val key: String
            val success: Boolean
            try {
                val currentKey = _state.value.lastBackupKey
                if (currentKey.isBlank()) {
                    throw IllegalStateException("没有可用的备份Key")
                }
                val file = storageRepository.downloadBackup(currentKey, _state.value.s3Config)
                if (file == null) {
                    throw IllegalStateException("恢复文件失败")
                }
                success = exportRepository.restoreDatabase(file)
                key = currentKey
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = errorMessage(e, "未知错误")
                _state.update { it.copy(restoreStatus = "失败", restoreState = RestoreState.Error(message)) }
                showS3Error("恢复失败", message)
                return@launch
            }
            if (success) {
                _state.update { it.copy(restoreStatus = "成功", restoreState = RestoreState.Success(key)) }
            } else {
                _state.update { it.copy(restoreStatus = "失败", restoreState = RestoreState.Error("恢复文件失败")) }
                showS3Error("S3访问失败", "恢复文件失败")
            }
        }
    }

    fun dismissS3Error() {
        _state.update { it.copy(showS3ErrorDialog = false) }
    }

    fun showS3Detail() {
        _state.update { it.copy(showS3DetailDialog = true) }
    }

    fun dismissS3Detail() {
        _state.update { it.copy(showS3DetailDialog = false) }
    }

    fun updateUserRole(username: String, role: UserRole) {
        viewModelScope.launch {
            try {
                val success = authRepository.updateUserRole(username, role.name.lowercase())
                if (!success) {
                    showUserError("更新角色失败")
                }
                refreshUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "更新角色失败"))
            }
        }
    }

    fun addUser(username: String, password: String, role: UserRole) {
        viewModelScope.launch {
            try {
                val result = authRepository.createUser(username, password, role.name.lowercase())
                if (!result.success) {
                    showUserError(result.message)
                }
                refreshUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "创建用户失败"))
            }
        }
    }

    fun resetPassword(username: String, newPassword: String) {
        viewModelScope.launch {
            try {
                val result = authRepository.resetPassword(username, newPassword)
                if (!result.success) {
                    showUserError(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "重置密码失败"))
            }
        }
    }

    fun deleteUser(username: String) {
        viewModelScope.launch {
            try {
                val result = authRepository.deleteUser(username)
                if (!result.success) {
                    showUserError(result.message)
                }
                refreshUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "删除用户失败"))
            }
        }
    }

    fun unlockAccount(username: String) {
        viewModelScope.launch {
            try {
                val success = authRepository.unlockAccount(username)
                if (!success) {
                    showUserError("未找到用户或账户未锁定")
                }
                refreshUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "解锁失败"))
            }
        }
    }

    fun syncPush() {
        viewModelScope.launch {
            try {
                syncRepository.pushOperations()
                refreshSyncStatus()
                refreshSyncConflicts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("同步失败", errorMessage(e, "未知错误"))
            }
        }
    }

    fun syncPull() {
        viewModelScope.launch {
            try {
                syncRepository.pullOperations()
                refreshSyncStatus()
                refreshSyncConflicts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("同步失败", errorMessage(e, "未知错误"))
            }
        }
    }

    fun syncMerge() {
        viewModelScope.launch {
            try {
                syncRepository.mergeOperations()
                refreshSyncStatus()
                refreshSyncConflicts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("同步失败", errorMessage(e, "未知错误"))
            }
        }
    }

    fun refreshSyncConflicts() {
        viewModelScope.launch {
            try {
                val conflicts = syncRepository.getConflicts()
                _state.update { it.copy(syncConflicts = conflicts) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("获取冲突失败", errorMessage(e, "未知错误"))
            }
        }
    }

    fun resolveConflict(conflict: SyncConflictItem, resolution: ConflictResolution) {
        viewModelScope.launch {
            try {
                val success = syncRepository.resolveConflict(conflict, resolution)
                if (!success) {
                    showS3Error("解决冲突失败", "操作未完成")
                    return@launch
                }
                refreshSyncStatus()
                refreshSyncConflicts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("解决冲突失败", errorMessage(e, "未知错误"))
            }
        }
    }

    private fun showS3Error(message: String, detail: String) {
        _state.update {
            it.copy(
                s3ErrorMessage = message,
                s3ErrorDetail = detail,
                showS3ErrorDialog = true,
                showS3DetailDialog = false
            )
        }
    }

    private fun errorMessage(e: Exception, fallback: String): String {
        return mapToAppException(e).message ?: fallback
    }

    fun dismissUserError() {
        _state.update { it.copy(showUserErrorDialog = false) }
    }

    private fun showUserError(message: String) {
        _state.update {
            it.copy(
                userErrorMessage = message,
                showUserErrorDialog = true
            )
        }
    }

    private fun refreshUsers() {
        viewModelScope.launch {
            try {
                val users = authRepository.getUsers().map { user ->
                    UserRoleEntry(
                        username = user.username,
                        role = if (user.role.lowercase() == "admin") UserRole.Admin else UserRole.User
                    )
                }
                _state.update { it.copy(users = users) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUserError(errorMessage(e, "获取用户失败"))
            }
        }
    }

    private fun refreshSyncStatus() {
        viewModelScope.launch {
            try {
                val status = syncRepository.getSyncStatus()
                _state.update { it.copy(syncStatus = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showS3Error("获取同步状态失败", errorMessage(e, "未知错误"))
            }
        }
    }

    private fun loadS3Config(): S3Config {
        return S3Config(
            endpoint = s3Prefs.getString(PrefsKeys.KEY_S3_ENDPOINT, "") ?: "",
            region = s3Prefs.getString(PrefsKeys.KEY_S3_REGION, "") ?: "",
            bucket = s3Prefs.getString(PrefsKeys.KEY_S3_BUCKET, "") ?: "",
            accessKey = s3Prefs.getString(PrefsKeys.KEY_S3_ACCESS_KEY, "") ?: "",
            secretKey = s3Prefs.getString(PrefsKeys.KEY_S3_SECRET_KEY, "") ?: ""
        )
    }

    private fun persistS3Config(config: S3Config) {
        s3Prefs.edit()
            .putString(PrefsKeys.KEY_S3_ENDPOINT, config.endpoint)
            .putString(PrefsKeys.KEY_S3_REGION, config.region)
            .putString(PrefsKeys.KEY_S3_BUCKET, config.bucket)
            .putString(PrefsKeys.KEY_S3_ACCESS_KEY, config.accessKey)
            .putString(PrefsKeys.KEY_S3_SECRET_KEY, config.secretKey)
            .apply()
    }
}
