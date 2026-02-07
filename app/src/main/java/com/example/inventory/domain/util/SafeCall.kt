package com.example.inventory.domain.util

import android.database.sqlite.SQLiteException
import com.example.inventory.domain.model.AppException
import com.example.inventory.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * 安全调用工具函数
 * 
 * 自动捕获异常并转换为 AppResult
 */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        AppResult.Error(mapToAppException(e))
    } catch (e: SQLiteException) {
        AppResult.Error(mapToAppException(e))
    } catch (e: IllegalArgumentException) {
        AppResult.Error(mapToAppException(e))
    } catch (e: SecurityException) {
        AppResult.Error(mapToAppException(e))
    } catch (e: AppException) {
        AppResult.Error(mapToAppException(e))
    } catch (e: Exception) {
        AppResult.Error(mapToAppException(e))
    }
}

/**
 * 将异常映射为应用异常类型
 * 
 * @param e 原始异常
 * @return 应用异常
 */
fun mapToAppException(e: Exception): AppException {
    return when (e) {
        is IOException -> AppException.NetworkException(e.message ?: "网络连接失败")
        is SQLiteException -> AppException.DatabaseException(e.message ?: "数据库操作失败")
        is IllegalArgumentException -> AppException.ValidationException(e.message ?: "验证失败")
        is SecurityException -> AppException.AuthException(e.message ?: "认证失败")
        is AppException -> e
        else -> AppException.UnknownException(e.message ?: "未知错误")
    }
}

/**
 * Result 扩展函数：获取数据或返回默认值
 */
fun <T> AppResult<T>.getOrDefault(defaultValue: T): T {
    return when (this) {
        is AppResult.Success -> data
        else -> defaultValue
    }
}

/**
 * Result 扩展函数：获取数据或返回 null
 */
fun <T> AppResult<T>.getOrNull(): T? {
    return when (this) {
        is AppResult.Success -> data
        else -> null
    }
}

/**
 * Result 扩展函数：判断是否成功
 */
fun <T> AppResult<T>.isSuccess(): Boolean {
    return this is AppResult.Success
}

/**
 * Result 扩展函数：判断是否失败
 */
fun <T> AppResult<T>.isError(): Boolean {
    return this is AppResult.Error
}

/**
 * Result 扩展函数：获取错误信息
 */
fun <T> AppResult<T>.getErrorMessage(): String? {
    return when (this) {
        is AppResult.Error -> exception.message
        else -> null
    }
}
