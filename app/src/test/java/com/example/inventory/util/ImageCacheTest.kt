package com.example.inventory.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImageCacheTest {

    private lateinit var context: Context
    private lateinit var imageCache: ImageCache

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        val instanceField = ImageCache::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        
        imageCache = ImageCache.getInstance(context)
    }

    @Test
    fun `test memory cache hit`() = runTest {
        val key = "test_key"
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        imageCache.put(key, bitmap)
        
        val retrieved = imageCache.get(key)
        assertNotNull(retrieved)
        assertEquals(bitmap, retrieved)
    }

    @Test
    fun `test disk cache persistence`() = runTest {
        val key = "disk_key"
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        imageCache.put(key, bitmap)
        
        getMemoryCache().evictAll()
        
        val retrieved = imageCache.get(key)
        assertNotNull(retrieved)
        assertEquals(100, retrieved?.width)
        assertEquals(100, retrieved?.height)
    }

    @Test
    fun `test LRU eviction strategy`() = runTest {
        val smallCache = object : android.util.LruCache<String, Bitmap>(2) {
             override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return 1
            }
        }
        setMemoryCache(smallCache)
        
        val bitmap1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bitmap3 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        
        imageCache.put("key1", bitmap1)
        imageCache.put("key2", bitmap2)
        imageCache.put("key3", bitmap3)
        val memoryCache = getMemoryCache()
        assertNull(memoryCache.get("key1"))
        assertNotNull(memoryCache.get("key2"))
        assertNotNull(memoryCache.get("key3"))
    }

    @Test
    fun `test downsampling logic`() = runTest {
        val largeBitmap = Bitmap.createBitmap(2000, 2000, Bitmap.Config.ARGB_8888)
        val file = File(context.cacheDir, "large_image.png")
        java.io.FileOutputStream(file).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        val loaded = imageCache.loadAndCache(file.absolutePath, maxWidth = 100, maxHeight = 100)
        
        assertNotNull(loaded)
        assertTrue(loaded!!.width < 2000 && loaded.height < 2000)
    }

    @Test
    fun `test cache cleanup`() = runTest {
        val key = "cleanup_key"
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        imageCache.put(key, bitmap)
        
        assertNotNull(imageCache.get(key))
        
        imageCache.clear()
        
        assertNull(imageCache.get(key))
        
        val cacheDir = File(context.cacheDir, "image_cache")
        assertTrue(cacheDir.listFiles()?.isEmpty() == true)
    }

    private fun getMemoryCache(): android.util.LruCache<String, Bitmap> {
        val delegateField = ImageCache::class.java.getDeclaredField("memoryCache\$delegate")
        delegateField.isAccessible = true
        val lazyValue = delegateField.get(imageCache) as Lazy<*>
        return lazyValue.value as android.util.LruCache<String, Bitmap>
    }

    private fun setMemoryCache(cache: android.util.LruCache<String, Bitmap>) {
        val delegateField = ImageCache::class.java.getDeclaredField("memoryCache\$delegate")
        delegateField.isAccessible = true
        delegateField.set(imageCache, lazyOf(cache))
    }
}
