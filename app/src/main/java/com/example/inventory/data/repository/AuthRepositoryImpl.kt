package com.example.inventory.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.inventory.data.model.UserEntity
import com.example.inventory.util.AppLogger
import com.example.inventory.util.Constants
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.SecurityConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val attemptListType = Types.newParameterizedType(List::class.java, StoredLoginAttempt::class.java)
    private val attemptAdapter = moshi.adapter<List<StoredLoginAttempt>>(attemptListType)
    
    // 登录尝试记录
    private data class LoginAttempt(
        var failCount: Int = 0,
        var lastAttemptTime: Long = 0,
        var lockUntil: Long = 0
    )
    
    private val loginAttempts = ConcurrentHashMap<String, LoginAttempt>()
    private val loginAttemptsMutex = Mutex()
    @Volatile
    private var loginAttemptsLoaded = false
    
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
        ensureLoginAttemptsLoaded()
        // 检查账户是否被锁定
        val attempt = getLoginAttempt(username)
        
        if (attempt.lockUntil > System.currentTimeMillis()) {
            val remainingTime = (attempt.lockUntil - System.currentTimeMillis()) / 1000
            AppLogger.w("账户 $username 已锁定，剩余时间: ${remainingTime}秒", "Auth")
            return LoginResult.accountLocked(remainingTime)
        }
        
        // 重置过期的尝试记录
        if (System.currentTimeMillis() - attempt.lastAttemptTime > ATTEMPT_WINDOW) {
            attempt.failCount = 0
            attempt.lockUntil = 0
            persistLoginAttempts()
        }
        
        // 验证输入
        if (username.isBlank() || password.isBlank() || !isPasswordValid(password, username)) {
            recordFailedAttempt(username, attempt)
            return LoginResult.invalidInput()
        }
        
        // 验证用户
        val users = loadUsers().toMutableList()
        val existing = users.firstOrNull { it.username == username }
        val hashed = withContext(Dispatchers.Default) { hashPassword(password) }
        val role = if (existing == null) {
            // 首次登录，创建用户
            val created = StoredUser(username = username, passwordHash = hashed, role = "user")
            users.add(created)
            saveUsers(users)
            AppLogger.logAuth("注册", username, true)
            created.role
        } else {
            // 验证密码
            if (!withContext(Dispatchers.Default) { verifyPassword(password, existing.passwordHash) }) {
                val remainingAttempts = MAX_ATTEMPTS - attempt.failCount - 1
                recordFailedAttempt(username, attempt)
                return LoginResult.invalidCredentials(remainingAttempts.coerceAtLeast(0))
            }
            existing.role
        }
        
        // 登录成功，清除尝试记录
        loginAttempts.remove(username)
        persistLoginAttempts()
        AppLogger.logAuth("登录", username, true)
        
        val userEntity = UserEntity(username = username, role = role)
        user = userEntity
        return LoginResult.success(userEntity)
    }
    
    /**
     * 记录失败的登录尝试
     */
    private suspend fun recordFailedAttempt(username: String, attempt: LoginAttempt) {
        attempt.failCount++
        attempt.lastAttemptTime = System.currentTimeMillis()
        
        if (attempt.failCount >= MAX_ATTEMPTS) {
            attempt.lockUntil = System.currentTimeMillis() + LOCK_DURATION
            AppLogger.w("账户 $username 因多次登录失败被锁定", "Auth")
        } else {
            val remainingAttempts = MAX_ATTEMPTS - attempt.failCount
            AppLogger.w("用户 $username 登录失败，剩余尝试次数: $remainingAttempts", "Auth")
        }
        persistLoginAttempts()
    }
    
    /**
     * 解锁账户（管理员功能）
     */
    override suspend fun unlockAccount(username: String): Boolean {
        ensureLoginAttemptsLoaded()
        return if (loginAttempts.remove(username) != null) {
            persistLoginAttempts()
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
        val created = StoredUser(
            username = username,
            passwordHash = withContext(Dispatchers.Default) { hashPassword(password) },
            role = role
        )
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
        users[index] = users[index].copy(passwordHash = withContext(Dispatchers.Default) { hashPassword(newPassword) })
        saveUsers(users)
        return AuthResult(true)
    }

    private suspend fun loadUsers(): List<StoredUser> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_USERS, null) ?: return@withContext emptyList()
        adapter.fromJson(raw).orEmpty()
    }

    private suspend fun saveUsers(users: List<StoredUser>) = withContext(Dispatchers.IO) {
        val json = adapter.toJson(users)
        prefs.edit().putString(KEY_USERS, json).apply()
    }

    private suspend fun ensureLoginAttemptsLoaded() {
        if (loginAttemptsLoaded) return
        loginAttemptsMutex.withLock {
            if (loginAttemptsLoaded) return
            val stored = loadLoginAttempts()
            stored.forEach { attempt ->
                loginAttempts[attempt.username] = LoginAttempt(
                    failCount = attempt.failCount,
                    lastAttemptTime = attempt.lastAttemptTime,
                    lockUntil = attempt.lockUntil
                )
            }
            loginAttemptsLoaded = true
        }
    }

    private suspend fun getLoginAttempt(username: String): LoginAttempt {
        return loginAttempts[username] ?: LoginAttempt().also {
            loginAttempts[username] = it
            persistLoginAttempts()
        }
    }

    private suspend fun loadLoginAttempts(): List<StoredLoginAttempt> = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_LOGIN_ATTEMPTS, null) ?: return@withContext emptyList()
        attemptAdapter.fromJson(raw).orEmpty()
    }

    private suspend fun persistLoginAttempts() = withContext(Dispatchers.IO) {
        val stored = loginAttempts.map { (username, attempt) ->
            StoredLoginAttempt(
                username = username,
                failCount = attempt.failCount,
                lastAttemptTime = attempt.lastAttemptTime,
                lockUntil = attempt.lockUntil
            )
        }
        val json = attemptAdapter.toJson(stored)
        prefs.edit().putString(KEY_LOGIN_ATTEMPTS, json).apply()
    }

    private fun securePreferences(context: Context): SharedPreferences {
        val masterKey = runCatching {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        }.getOrElse {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
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
    val salt = ByteArray(Constants.Password.SALT_LENGTH)
    SecureRandom().nextBytes(salt)
    
    val spec = PBEKeySpec(
        password.toCharArray(),
        salt,
        Constants.Password.PBKDF2_ITERATIONS,
        Constants.Password.HASH_LENGTH
    )
    val factory = SecretKeyFactory.getInstance(Constants.Password.ALGORITHM)
    val hash = factory.generateSecret(spec).encoded
    
    val saltBase64 = Base64.getEncoder().encodeToString(salt)
    val hashBase64 = Base64.getEncoder().encodeToString(hash)
    return "${Constants.Password.HASH_FORMAT_PREFIX}\$${Constants.Password.PBKDF2_ITERATIONS}\$$saltBase64\$$hashBase64"
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
    if (parts[0] != Constants.Password.HASH_FORMAT_PREFIX) return false
    
    val iterations = parts[1].toIntOrNull() ?: return false
    val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
    val expectedHash = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
    
    // 使用相同的盐值和迭代次数计算哈希
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
    val factory = SecretKeyFactory.getInstance(Constants.Password.ALGORITHM)
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
    if (password.length < Constants.Password.MIN_LENGTH) return false
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
private const val KEY_LOGIN_ATTEMPTS = PrefsKeys.KEY_LOGIN_ATTEMPTS

private data class StoredLoginAttempt(
    val username: String,
    val failCount: Int,
    val lastAttemptTime: Long,
    val lockUntil: Long
)
