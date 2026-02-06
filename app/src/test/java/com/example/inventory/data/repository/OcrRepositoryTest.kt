package com.example.inventory.data.repository

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.inventory.data.model.OcrResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * OcrRepository 单元测试
 * 
 * 测试覆盖：
 * - 本地OCR识别流程
 * - 图片预处理逻辑
 * - 结果合并逻辑
 * - 边界情况和异常处理
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OcrRepositoryTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockAssetManager: AssetManager
    
    @Mock
    private lateinit var mockFile: File
    
    private lateinit var repository: OcrRepositoryImpl
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.assets).thenReturn(mockAssetManager)
        `when`(mockContext.filesDir).thenReturn(File("/tmp"))
        
        repository = OcrRepositoryImpl(mockContext)
    }
    
    @Test
    fun `recognizeLocal should return empty result when file does not exist`() = runTest {
        // Given
        `when`(mockFile.exists()).thenReturn(false)
        
        // When
        val result = repository.recognizeLocal(mockFile)
        
        // Then
        assertNotNull(result)
        assertTrue(result.groups.isEmpty())
    }
    
    @Test
    fun `recognizeLocal should handle invalid image file`() = runTest {
        // Given
        val tempFile = File.createTempFile("test", ".jpg")
        tempFile.writeText("not an image")
        
        try {
            // When
            val result = repository.recognizeLocal(tempFile)
            
            // Then
            assertNotNull(result)
            assertTrue(result.groups.isEmpty())
        } finally {
            tempFile.delete()
        }
    }
    
    @Test
    fun `recognizeLocal should handle large image with downsampling`() = runTest {
        // Given - 创建一个模拟的大图片文件
        val tempFile = File.createTempFile("test_large", ".jpg")
        
        try {
            // 创建一个小的测试图片（实际测试中应该是大图）
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            
            // When
            val result = repository.recognizeLocal(tempFile)
            
            // Then
            assertNotNull(result)
            // 由于没有真实的OCR模型，结果应该为空
            assertTrue(result.groups.isEmpty())
        } finally {
            tempFile.delete()
        }
    }
    
    @Test
    fun `recognizeOnline should return empty result`() = runTest {
        // Given
        val file = File("test.jpg")
        
        // When
        val result = repository.recognizeOnline(file)
        
        // Then
        assertNotNull(result)
        assertTrue(result.groups.isEmpty())
    }
    
    @Test
    fun `mergeResults should combine local and online results`() = runTest {
        // Given
        val localResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "1",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "测试文本",
                            confidence = 0.9f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.9f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        
        val onlineResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "2",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "在线文本",
                            confidence = 0.85f,
                            box = listOf(0f, 60f, 100f, 110f)
                        )
                    ),
                    confidence = 0.85f,
                    box = listOf(0f, 60f, 100f, 110f)
                )
            )
        )
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertEquals(2, merged.groups.size)
    }
    
    @Test
    fun `mergeResults should deduplicate identical text`() = runTest {
        // Given - 两个结果包含相同的文本
        val localResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "1",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "重复文本",
                            confidence = 0.9f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.9f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        
        val onlineResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "2",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "重复文本",
                            confidence = 0.85f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.85f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertEquals(1, merged.groups.size) // 应该去重
        assertEquals(0.9f, merged.groups[0].confidence, 0.01f) // 应该选择置信度更高的
    }
    
    @Test
    fun `mergeResults should handle empty local result`() = runTest {
        // Given
        val localResult = OcrResult(emptyList())
        val onlineResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "1",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "在线文本",
                            confidence = 0.85f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.85f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertEquals(1, merged.groups.size)
        assertEquals("在线文本", merged.groups[0].tokens[0].text)
    }
    
    @Test
    fun `mergeResults should handle empty online result`() = runTest {
        // Given
        val localResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "1",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "本地文本",
                            confidence = 0.9f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.9f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        val onlineResult = OcrResult(emptyList())
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertEquals(1, merged.groups.size)
        assertEquals("本地文本", merged.groups[0].tokens[0].text)
    }
    
    @Test
    fun `mergeResults should handle both empty results`() = runTest {
        // Given
        val localResult = OcrResult(emptyList())
        val onlineResult = OcrResult(emptyList())
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertTrue(merged.groups.isEmpty())
    }
    
    @Test
    fun `mergeResults should select highest confidence when duplicates exist`() = runTest {
        // Given
        val localResult = OcrResult(
            groups = listOf(
                com.example.inventory.data.model.OcrGroup(
                    id = "1",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "文本A",
                            confidence = 0.7f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.7f,
                    box = listOf(0f, 0f, 100f, 50f)
                ),
                com.example.inventory.data.model.OcrGroup(
                    id = "2",
                    tokens = listOf(
                        com.example.inventory.data.model.OcrToken(
                            text = "文本A",
                            confidence = 0.95f,
                            box = listOf(0f, 0f, 100f, 50f)
                        )
                    ),
                    confidence = 0.95f,
                    box = listOf(0f, 0f, 100f, 50f)
                )
            )
        )
        val onlineResult = OcrResult(emptyList())
        
        // When
        val merged = repository.mergeResults(localResult, onlineResult)
        
        // Then
        assertNotNull(merged)
        assertEquals(1, merged.groups.size)
        assertEquals(0.95f, merged.groups[0].confidence, 0.01f)
    }
}
