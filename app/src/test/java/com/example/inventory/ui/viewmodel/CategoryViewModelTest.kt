package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * CategoryViewModel 单元测试
 * 
 * 测试分类管理功能
 */
@ExperimentalCoroutinesApi
class CategoryViewModelTest {
    
    @Mock
    private lateinit var categoryRepository: CategoryRepository
    
    private lateinit var viewModel: CategoryViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    private val testCategories = listOf(
        CategoryEntity(id = 1L, name = "电子产品"),
        CategoryEntity(id = 2L, name = "办公用品"),
        CategoryEntity(id = 3L, name = "食品饮料")
    )
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock repository 返回测试数据
        runTest {
            whenever(categoryRepository.getAllCategories()).thenReturn(testCategories)
        }
        
        viewModel = CategoryViewModel(categoryRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `init should load categories automatically`() = runTest {
        // Given - setup 中已初始化
        
        // Then - 验证自动加载
        advanceUntilIdle()
        verify(categoryRepository).getAllCategories()
        assertEquals(testCategories, viewModel.categories.first())
    }
    
    @Test
    fun `loadCategories should update categories state`() = runTest {
        // Given
        val newCategories = listOf(
            CategoryEntity(id = 4L, name = "新分类1"),
            CategoryEntity(id = 5L, name = "新分类2")
        )
        whenever(categoryRepository.getAllCategories()).thenReturn(newCategories)
        
        // When
        viewModel.loadCategories()
        advanceUntilIdle()
        
        // Then
        assertEquals(newCategories, viewModel.categories.first())
    }
    
    @Test
    fun `loadCategories should set loading state`() = runTest {
        // Given
        whenever(categoryRepository.getAllCategories()).thenReturn(testCategories)
        
        // When
        viewModel.loadCategories()
        advanceUntilIdle()
        
        // Then - 验证加载完成后 loading 为 false
        assertFalse(viewModel.isLoading.first())
    }
    
    @Test
    fun `addCategory with valid name should return true`() = runTest {
        // Given
        val categoryName = "新分类"
        
        // When
        val result = viewModel.addCategory(categoryName)
        advanceUntilIdle()
        
        // Then
        assertTrue(result)
        verify(categoryRepository).addCategory(
            org.mockito.kotlin.argThat { category -> category.name == categoryName }
        )
        verify(categoryRepository, org.mockito.kotlin.atLeast(2)).getAllCategories() // init + reload
    }
    
    @Test
    fun `addCategory with blank name should return false`() = runTest {
        // Given
        val blankNames = listOf("", "   ", "\t", "\n")
        
        // When & Then
        blankNames.forEach { name ->
            val result = viewModel.addCategory(name)
            assertFalse("空白名称 '$name' 应该返回 false", result)
        }
        
        // 验证没有调用 repository
        verify(categoryRepository, org.mockito.kotlin.never()).addCategory(any())
    }
    
    @Test
    fun `addCategory should trim whitespace`() = runTest {
        // Given
        val nameWithSpaces = "  新分类  "
        val expectedName = "新分类"
        
        // When
        viewModel.addCategory(nameWithSpaces)
        advanceUntilIdle()
        
        // Then
        verify(categoryRepository).addCategory(
            org.mockito.kotlin.argThat { category -> category.name == expectedName }
        )
    }
    
    @Test
    fun `addCategory with exception should return false`() = runTest {
        // Given
        val categoryName = "新分类"
        whenever(categoryRepository.addCategory(any())).thenThrow(RuntimeException("添加失败"))
        
        // When
        val result = viewModel.addCategory(categoryName)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `deleteCategory should return true on success`() = runTest {
        // Given
        val categoryId = 123L
        
        // When
        val result = viewModel.deleteCategory(categoryId)
        advanceUntilIdle()
        
        // Then
        assertTrue(result)
        verify(categoryRepository).deleteCategory(categoryId)
        verify(categoryRepository, org.mockito.kotlin.atLeast(2)).getAllCategories() // init + reload
    }
    
    @Test
    fun `deleteCategory with exception should return false`() = runTest {
        // Given
        val categoryId = 123L
        whenever(categoryRepository.deleteCategory(categoryId)).thenThrow(RuntimeException("删除失败"))
        
        // When
        val result = viewModel.deleteCategory(categoryId)
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `initial state should have empty categories`() = runTest {
        // Given - 创建新的 ViewModel，不自动加载
        whenever(categoryRepository.getAllCategories()).thenReturn(emptyList())
        clearInvocations(categoryRepository)
        val newViewModel = CategoryViewModel(categoryRepository)
        advanceUntilIdle()
        
        // Then - 初始化后应该调用加载
        verify(categoryRepository).getAllCategories()
    }
    
    @Test
    fun `multiple loadCategories calls should work correctly`() = runTest {
        // Given
        val categories1 = listOf(CategoryEntity(id = 1L, name = "分类1"))
        val categories2 = listOf(CategoryEntity(id = 2L, name = "分类2"))
        val categories3 = listOf(CategoryEntity(id = 3L, name = "分类3"))
        
        // When & Then
        whenever(categoryRepository.getAllCategories()).thenReturn(categories1)
        viewModel.loadCategories()
        advanceUntilIdle()
        assertEquals(categories1, viewModel.categories.first())
        
        whenever(categoryRepository.getAllCategories()).thenReturn(categories2)
        viewModel.loadCategories()
        advanceUntilIdle()
        assertEquals(categories2, viewModel.categories.first())
        
        whenever(categoryRepository.getAllCategories()).thenReturn(categories3)
        viewModel.loadCategories()
        advanceUntilIdle()
        assertEquals(categories3, viewModel.categories.first())
    }
}
