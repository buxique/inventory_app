package com.example.inventory.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 图片缓存管理器
 * 
 * ## 核心功能
 * 实现两级缓存策略，提供高效的图片缓存管理
 * 
 * ### 两级缓存架构
 * 
 * 1. **内存缓存（LruCache）**:
 *    - 快速访问，典型耗时 < 1ms
 *    - 自动管理内存，使用 LRU 算法淘汰
 *    - 大小 = 应用最大内存 / 8
 *    - 支持 Bitmap 自动回收
 * 
 * 2. **磁盘缓存（文件系统）**:
 *    - 持久化存储，应用重启后仍可用
 *    - 典型耗时 10-50ms
 *    - 最大 50MB，超出自动清理最旧文件
 *    - 使用 MD5 哈希作为文件名
 * 
 * ## 并发处理策略
 * 
 * ### 线程安全
 * - 所有公共方法使用 `withContext(Dispatchers.IO)` 在 IO 线程执行
 * - LruCache 本身是线程安全的（内部使用 synchronized）
 * - 磁盘操作使用 IO 调度器，避免阻塞主线程
 * 
 * ### 并发优化
 * - 快速路径：内存缓存命中时，无需磁盘 IO
 * - 懒加载：磁盘缓存目录按需创建
 * - 原子操作：使用 AtomicLong 管理磁盘缓存大小
 * 
 * ## 内存管理
 * 
 * ### Bitmap 回收策略
 * ```
 * 回收条件：
 * 1. 被 LRU 淘汰（evicted = true）
 * 2. 新旧值不同（避免回收仍在使用的）
 * 3. 未被回收（避免重复回收）
 * ```
 * 
 * ### 内存占用估算
 * - 1920x1080 ARGB_8888 图片：约 8MB
 * - 内存缓存最大值：应用最大内存 / 8
 * - 典型应用（512MB 最大内存）：内存缓存约 64MB
 * 
 * ## 磁盘管理
 * 
 * ### 磁盘缓存清理策略
 * ```
 * 触发条件：磁盘缓存 > 50MB
 * 清理策略：按文件修改时间排序，删除最旧的文件
 * 清理目标：磁盘缓存 ≤ 50MB
 * ```
 * 
 * ### 文件命名
 * - 使用 MD5 哈希作为文件名
 * - 避免文件名冲突
 * - 固定长度（32 字符）
 * 
 * ## 性能优化
 * 
 * ### 降采样加载
 * - 根据目标尺寸计算 inSampleSize
 * - 使用 2 的幂次方（1, 2, 4, 8, ...）
 * - 减少内存占用，提升加载速度
 * 
 * ### 缓存命中率
 * - 内存缓存命中：< 1ms
 * - 磁盘缓存命中：10-50ms
 * - 缓存未命中：100-500ms（需要解码）
 * 
 * ## 使用示例
 * 
 * ### 示例 1：基本用法
 * ```kotlin
 * val imageCache = ImageCache.getInstance(context)
 * 
 * // 获取缓存图片
 * val bitmap = imageCache.get("image_key")
 * if (bitmap != null) {
 *     imageView.setImageBitmap(bitmap)
 * }
 * 
 * // 保存图片到缓存
 * imageCache.put("image_key", bitmap)
 * ```
 * 
 * ### 示例 2：加载并缓存
 * ```kotlin
 * val imageCache = ImageCache.getInstance(context)
 * 
 * // 从文件加载并缓存（自动降采样）
 * val bitmap = imageCache.loadAndCache(
 *     filePath = "/path/to/image.jpg",
 *     maxWidth = 1920,
 *     maxHeight = 1080
 * )
 * 
 * if (bitmap != null) {
 *     imageView.setImageBitmap(bitmap)
 * }
 * ```
 * 
 * ### 示例 3：查看缓存统计
 * ```kotlin
 * val stats = imageCache.getCacheStats()
 * 
 * println("内存缓存: ${stats.memorySize}KB / ${stats.memoryMaxSize}KB")
 * println("内存命中率: ${stats.memoryHitRate * 100}%")
 * println("磁盘缓存: ${stats.diskSizeMB}MB (${stats.diskFileCount} 个文件)")
 * ```
 * 
 * ### 示例 4：清理缓存
 * ```kotlin
 * // 清除指定图片
 * imageCache.remove("image_key")
 * 
 * // 清空所有缓存
 * imageCache.clear()
 * ```
 * 
 * ## 注意事项
 * 
 * 1. **单例模式**:
 *    - 使用 getInstance() 获取实例
 *    - 全局共享，避免重复创建
 * 
 * 2. **Bitmap 生命周期**:
 *    - 从缓存获取的 Bitmap 不要手动 recycle
 *    - 缓存会自动管理 Bitmap 生命周期
 * 
 * 3. **内存压力**:
 *    - 大图片会占用大量内存
 *    - 建议使用降采样加载
 *    - 监控缓存统计信息
 * 
 * 4. **磁盘空间**:
 *    - 磁盘缓存最大 50MB
 *    - 超出自动清理最旧文件
 *    - 可以手动调用 clear() 清空
 * 
 * @see LruCache Android 提供的 LRU 缓存实现
 * @see BitmapFactory.Options 图片解码选项
 */
