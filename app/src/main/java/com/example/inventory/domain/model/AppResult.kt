package com.example.inventory.domain.model

/**
 * 应用统一结果封装
 * 
 * 用于统一处理成功、失败和加载状态
 */
sealed class AppResult<out T> {
    /**
     * 成功状态
     */
    data class Success<T>(val data: T) : AppResult<T>()
    
    /**
     * 错误状态
     */
    data class Error(val exception: AppException) : AppResult<Nothing>()
    
    /**
     * 加载中状态
     */
    object Loading : AppResult<Nothing>()
}

/**
 * 自定义异常类型
 * 
 * 用于区分不同类型的错误
 */
sealed class AppException(message: String) : Exception(message) {
    /**
     * 网络异常
     */
    class NetworkException(message: String = "网络连接失败") : AppException(message)
    
    /**
     * 数据库异常
     */
    class DatabaseException(message: String = "数据库操作失败") : AppException(message)
    
    /**
     * 验证异常
     */
    class ValidationException(message: String) : AppException(message)
    
    /**
     * 认证异常
     */
    class AuthException(message: String = "认证失败") : AppException(message)
    
    /**
     * 权限异常
     */
    class PermissionException(message: String = "权限不足") : AppException(message)
    
    /**
     * 未知异常
     */
    class UnknownException(message: String = "未知错误") : AppException(message)
}
