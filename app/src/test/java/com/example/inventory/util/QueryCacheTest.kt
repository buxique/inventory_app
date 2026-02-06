package com.example.inventory.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QueryCacheTest {

    @Test
    fun `test TTL expiration`() = runTest {
        val cache = QueryCache.create<String>(maxSize = 10, defaultTtlMillis = 100L)
        
        cache.put("key1", "value1")
        
        // Immediate get
        assertEquals("value1", cache.get("key1"))
        
        // Wait for expiration
        Thread.sleep(150)
        
        assertNull(cache.get("key1"))
    }

    @Test
    fun `test tag-based removal`() = runTest {
        val cache = QueryCache.create<String>(maxSize = 10)
        
        cache.put("key1", "value1", tags = setOf("groupA"))
        cache.put("key2", "value2", tags = setOf("groupA"))
        cache.put("key3", "value3", tags = setOf("groupB"))
        
        val removedCount = cache.removeByTag("groupA")
        
        assertEquals(2, removedCount)
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNotNull(cache.get("key3"))
    }

    @Test
    fun `test getOrPut`() = runTest {
        val cache = QueryCache.create<String>(maxSize = 10)
        var counter = 0
        
        val result1 = cache.getOrPut("key") {
            counter++
            "computed"
        }
        
        val result2 = cache.getOrPut("key") {
            counter++
            "computed2"
        }
        
        assertEquals("computed", result1)
        assertEquals("computed", result2)
        assertEquals(1, counter)
    }

    @Test
    fun `test concurrency`() = runTest {
        val cache = QueryCache.create<String>(maxSize = 100)
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        val dispatcher = Dispatchers.Default
        
        repeat(50) { i ->
            jobs += launch(dispatcher) {
                cache.put("key$i", "value$i")
            }
        }
        
        jobs.forEach { it.join() }
        jobs.clear()
        
        var errorOccurred = false
        repeat(50) { i ->
            jobs += launch(dispatcher) {
                try {
                    cache.getOrPut("key$i") { "computed$i" }
                } catch (e: Exception) {
                    errorOccurred = true
                }
            }
        }
        
        jobs.forEach { it.join() }
        
        assertFalse("Concurrency error occurred", errorOccurred)
        assertEquals(50, cache.getStats().size)
    }
}
