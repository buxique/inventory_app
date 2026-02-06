package com.example.inventory.util

import com.example.inventory.InventoryApplication
import com.example.inventory.R

/**
 * 错误提示工具类
 * 
 * 统一管理应用中的错误提示文案，提供更友好的用户体验
 * 已迁移至 strings.xml 实现国际化
 */
object ErrorMessages {
    
    private val context get() = InventoryApplication.INSTANCE

    /**
     * 网络相关错误
     */
    object Network {
        val TIMEOUT get() = context.getString(R.string.error_network_timeout)
        val UNAVAILABLE get() = context.getString(R.string.error_network_unavailable)
        val SERVER_ERROR get() = context.getString(R.string.error_network_server_error)
        val NO_INTERNET get() = context.getString(R.string.error_network_no_internet)
        
        fun unknownError(code: Int): String = context.getString(R.string.error_network_unknown, code)
    }
    
    /**
     * 数据库相关错误
     */
    object Database {
        val BACKUP_FAILED get() = context.getString(R.string.error_db_backup_failed)
        val RESTORE_FAILED get() = context.getString(R.string.error_db_restore_failed)
        val RESTORE_INVALID get() = context.getString(R.string.error_db_restore_invalid)
        val RESTORE_NOT_FOUND get() = context.getString(R.string.error_db_restore_not_found)
        val QUERY_FAILED get() = context.getString(R.string.error_db_query_failed)
        val SAVE_FAILED get() = context.getString(R.string.error_db_save_failed)
        val DELETE_FAILED get() = context.getString(R.string.error_db_delete_failed)
        val MIGRATION_FAILED get() = context.getString(R.string.error_db_migration_failed)
    }
    
    /**
     * 文件操作错误
     */
    object File {
        val NOT_FOUND get() = context.getString(R.string.error_file_not_found)
        val READ_FAILED get() = context.getString(R.string.error_file_read_failed)
        val WRITE_FAILED get() = context.getString(R.string.error_file_write_failed)
        val TOO_LARGE get() = context.getString(R.string.error_file_too_large)
        val INVALID_FORMAT get() = context.getString(R.string.error_file_invalid_format)
        val PERMISSION_DENIED get() = context.getString(R.string.error_file_permission_denied)
        val STORAGE_FULL get() = context.getString(R.string.error_file_storage_full)
    }
    
    /**
     * 导入导出错误
     */
    object ImportExport {
        val IMPORT_FAILED get() = context.getString(R.string.error_import_failed)
        val EXPORT_FAILED get() = context.getString(R.string.error_export_failed)
        val TOO_MANY_ROWS get() = context.getString(R.string.error_import_too_many_rows)
        val EMPTY_FILE get() = context.getString(R.string.error_import_empty_file)
        val INVALID_CSV get() = context.getString(R.string.error_import_invalid_csv)
        val INVALID_EXCEL get() = context.getString(R.string.error_import_invalid_excel)
        val INVALID_ACCESS get() = context.getString(R.string.error_import_invalid_access)
        
        fun progressMessage(current: Int, total: Int, type: String): String {
            val percentage = if (total > 0) (current * 100) / total else 0
            return context.getString(R.string.msg_progress, type, percentage, current, total)
        }
    }
    
    /**
     * OCR识别错误
     */
    object Ocr {
        val RECOGNITION_FAILED get() = context.getString(R.string.error_ocr_failed)
        val IMAGE_TOO_LARGE get() = context.getString(R.string.error_ocr_image_too_large)
        val IMAGE_INVALID get() = context.getString(R.string.error_ocr_image_invalid)
        val NO_TEXT_DETECTED get() = context.getString(R.string.error_ocr_no_text)
        val MODEL_NOT_FOUND get() = context.getString(R.string.error_ocr_model_missing)
        val PROCESSING get() = context.getString(R.string.msg_ocr_processing)
    }
    
    /**
     * 认证相关错误
     */
    object Auth {
        val LOGIN_FAILED get() = context.getString(R.string.error_login_failed)
        val PASSWORD_TOO_SHORT get() = context.getString(R.string.error_password_too_short)
        val PASSWORD_WEAK get() = context.getString(R.string.error_password_weak)
        val PASSWORD_WITH_SPACE get() = context.getString(R.string.error_password_space)
        val PASSWORD_WITH_USERNAME get() = context.getString(R.string.error_password_username)
        val USERNAME_EMPTY get() = context.getString(R.string.error_username_empty)
        val PASSWORD_EMPTY get() = context.getString(R.string.error_password_empty)
        val LOGOUT_FAILED get() = context.getString(R.string.error_logout_failed)
        
