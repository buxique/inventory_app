package com.example.inventory.util

import android.util.Log
import com.example.inventory.BuildConfig

/**
 * 统一的应用日志工具类
 * 
 * 功能：
 * - 开发环境：输出详细日志
 * - 生产环境：仅输出错误日志
 * - 支持结构化日志记录
 * - 可扩展到远程日志服务（如Crashlytics）
 */
object AppLogger {
    private const val TAG = "InventoryApp"
    
    // 是否启用调试日志
    private val DEBUG = BuildConfig.DEBUG
    
    /**
     * 调试日志
     */
    fun d(message: String, tag: String = TAG) {
        if (DEBUG) {
            Log.d(tag, message)
        }
    }
    
    /**
     * 信息日志
     */
    fun i(message: String, tag: String = TAG) {
        if (DEBUG) {
            Log.i(tag, message)
        }
    }
    
    /**
     * 警告日志
     */
    fun w(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (DEBUG) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }
    
    /**
     * 错误日志（生产环境也会输出）
     */
    fun e(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    // ==================== 业务日志 ====================
    
    /**
     * 同步操作日志
     */
    fun logSync(operation: String, success: Boolean, details: String = "") {
        val status = if (success) "成功" else "失败"
        val message = "同步[$operation]: $status ${if (details.isNotEmpty()) "- $details" else ""}"
        if (success) {
            i(message, "Sync")
        } else {
            e(message, "Sync")
        }
    }
    
    /**
     * 认证操作日志
     */
    fun logAuth(operation: String, username: String, success: Boolean) {
        val status = if (success) "成功" else "失败"
        val maskedUser = if (username.length > 2) {
            "${username.take(1)}***${username.takeLast(1)}"
        } else {
            "***"
        }
        val debugMessage = "认证[$operation]: user=$maskedUser, status=$status"
        val releaseMessage = "认证[$operation]: status=$status"
        if (DEBUG) {
            if (success) {
                i(debugMessage, "Auth")
            } else {
                w(debugMessage, "Auth")
            }
        } else if (!success) {
            e(releaseMessage, "Auth")
        }
    }

    
    /**
     * 数据库操作日志
     */
    fun logDatabase(operation: String, details: String = "") {
        val message = "数据库[$operation] ${if (details.isNotEmpty()) "- $details" else ""}"
        i(message, "Database")
    }
    
    /**
     * OCR识别日志
     */
    fun logOcr(success: Boolean, resultCount: Int, duration: Long) {
        val message = "OCR识别: ${if (success) "成功" else "失败"}, 结果数=$resultCount, 耗时=${duration}ms"
        if (success) {
            i(message, "OCR")
        } else {
            e(message, "OCR")
        }
    }
    
    /**
     * 文件操作日志
     */
    fun logFile(operation: String, fileName: String, success: Boolean) {
        val status = if (success) "成功" else "失败"
        val message = "文件[$operation]: $fileName - $status"
        if (success) {
            i(message, "File")
        } else {
            e(message, "File")
        }
    }
    
    /**
     * 网络请求日志
     */
    fun logNetwork(method: String, url: String, statusCode: Int, duration: Long) {
        val message = "网络[$method]: $url, status=$statusCode, 耗时=${duration}ms"
        d(message, "Network")
    }
    
    /**
     * 性能监控日志
     */
    fun logPerformance(operation: String, duration: Long) {
        val message = "性能[$operation]: 耗时=${duration}ms"
        if (duration > 1000) {
            w(message, "Performance")
        } else {
            d(message, "Performance")
        }
    }
}
