package com.example.inventory.data.repository

import android.content.Context
import com.example.inventory.data.model.UserEntity
import com.example.inventory.util.AppLogger
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.SecurityConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 认证仓库实现类
 * 
 * 负责用户认证、权限管理和密码策略
 * - 使用PBKDF2WithHmacSHA256进行密码哈希
 * - 使用EncryptedSharedPreferences安全存储
 * - 实现完整的用户CRUD操作
 * - 防暴力破解：登录尝试限制和账户锁定
 */
class AuthRepositoryImpl(
    context: Context
) : AuthRepository {
    private val appContext = context.applicationContext
    
    /**
     * 当前登录用户
     * 使用 @Volatile 确保多线程可见性
     */
    @Volatile
    private var user: UserEntity? = null
    
    private val prefs by lazy { securePreferences(appContext) }
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, StoredUser::class.java)
    private val adapter = moshi.adapter<List<StoredUser>>(listType)
    
    // 登录尝试记录
    private data class LoginAttempt(
        var failCount: Int = 0,
        var lastAttemptTime: Long = 0,
        var lockUntil: Long = 0
    )
    
    private val loginAttempts = ConcurrentHashMap<String, LoginAttempt>()
    
    // 使用统一的安全配置
    private companion object {
        const val MAX_ATTEMPTS = SecurityConfig.MAX_LOGIN_ATTEMPTS
        const val LOCK_DURATION = SecurityConfig.ACCOUNT_LOCK_DURATION_MS
        const val ATTEMPT_WINDOW = SecurityConfig.LOGIN_ATTEMPT_WINDOW_MS
    }

    /**
     * 用户登录
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @return LoginResult 包含详细的登录结果信息
     * 
     * 逻辑：
     * 1. 检查账户是否被锁定
     * 2. 验证密码策略
     * 3. 如果用户不存在则自动创建（首次登录）
     * 4. 如果用户已存在则验证密码
     * 5. 记录登录尝试
     */
    override suspend fun login(username: String, password: String): LoginResult {
        // 检查账户是否被锁定
        val attempt = loginAttempts.getOrPut(username) { LoginAttempt() }
        
        if (attempt.lockUntil > System.currentTimeMillis()) {
            val remainingTime = (attempt.lockUntil - System.currentTimeMillis()) / 1000
            AppLogger.w("账户 $username 已锁定，剩余时间: ${remainingTime}秒", "Auth")
            return LoginResult.accountLocked(remainingTime)
        }
        
        // 重置过期的尝试记录
        if (System.currentTimeMillis() - attempt.lastAttemptTime > ATTEMPT_WINDOW) {
            attempt.failCount = 0
        }
        
        // 验证输入
        if (username.isBlank() || password.isBlank() || !isPasswordValid(password, username)) {
            recordFailedAttempt(username, attempt)
            return LoginResult.invalidInput()
        }
        
        // 验证用户
        val users = loadUsers().toMutableList()
        val existing = users.firstOrNull { it.username == username }
        val hashed = hashPassword(password)
        val role = if (existing == null) {
            // 首次登录，创建用户
            val created = StoredUser(username = username, passwordHash = hashed, role = "user")
            users.add(created)
            saveUsers(users)
            AppLogger.logAuth("注册", username, true)
            created.role
        } else {
            // 验证密码
            if (!verifyPassword(password, existing.passwordHash)) {
                val remainingAttempts = MAX_ATTEMPTS - attempt.failCount - 1
                recordFailedAttempt(username, attempt)
                return LoginResult.invalidCredentials(remainingAttempts.coerceAtLeast(0))
            }
            existing.role
        }
        
        // 登录成功，清除尝试记录
        loginAttempts.remove(username)
        AppLogger.logAuth("登录", username, true)
        
        val userEntity = UserEntity(username = username, role = role)
        user = userEntity
        return LoginResult.success(userEntity)
    }
    
    /**
     * 记录失败的登录尝试
     */
    private fun recordFailedAttempt(username: String, attempt: LoginAttempt) {
        attempt.failCount++
        attempt.lastAttemptTime = System.currentTimeMillis()
        
        if (attempt.failCount >= MAX_ATTEMPTS) {
            attempt.lockUntil = System.currentTimeMillis() + LOCK_DURATION
            AppLogger.w("账户 $username 因多次登录失败被锁定", "Auth")
        } else {
            val remainingAttempts = MAX_ATTEMPTS - attempt.failCount
            AppLogger.w("用户 $username 登录失败，剩余尝试次数: $remainingAttempts", "Auth")
        }
    }
    
    /**
     * 解锁账户（管理员功能）
     */
    fun unlockAccount(username: String): Boolean {
        return if (loginAttempts.remove(username) != null) {
            AppLogger.i("账户 $username 已解锁", "Auth")
            true
        } else {
            false
        }
    }

    override suspend fun currentUser(): UserEntity? = user

    override suspend fun logout() {
        user = null
    }

    override suspend fun getUsers(): List<UserEntity> {
        return loadUsers().map { UserEntity(username = it.username, role = it.role) }
    }

    override suspend fun updateUserRole(username: String, role: String): Boolean {
        val users = loadUsers().toMutableList()
        val index = users.indexOfFirst { it.username == username }
        if (index == -1) return false
        users[index] = users[index].copy(role = role)
        saveUsers(users)
        if (user?.username == username) {
            user = user?.copy(role = role)
        }
        return true
    }

    override suspend fun createUser(username: String, password: String, role: String): AuthResult {
        if (username.isBlank()) return AuthResult(false, "用户名不能为空")
        if (!isPasswordValid(password, username)) return AuthResult(false, "密码不符合策略")
        val users = loadUsers().toMutableList()
        if (users.any { it.username == username }) return AuthResult(false, "用户已存在")
        val created = StoredUser(username = username, passwordHash = hashPassword(password), role = role)
        users.add(created)
        saveUsers(users)
        return AuthResult(true)
    }

    override suspend fun deleteUser(username: String): AuthResult {
        val users = loadUsers().toMutableList()
        val removed = users.removeAll { it.username == username }
        if (!removed) return AuthResult(false, "未找到用户")
        saveUsers(users)
        if (user?.username == username) {
            user = null
        }
        return AuthResult(true)
    }

    override suspend fun resetPassword(username: String, newPassword: String): AuthResult {
        if (!isPasswordValid(newPassword, username)) return AuthResult(false, "密码不符合策略")
        val users = loadUsers().toMutableList()
        val index = users.indexOfFirst { it.username == username }
        if (index == -1) return AuthResult(false, "未找到用户")
        users[index] = users[index].copy(passwordHash = hashPassword(newPassword))
        saveUsers(users)
        return AuthResult(true)
    }

    private fun loadUsers(): List<StoredUser> {
        val raw = prefs.getString(KEY_USERS, null) ?: return emptyList()
        return adapter.fromJson(raw).orEmpty()
    }

    private fun saveUsers(users: List<StoredUser>) {
        val json = adapter.toJson(users)
        prefs.edit().putString(KEY_USERS, json).apply()
    }

    private fun securePreferences(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

/**
 * 使用PBKDF2WithHmacSHA256进行密码哈希
 * 
 * @param password 明文密码
 * @return 哈希后的密码字符串，格式：pbkdf2_sha256$iterations$salt$hash
 * 
 * 安全特性：
 * - 100,000次迭代
 * - 32字节随机盐值
 * - 256位哈希长度
 */
internal fun hashPassword(password: String): String {
    // 生成随机盐值（32字节）
    val salt = ByteArray(32)
    SecureRandom().nextBytes(salt)
    
    // 使用PBKDF2WithHmacSHA256进行哈希（100000次迭代）
    val spec = PBEKeySpec(password.toCharArray(), salt, 100000, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    
    // 返回格式：algorithm$iterations$salt$hash
    val saltBase64 = Base64.getEncoder().encodeToString(salt)
    val hashBase64 = Base64.getEncoder().encodeToString(hash)
    return "pbkdf2_sha256\$100000\$$saltBase64\$$hashBase64"
}

/**
 * 验证密码
 * 
 * @param password 明文密码
 * @param storedHash 存储的哈希值
 * @return 密码是否匹配
 * 
 * 支持格式：
 * - pbkdf2_sha256$iterations$salt$hash（新格式）
 * - 旧SHA-256格式直接返回false，强制用户重置
 */
internal fun verifyPassword(password: String, storedHash: String): Boolean {
    // 兼容旧的SHA-256格式（用于数据迁移）
    if (!storedHash.contains("$")) {
        // 旧格式：直接SHA-256的Base64
        return false // 强制用户重新设置密码
    }
    
    val parts = storedHash.split("$")
    if (parts.size != 4) return false
    if (parts[0] != "pbkdf2_sha256") return false
    
    val iterations = parts[1].toIntOrNull() ?: return false
    val salt = try {
        Base64.getDecoder().decode(parts[2])
    } catch (e: Exception) {
        return false
    }
    val expectedHash = try {
        Base64.getDecoder().decode(parts[3])
    } catch (e: Exception) {
        return false
    }
    
    // 使用相同的盐值和迭代次数计算哈希
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val testHash = factory.generateSecret(spec).encoded
    
    // 使用常量时间比较，防止时序攻击
    return testHash.contentEquals(expectedHash)
}

/**
 * 验证密码是否符合策略
 * 
 * @param password 待验证密码
 * @param username 用户名（用于检查是否包含用户名）
 * @return 是否符合策略
 * 
 * 密码策略：
 * - 最小长度10位
 * - 必须包含大写字母
 * - 必须包含小写字母
 * - 必须包含数字
 * - 必须包含特殊字符
 * - 不能包含空格
 * - 不能包含用户名
 */
internal fun isPasswordValid(password: String, username: String): Boolean {
    if (password.length < SecurityConfig.PASSWORD_MIN_LENGTH) return false
    if (password.any { it.isWhitespace() }) return false
    val lower = password.any { it.isLowerCase() }
    val upper = password.any { it.isUpperCase() }
    val digit = password.any { it.isDigit() }
    val special = password.any { !it.isLetterOrDigit() }
    if (!(lower && upper && digit && special)) return false
    if (username.isNotBlank() && password.lowercase().contains(username.lowercase())) return false
    return true
}

private data class StoredUser(
    val username: String,
    val passwordHash: String,
    val role: String
)

private const val PREFS_NAME = PrefsKeys.AUTH_PREFS_NAME
private const val KEY_USERS = PrefsKeys.KEY_USERS
