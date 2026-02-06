package com.example.inventory.ui.viewmodel

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import androidx.paging.PagingData
import kotlinx.coroutines.flow.flowOf

/**
 * SearchViewModel 单元测试
 * 
 * 测试搜索和过滤功能
 */
@ExperimentalCoroutinesApi
class SearchViewModelTest {
    
    @Mock
    private lateinit var inventoryRepository: InventoryRepository
    
    @Mock
    private lateinit var categoryRepository: CategoryRepository
    
    private lateinit var viewModel: SearchViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = SearchViewModel(inventoryRepository, categoryRepository)
    }
    
    @Test
    fun `updateSearchQuery should update query state`() = runTest {
        // Given
        val query = "test query"
        
        // When
        viewModel.updateSearchQuery(query)
        
        // Then
        assertEquals(query, viewModel.searchQuery.first())
    }
    
    @Test
    fun `updateSearchQuery should trim whitespace`() = runTest {
        // Given
        val query = "  test query  "
        
        // When
        viewModel.updateSearchQuery(query)
        
        // Then
        assertEquals("test query", viewModel.searchQuery.first())
    }
    
    @Test
    fun `clearSearch should reset query to empty`() = runTest {
        // Given
        viewModel.updateSearchQuery("test")
        
        // When
        viewModel.clearSearch()
        
        // Then
        assertEquals("", viewModel.searchQuery.first())
    }
    
    @Test
    fun `selectCategory should update selected category id`() = runTest {
        // Given
        val categoryId = 123L
        
        // When
        viewModel.selectCategory(categoryId)
        
        // Then
        assertEquals(categoryId, viewModel.selectedCategoryId.first())
    }
    
    @Test
    fun `selectCategory with null should clear category filter`() = runTest {
        // Given
        viewModel.selectCategory(123L)
        
        // When
        viewModel.selectCategory(null)
        
        // Then
        assertNull(viewModel.selectedCategoryId.first())
    }
    
    @Test
    fun `clearCategoryFilter should reset category to null`() = runTest {
        // Given
        viewModel.selectCategory(123L)
        
        // When
        viewModel.clearCategoryFilter()
        
        // Then
        assertNull(viewModel.selectedCategoryId.first())
    }
    
    @Test
    fun `initial state should have empty query and no category`() = runTest {
        // Then
        assertEquals("", viewModel.searchQuery.first())
        assertNull(viewModel.selectedCategoryId.first())
    }
}