        fun passwordRequirements(): String = context.getString(R.string.msg_password_requirements)
    }
    
    /**
     * S3同步错误
     */
    object S3 {
        val UPLOAD_FAILED get() = context.getString(R.string.error_s3_upload_failed)
        val DOWNLOAD_FAILED get() = context.getString(R.string.error_s3_download_failed)
        val AUTH_FAILED get() = context.getString(R.string.error_s3_auth_failed)
        val BUCKET_NOT_FOUND get() = context.getString(R.string.error_s3_bucket_not_found)
        val CONFIG_INVALID get() = context.getString(R.string.error_s3_config_invalid)
        val SYNC_FAILED get() = context.getString(R.string.error_s3_sync_failed)
        val TIMEOUT get() = context.getString(R.string.error_s3_timeout)
        
        fun uploadProgress(current: Long, total: Long): String {
            val mb = total / (1024 * 1024)
            val percentage = if (total > 0) (current * 100) / total else 0
            return context.getString(R.string.msg_s3_upload_progress, percentage, mb)
        }
    }
    
    /**
     * 权限相关错误
     */
    object Permission {
        val CAMERA_DENIED get() = context.getString(R.string.error_perm_camera)
        val STORAGE_DENIED get() = context.getString(R.string.error_perm_storage)
        val NETWORK_DENIED get() = context.getString(R.string.error_perm_network)
        
        fun requestMessage(permission: String): String = context.getString(R.string.msg_perm_request, permission)
    }
    
    /**
     * 表单验证错误
     */
    object Validation {
        val EMPTY_NAME get() = context.getString(R.string.error_val_empty_name)
        val EMPTY_BRAND get() = context.getString(R.string.error_val_empty_brand)
        val EMPTY_MODEL get() = context.getString(R.string.error_val_empty_model)
        val INVALID_QUANTITY get() = context.getString(R.string.error_val_invalid_qty)
        val QUANTITY_TOO_LARGE get() = context.getString(R.string.error_val_qty_too_large)
        val BARCODE_INVALID get() = context.getString(R.string.error_val_barcode_invalid)
        val BARCODE_EXISTS get() = context.getString(R.string.error_val_barcode_exists)
        
        fun fieldRequired(fieldName: String): String = context.getString(R.string.error_val_field_required, fieldName)
        fun fieldTooLong(fieldName: String, maxLength: Int): String = context.getString(R.string.error_val_field_too_long, fieldName, maxLength)
    }
    
    /**
     * 通用错误
     */
    object General {
        val UNKNOWN get() = context.getString(R.string.error_unknown)
        val OPERATION_FAILED get() = context.getString(R.string.error_operation_failed)
        val OPERATION_CANCELLED get() = context.getString(R.string.error_operation_cancelled)
        val TIMEOUT get() = context.getString(R.string.error_timeout)
        val NO_DATA get() = context.getString(R.string.msg_no_data)
        val LOADING get() = context.getString(R.string.msg_loading)
        
        fun unexpectedError(message: String?): String = context.getString(R.string.error_unexpected, message ?: "未知原因")
    }
    
    /**
     * 成功提示
     */
    object Success {
        val SAVED get() = context.getString(R.string.msg_saved)
        val DELETED get() = context.getString(R.string.msg_deleted)
        val IMPORTED get() = context.getString(R.string.msg_imported)
        val EXPORTED get() = context.getString(R.string.msg_exported)
        val BACKUP_CREATED get() = context.getString(R.string.msg_backup_created)
        val RESTORED get() = context.getString(R.string.msg_restored)
        val UPLOADED get() = context.getString(R.string.msg_uploaded)
        val DOWNLOADED get() = context.getString(R.string.msg_downloaded)
        val SYNCED get() = context.getString(R.string.msg_synced)
        
        fun operationSuccess(operation: String): String = context.getString(R.string.msg_operation_success, operation)
    }
    
    /**
     * 确认对话框文案
     */
    object Confirmation {
        val DELETE_ITEM get() = context.getString(R.string.confirm_delete_item)
        val DELETE_MULTIPLE get() = context.getString(R.string.confirm_delete_multiple)
        val CLEAR_ALL get() = context.getString(R.string.confirm_clear_all)
        val RESTORE_DATABASE get() = context.getString(R.string.confirm_restore_db)
        val LOGOUT get() = context.getString(R.string.confirm_logout)
        val DISCARD_CHANGES get() = context.getString(R.string.confirm_discard_changes)
        
        fun deleteConfirm(itemName: String): String = context.getString(R.string.confirm_delete_named, itemName)
    }
}