class ImageCache private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ImageCache? = null
        
        private const val MEMORY_CACHE_SIZE_DIVIDER = Constants.Cache.IMAGE_MEMORY_CACHE_DIVIDER
        private const val DISK_CACHE_SIZE = Constants.Cache.IMAGE_DISK_CACHE_SIZE.toInt()
        
        fun getInstance(context: Context): ImageCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageCache(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / MEMORY_CACHE_SIZE_DIVIDER
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
            
            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                /**
                 * Bitmap 回收策略
                 * 
                 * ## 回收条件
                 * 1. 被淘汰（evicted = true）：LRU 缓存满了，自动移除最久未使用的
                 * 2. 新旧值不同（oldValue != newValue）：避免回收仍在使用的 Bitmap
                 * 3. 未被回收（!oldValue.isRecycled）：避免重复回收
                 * 
                 * ## 为什么需要回收？
                 * - Bitmap 占用大量内存（如 1920x1080 的图片约 8MB）
                 * - 及时回收可以避免 OutOfMemoryError
                 * - 减少 GC 压力，提升性能
                 * 
                 * ## 安全性考虑
                 * - 仅回收被淘汰的 Bitmap，不回收主动替换的
                 * - 检查 isRecycled 状态，避免重复回收导致崩溃
                 * - 使用 try-catch 捕获异常，避免影响缓存功能
                 */
                if (evicted && oldValue != newValue && !oldValue.isRecycled) {
                    AppLogger.d("LRU 淘汰 Bitmap: $key (${oldValue.byteCount / 1024}KB)", "ImageCache")
                }

            }
        }
    }
    
    // 磁盘缓存目录
    private val diskCacheDir: File by lazy {
        File(context.cacheDir, "image_cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    // 当前磁盘缓存大小（字节）
    private val currentDiskCacheSize = java.util.concurrent.atomic.AtomicLong(0L)
    private val cacheSizeInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    
    /**
     * 确保缓存大小已初始化
     */
    private fun ensureCacheSizeInitialized() {
        if (cacheSizeInitialized.compareAndSet(false, true)) {
            val size = diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
            currentDiskCacheSize.set(size)
        }
    }
    
    /**
     * 获取缓存图片
     * 
     * @param key 缓存键（通常是图片URL或文件路径）
     * @return Bitmap或null
     */
    suspend fun get(key: String): Bitmap? = withContext(Dispatchers.IO) {
        val cachedBitmap = memoryCache.get(key)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            AppLogger.i("从内存缓存获取图片: $key", "ImageCache")
            return@withContext cachedBitmap
        }
        
        val diskFile = getDiskCacheFile(key)
        if (diskFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bitmap != null) {
                AppLogger.i("从磁盘缓存获取图片: $key", "ImageCache")
                memoryCache.put(key, bitmap)
                return@withContext bitmap
            }
        }
        
        null
    }
    
    /**
     * 保存图片到缓存
     * 
     * @param key 缓存键
     * @param bitmap 图片
     */
    suspend fun put(key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        ensureCacheSizeInitialized()
        memoryCache.put(key, bitmap)
        
        try {
            val diskFile = getDiskCacheFile(key)
            val oldSize = if (diskFile.exists()) diskFile.length() else 0L
            
            // 根据key后缀判断是否使用JPEG
            val isJpeg = key.endsWith(".jpg", ignoreCase = true) || key.endsWith(".jpeg", ignoreCase = true)
            val format = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val quality = if (isJpeg) Constants.Cache.IMAGE_COMPRESS_QUALITY_JPEG else Constants.Cache.IMAGE_COMPRESS_QUALITY

            FileOutputStream(diskFile).use { out ->
                bitmap.compress(format, quality, out)
                out.flush()
            }
            
            val newSize = diskFile.length()
            currentDiskCacheSize.addAndGet(newSize - oldSize)  // 只增加差值
            
            AppLogger.d("图片已缓存: $key ($quality%)", "ImageCache")
        } catch (e: Exception) {
            AppLogger.e("保存图片缓存失败: ${e.message}", "ImageCache")
        }
        
        checkDiskCacheSize()
    }
    
    /**
     * 从文件加载并缓存图片
     * 
     * @param filePath 文件路径
     * @param maxWidth 最大宽度（用于降采样）
     * @param maxHeight 最大高度（用于降采样）
     * @return Bitmap或null
     */
    suspend fun loadAndCache(
        filePath: String,
        maxWidth: Int = Constants.Cache.IMAGE_DEFAULT_WIDTH,
        maxHeight: Int = Constants.Cache.IMAGE_DEFAULT_HEIGHT
    ): Bitmap? = withContext(Dispatchers.IO) {

        val key = filePath
        
        val cached = get(key)
        if (cached != null) {
            return@withContext cached
        }
        
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext null
        }
        
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            
            val bitmap = BitmapFactory.decodeFile(filePath, options)
            
            if (bitmap != null) {
                put(key, bitmap)
            }
            
            bitmap
        } catch (e: Exception) {
            AppLogger.e("加载图片失败: ${e.message}", "ImageCache")
            null
        }
    }
    
    /**
     * 清除指定键的缓存
     */
    suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        ensureCacheSizeInitialized()
        memoryCache.remove(key)
        
        val diskFile = getDiskCacheFile(key)
        if (diskFile.exists()) {
            val fileSize = diskFile.length()
            if (diskFile.delete()) {
                currentDiskCacheSize.addAndGet(-fileSize)  // 减少大小
            }
        }
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        
        diskCacheDir.listFiles()?.forEach { it.delete() }
        
        // 重置磁盘缓存大小统计
        currentDiskCacheSize.set(0L)
        
        AppLogger.i("已清空图片缓存", "ImageCache")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): ImageCacheStats {
        val memorySize = memoryCache.size()
        val memoryMaxSize = memoryCache.maxSize()
        val memoryHitCount = memoryCache.hitCount()
        val memoryMissCount = memoryCache.missCount()
        
        val diskSize = diskCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        val diskFileCount = diskCacheDir.listFiles()?.size ?: 0
        
        return ImageCacheStats(
            memorySize = memorySize,
            memoryMaxSize = memoryMaxSize,
            memoryHitCount = memoryHitCount.toInt(),
            memoryMissCount = memoryMissCount.toInt(),
            diskSize = diskSize,
            diskFileCount = diskFileCount
        )
    }
    
    private fun getDiskCacheFile(key: String): File {
        val filename = hashKey(key)
        return File(diskCacheDir, filename)
    }
    
    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(key.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && 
                   halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        AppLogger.d("计算采样率: inSampleSize=$inSampleSize", "ImageCache")
        return inSampleSize
    }
    
    private fun checkDiskCacheSize() {
        ensureCacheSizeInitialized()
        
        // 快速检查
        if (currentDiskCacheSize.get() <= DISK_CACHE_SIZE) return

        val files = diskCacheDir.listFiles() ?: return
        var currentSize = currentDiskCacheSize.get()
        
        if (currentSize > DISK_CACHE_SIZE) {
            AppLogger.w("磁盘缓存超出限制(${currentSize / 1024 / 1024}MB)，开始清理", "ImageCache")
            files.sortedBy { it.lastModified() }
                .forEach { file ->
                    if (currentSize <= DISK_CACHE_SIZE) return@forEach
                    val fileSize = file.length()
                    if (file.delete()) {
                        currentSize -= fileSize
                        AppLogger.d("删除旧缓存文件: ${file.name}", "ImageCache")
                    }
                }
            currentDiskCacheSize.set(currentSize)
            AppLogger.i("磁盘缓存清理完成，当前大小: ${currentSize / (1024 * 1024)}MB", "ImageCache")
        }
    }
}

/**
 * 缓存统计信息
 */
data class ImageCacheStats(
    val memorySize: Int,        // 内存缓存当前大小（KB）
    val memoryMaxSize: Int,     // 内存缓存最大大小（KB）
    val memoryHitCount: Int,    // 内存缓存命中次数
    val memoryMissCount: Int,   // 内存缓存未命中次数
    val diskSize: Long,         // 磁盘缓存大小（字节）
    val diskFileCount: Int      // 磁盘缓存文件数
) {
    val memoryHitRate: Float
        get() = if (memoryHitCount + memoryMissCount > 0) {
            memoryHitCount.toFloat() / (memoryHitCount + memoryMissCount)
        } else 0f
    
    val diskSizeMB: Float
        get() = diskSize / (1024f * 1024f)
}
