package com.example.inventory.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.model.OcrResult
import com.example.inventory.data.repository.OcrRepository
import com.example.inventory.ui.state.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.example.inventory.data.model.OcrToken
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * CaptureViewModel单元测试
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var mockOcrRepository: OcrRepository
    
    private lateinit var viewModel: CaptureViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        viewModel = CaptureViewModel(mockOcrRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `runOcr should update state with OCR results on success`() = runTest {
        // Given
        val mockFile = mock<File>()
        val expectedResult = OcrResult(
            listOf(
                OcrGroup("group1", listOf(OcrToken("商品名称: 测试商品", 0.95f)), 0.95f),
                OcrGroup("group2", listOf(OcrToken("品牌: 测试品牌", 0.90f)), 0.90f)
            )
        )
        val localResult = OcrResult(expectedResult.groups)
        val onlineResult = OcrResult(emptyList())

        whenever(mockOcrRepository.recognizeLocal(mockFile)).thenReturn(localResult)
        whenever(mockOcrRepository.recognizeOnline(mockFile)).thenReturn(onlineResult)
        whenever(mockOcrRepository.mergeResults(localResult, onlineResult)).thenReturn(expectedResult)
        
        // When
        viewModel.runOcr(mockFile)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.processingState is ProcessingState.Success)
        assertEquals(2, state.ocrGroups.size)
    }
    
    @Test
    fun `runOcr should update state with error on failure`() = runTest {
        // Given
        val mockFile = mock<File>()
        val errorMessage = "识别失败"
        whenever(mockOcrRepository.recognizeLocal(mockFile)).thenThrow(RuntimeException(errorMessage))
        
        // When
        viewModel.runOcr(mockFile)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.processingState is ProcessingState.Error)
        assertEquals(errorMessage, (state.processingState as ProcessingState.Error).message)
    }
    
    @Test
    fun `selectGroup should update selectedGroupId in state`() {
        // Given
        val groupId = "group-1"
        val group = OcrGroup(groupId, listOf(OcrToken("test", 0.9f)), 0.9f)
        
        // When
        viewModel.selectGroup(group)
        
        // Then
        val state = viewModel.state.value
        assertEquals(groupId, state.selectedGroupId)
    }
    
    @Test
    fun `toggleGroupSelection should add group to selection`() {
        // Given
        val groupId = "group-1"
        val group = OcrGroup(groupId, listOf(OcrToken("test", 0.9f)), 0.9f)
        
        // When
        viewModel.toggleGroupSelection(group)
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.selectedGroupIds.contains(groupId))
    }
    
    @Test
    fun `toggleGroupSelection should remove group from selection if already selected`() {
        // Given
        val groupId = "group-1"
        val group = OcrGroup(groupId, listOf(OcrToken("test", 0.9f)), 0.9f)
        viewModel.toggleGroupSelection(group) // 先选中
        
        // When
        viewModel.toggleGroupSelection(group) // 再取消
        
        // Then
        val state = viewModel.state.value
        assertTrue(!state.selectedGroupIds.contains(groupId))
    }
    
    @Test
    fun `mergeSelectedGroups should clear selection`() {
        // Given
        val group1 = OcrGroup("group-1", listOf(OcrToken("test", 0.9f)), 0.9f)
        val group2 = OcrGroup("group-2", listOf(OcrToken("test", 0.9f)), 0.9f)
        viewModel.toggleGroupSelection(group1)
        viewModel.toggleGroupSelection(group2)
        
        // When
        viewModel.mergeSelectedGroups()
        
        // Then
        val state = viewModel.state.value
        assertTrue(state.selectedGroupIds.isEmpty())
    }
}
