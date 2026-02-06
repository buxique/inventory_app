package com.example.inventory.data.repository

import com.example.inventory.data.model.UserEntity

interface AuthRepository {
    suspend fun login(username: String, password: String): LoginResult
    suspend fun currentUser(): UserEntity?
    suspend fun logout()
    suspend fun getUsers(): List<UserEntity>
    suspend fun updateUserRole(username: String, role: String): Boolean
    suspend fun createUser(username: String, password: String, role: String): AuthResult
    suspend fun deleteUser(username: String): AuthResult
    suspend fun resetPassword(username: String, newPassword: String): AuthResult
    suspend fun unlockAccount(username: String): Boolean
}

/**
 * 登录结果
 * 
 * @param success 是否成功
 * @param user 登录成功时的用户信息
 * @param errorType 错误类型
 * @param message 错误消息
 * @param lockRemainingSeconds 账户锁定剩余秒数（仅当 errorType 为 ACCOUNT_LOCKED 时有效）
 */
data class LoginResult(
    val success: Boolean,
    val user: UserEntity? = null,
    val errorType: LoginErrorType = LoginErrorType.NONE,
    val message: String = "",
    val lockRemainingSeconds: Long = 0
) {
    companion object {
        fun success(user: UserEntity) = LoginResult(
            success = true,
            user = user
        )
        
        fun accountLocked(remainingSeconds: Long) = LoginResult(
            success = false,
            errorType = LoginErrorType.ACCOUNT_LOCKED,
            message = "账户已锁定，剩余时间: ${remainingSeconds}秒",
            lockRemainingSeconds = remainingSeconds
        )
        
        fun invalidCredentials(remainingAttempts: Int) = LoginResult(
            success = false,
            errorType = LoginErrorType.INVALID_CREDENTIALS,
            message = "用户名或密码错误，剩余尝试次数: $remainingAttempts"
        )
        
        fun invalidInput() = LoginResult(
            success = false,
            errorType = LoginErrorType.INVALID_INPUT,
            message = "用户名或密码不符合要求"
        )
    }
}

/**
 * 登录错误类型
 */
enum class LoginErrorType {
    NONE,                   // 无错误
    INVALID_INPUT,          // 输入无效
    INVALID_CREDENTIALS,    // 凭证错误
    ACCOUNT_LOCKED,         // 账户锁定
    UNKNOWN                 // 未知错误
}

data class AuthResult(
    val success: Boolean,
    val message: String = ""
)
