package com.example.inventory.ui.viewmodel

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SearchViewModel 单元测试
 * 
 * 测试搜索和过滤功能
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchViewModelTest {
    
    @Mock
    private lateinit var inventoryRepository: InventoryRepository
    
    @Mock
    private lateinit var categoryRepository: CategoryRepository
    
    private lateinit var viewModel: SearchViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(inventoryRepository, categoryRepository)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    
    @Test
    fun `itemsFlow should expose items when no filters`() = runTest {
        val items = listOf(
            InventoryItemEntity(
                id = 1L,
                listId = 1L,
                name = "商品1",
                brand = "品牌A",
                model = "型号A",
                parameters = "",
                barcode = "",
                quantity = 10,
                remark = ""
            ),
            InventoryItemEntity(
                id = 2L,
                listId = 1L,
                name = "商品2",
                brand = "品牌B",
                model = "型号B",
                parameters = "",
                barcode = "",
                quantity = 20,
                remark = ""
            )
        )
        whenever(inventoryRepository.getItems()).thenReturn(flowOf(PagingData.from(items)))
        
        val pagingData = viewModel.itemsFlow.first()
        val snapshot = collectPagingSnapshot(pagingData)
        
        assertEquals(items, snapshot)
    }
    
    @Test
    fun `itemsFlow should expose search results when query provided`() = runTest {
        val items = listOf(
            InventoryItemEntity(
                id = 3L,
                listId = 1L,
                name = "测试商品",
                brand = "品牌C",
                model = "型号C",
                parameters = "",
                barcode = "",
                quantity = 5,
                remark = ""
            )
        )
        whenever(inventoryRepository.searchItemsPaging("测试"))
            .thenReturn(flowOf(PagingData.from(items)))
        
        viewModel.updateSearchQuery("测试")
        
        val pagingData = viewModel.itemsFlow.first()
        val snapshot = collectPagingSnapshot(pagingData)
        
        assertEquals(items, snapshot)
    }
    
    private suspend fun collectPagingSnapshot(pagingData: PagingData<InventoryItemEntity>): List<InventoryItemEntity> {
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<InventoryItemEntity>() {
                override fun areItemsTheSame(
                    oldItem: InventoryItemEntity,
                    newItem: InventoryItemEntity
                ): Boolean = oldItem.id == newItem.id
                
                override fun areContentsTheSame(
                    oldItem: InventoryItemEntity,
                    newItem: InventoryItemEntity
                ): Boolean = oldItem == newItem
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) = Unit
                override fun onRemoved(position: Int, count: Int) = Unit
                override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
            },
            mainDispatcher = testDispatcher,
            workerDispatcher = testDispatcher
        )
        differ.submitData(pagingData)
        testDispatcher.scheduler.advanceUntilIdle()
        return differ.snapshot().items
    }
}
