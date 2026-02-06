package com.example.inventory.util

import androidx.collection.LruCache
import com.example.inventory.util.CacheConfig
import com.example.inventory.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据库查询结果缓存管理器
 * 
 * 特性：
 * - 使用LruCache自动管理内存
 * - 支持TTL（生存时间）
 * - 线程安全
 * - 支持标签分组清除
 */
class QueryCache<T : Any> private constructor(
    maxSize: Int,
    private val defaultTtlMillis: Long
) {
    
    companion object {
        private const val DEFAULT_MAX_SIZE = Constants.Cache.QUERY_DEFAULT_MAX_SIZE
        private const val DEFAULT_TTL_MILLIS = Constants.Cache.QUERY_DEFAULT_TTL_MILLIS
        
        fun <T : Any> create(
            maxSize: Int = DEFAULT_MAX_SIZE,
            defaultTtlMillis: Long = DEFAULT_TTL_MILLIS
        ): QueryCache<T> {
            return QueryCache(maxSize, defaultTtlMillis)
        }
    }
    
    /**
     * 缓存条目
     */
    private data class CacheEntry<T>(
        val value: T,
        val timestamp: Long,
        val ttlMillis: Long,
        val tags: Set<String>
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > ttlMillis
        }
    }
    
    // LRU缓存
    private val cache = object : LruCache<String, CacheEntry<T>>(maxSize) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CacheEntry<T>,
            newValue: CacheEntry<T>?
        ) {
            if (evicted) {
                oldValue.tags.forEach { tag ->
                    tagIndex[tag]?.remove(key)
                    if (tagIndex[tag]?.isEmpty() == true) {
                        tagIndex.remove(tag)
                    }
                }
            }
        }
    }
    
    // 标签索引：标签 -> 键集合
    private val tagIndex = mutableMapOf<String, MutableSet<String>>()
    
    // 互斥锁，保证线程安全
    private val mutex = Mutex()
    
    // 正在计算的键集合，防止重复计算
    private val computingKeys = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * 获取缓存值
     * 
     * @param key 缓存键
     * @return 缓存的值或null（未找到或已过期）
     */
    suspend fun get(key: String): T? = mutex.withLock {
        val entry = cache.get(key) ?: return null
        
        if (entry.isExpired()) {
            // 过期，移除缓存
            removeInternal(key)
            return null
        }
        
        return entry.value
    }
    
    /**
     * 保存缓存值
     * 
     * @param key 缓存键
     * @param value 值
     * @param ttlMillis TTL（毫秒），null则使用默认值
     * @param tags 标签集合，用于分组清除
     */
    suspend fun put(
        key: String,
        value: T,
        ttlMillis: Long? = null,
        tags: Set<String> = emptySet()
    ) = mutex.withLock {
        val entry = CacheEntry(
            value = value,
            timestamp = System.currentTimeMillis(),
            ttlMillis = ttlMillis ?: defaultTtlMillis,
            tags = tags
        )
        
        // 保存到LRU缓存
        cache.put(key, entry)
        
        // 更新标签索引
        tags.forEach { tag ->
            tagIndex.getOrPut(tag) { mutableSetOf() }.add(key)
        }
    }
    
    /**
     * 获取或计算缓存值
     * 
     * 如果缓存存在且未过期，直接返回；否则执行计算函数并缓存结果
     * 优化了并发性能，防止重复计算
     * 
     * @param key 缓存键
     * @param ttlMillis TTL（毫秒）
     * @param tags 标签集合
     * @param compute 计算函数
     * @return 缓存或计算的值
     */
    suspend fun getOrPut(
        key: String,
        ttlMillis: Long? = null,
        tags: Set<String> = emptySet(),
        compute: suspend () -> T
    ): T {
        // 快速路径：检查缓存
        mutex.withLock {
            val entry = cache.get(key)
            if (entry != null && !entry.isExpired()) {
                return entry.value
            }
            if (entry != null && entry.isExpired()) {
                removeInternal(key)
            }
        }

        // 检查是否有其他协程正在计算
        var retryCount = 0
        while (!computingKeys.add(key)) {
            if (retryCount++ > CacheConfig.MAX_RETRY_COUNT) {
                // 超过最大重试次数，直接计算
                break
            }
            delay(CacheConfig.COMPUTE_RETRY_DELAY_MS)
            mutex.withLock {
                val entry = cache.get(key)
                if (entry != null && !entry.isExpired()) {
                    return entry.value
                }
            }
        }

        try {
            // 计算值（锁外执行，避免阻塞）
            val value = compute()
            
            // 保存到缓存
            mutex.withLock {
                // 双重检查，防止重复计算
                val entry = cache.get(key)
                if (entry != null && !entry.isExpired()) {
                    return@withLock entry.value
                }
                
                val newEntry = CacheEntry(
                    value = value,
                    timestamp = System.currentTimeMillis(),
                    ttlMillis = ttlMillis ?: defaultTtlMillis,
                    tags = tags
                )
                cache.put(key, newEntry)
                tags.forEach { tag ->
                    tagIndex.getOrPut(tag) { mutableSetOf() }.add(key)
                }
            }
            return value
        } finally {
            computingKeys.remove(key)
        }
    }
    
    /**
     * 移除指定键的缓存
     */
    suspend fun remove(key: String) = mutex.withLock {
        removeInternal(key)
    }
    
    /**
     * 移除带有指定标签的所有缓存
     * 
     * @param tag 标签
     * @return 移除的条目数
     */
    suspend fun removeByTag(tag: String): Int = mutex.withLock {
        val keys = tagIndex[tag] ?: return 0
        var count = 0
        
        keys.toList().forEach { key ->
            removeInternal(key)
            count++
        }
        
        count
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clear() = mutex.withLock {
        cache.evictAll()
        tagIndex.clear()
    }
    
    /**
     * 清除过期的缓存条目
     * 
     * @return 清除的条目数
     */
    suspend fun evictExpired(): Int = mutex.withLock {
        val snapshot = cache.snapshot()
        var count = 0
        
        snapshot.forEach { (key, entry) ->
            if (entry.isExpired()) {
                removeInternal(key)
                count++
            }
        }
        
        count
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getStats(): QueryCacheStats {
        val snapshot = cache.snapshot()
        val now = System.currentTimeMillis()
        
        var expiredCount = 0
        snapshot.values.forEach { entry ->
            if (entry.isExpired()) {
                expiredCount++
            }
        }
        
        return QueryCacheStats(
            size = snapshot.size,
            maxSize = cache.maxSize(),
            expiredCount = expiredCount,
            hitCount = cache.hitCount().toInt(),
            missCount = cache.missCount().toInt(),
            tagCount = tagIndex.size
        )
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 移除缓存条目（内部方法，不加锁）
     */
    private fun removeInternal(key: String) {
        val entry = cache.remove(key)
        
        // 从标签索引中移除
        entry?.tags?.forEach { tag ->
            tagIndex[tag]?.remove(key)
            if (tagIndex[tag]?.isEmpty() == true) {
                tagIndex.remove(tag)
            }
        }
    }
}

/**
 * 缓存统计信息
 */
data class QueryCacheStats(
    val size: Int,          // 当前缓存条目数
    val maxSize: Int,       // 最大缓存条目数
    val expiredCount: Int,  // 过期条目数
    val hitCount: Int,      // 命中次数
    val missCount: Int,     // 未命中次数
    val tagCount: Int       // 标签数量
) {
    val hitRate: Float
        get() = if (hitCount + missCount > 0) {
            hitCount.toFloat() / (hitCount + missCount)
        } else 0f
}

/**
 * 生成查询缓存键
 * 
 * @param query 查询描述
 * @param params 查询参数
 * @return 缓存键
 */
fun generateQueryKey(query: String, vararg params: Any?): String {
    val combined = buildString {
        append(query)
        params.forEach { param ->
            append("|")
            append(param?.toString() ?: "null")
        }
    }
    
    // 使用MD5哈希生成固定长度的键
    val digest = MessageDigest.getInstance("MD5")
    val hash = digest.digest(combined.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}

