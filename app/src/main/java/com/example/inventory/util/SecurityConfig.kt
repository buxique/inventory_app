package com.example.inventory.util

/**
 * 安全配置常量
 * 
 * 统一管理安全相关的配置参数
 */
object SecurityConfig {
    // ==================== 登录安全 ====================
    
    /**
     * 最大登录尝试次数
     * 超过此次数将锁定账户
     */
    const val MAX_LOGIN_ATTEMPTS = 5
    
    /**
     * 账户锁定时长（毫秒）
     * 默认 15 分钟
     */
    const val ACCOUNT_LOCK_DURATION_MS = 15 * 60 * 1000L
    
    /**
     * 登录尝试窗口时长（毫秒）
     * 在此时间窗口内的失败尝试会累计
     * 默认 5 分钟
     */
    const val LOGIN_ATTEMPT_WINDOW_MS = 5 * 60 * 1000L
    
    // ==================== 密码策略 ====================
    
    /**
     * 密码最小长度
     */
    const val PASSWORD_MIN_LENGTH = 10
    
    /**
     * 密码哈希迭代次数
     * 使用 PBKDF2WithHmacSHA256
     */
    const val PASSWORD_HASH_ITERATIONS = 100_000
    
    /**
     * 密码哈希长度（位）
     */
    const val PASSWORD_HASH_LENGTH = 256
    
    /**
     * 盐值长度（字节）
     */
    const val SALT_LENGTH = 32
}

/**
 * 同步配置常量
 */
object SyncConfig {
    /**
     * 推送操作超时时间（毫秒）
     */
    const val PUSH_TIMEOUT_MS = 30_000L
    
    /**
     * 拉取操作超时时间（毫秒）
     */
    const val PULL_TIMEOUT_MS = 30_000L
    
    /**
     * 合并操作超时时间（毫秒）
     */
    const val MERGE_TIMEOUT_MS = 60_000L
}

/**
 * 缓存配置常量
 */
object CacheConfig {
    /**
     * 计算重试延迟时间（毫秒）
     * 当检测到其他协程正在计算时的等待时间
     */
    const val COMPUTE_RETRY_DELAY_MS = 10L
    
    /**
     * 最大重试次数
     */
    const val MAX_RETRY_COUNT = 100
}
