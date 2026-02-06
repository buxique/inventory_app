package com.example.inventory.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.paging.PagingData
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.state.StockAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * InventoryViewModel 单元测试
 * 
 * 测试旧版 InventoryViewModel 的基本功能
 * 注意：这是为了向后兼容保留的测试，新功能应使用 InventoryViewModelRefactored
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Mock
    private lateinit var mockRepository: InventoryRepository
    
    @Mock
    private lateinit var mockImportCoordinator: ImportCoordinator
    
    @Mock
    private lateinit var mockCategoryRepository: CategoryRepository
    
    private lateinit var viewModel: InventoryViewModel
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock 分类仓库返回空列表
        runTest {
            `when`(mockCategoryRepository.getAllCategories()).thenReturn(emptyList())
        }
        
        viewModel = InventoryViewModel(
            mockRepository, 
            mockImportCoordinator,
            mockCategoryRepository
        )
    }
    
    @Test
    fun `init should load categories`() = runTest {
        // Given
        val expectedCategories = listOf(
            CategoryEntity(id = 1L, name = "分类1"),
            CategoryEntity(id = 2L, name = "分类2")
        )
        `when`(mockCategoryRepository.getAllCategories()).thenReturn(expectedCategories)
        
        // When
        val newViewModel = InventoryViewModel(
            mockRepository,
            mockImportCoordinator,
            mockCategoryRepository
        )
        advanceUntilIdle()
        
        // Then
        verify(mockCategoryRepository).getAllCategories()
        assertEquals(expectedCategories, newViewModel.state.value.categories)
    }
    
    @Test
    fun `showSearchDialog should update dialog state`() {
        // When
        viewModel.showSearchDialog()
        
        // Then
        val state = viewModel.state.value
        assertEquals(DialogState.SearchDialog, state.dialogState)
    }
    
    @Test
    fun `updateSearchQuery should update search query`() {
        // Given
        val query = "测试查询"
        
        // When
        viewModel.updateSearchQuery(query)
        
        // Then
        assertEquals(query, viewModel.state.value.searchQuery)
    }
    
    @Test
    fun `clearSearch should reset search query`() {
        // Given
        viewModel.updateSearchQuery("测试")
        
        // When
        viewModel.clearSearch()
        
        // Then
        assertEquals("", viewModel.state.value.searchQuery)
    }
    
    @Test
    fun `showEdit should update edit state`() {
        // Given
        val item = InventoryItemEntity(
            id = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )

        // When
        viewModel.showEdit(item)

        // Then
        val state = viewModel.state.value
        assertEquals(item, state.selectedItem)
        assertEquals(item.name, state.editText)
        assertEquals(DialogState.EditDialog, state.dialogState)
    }
    
    @Test
    fun `updateEditText should update edit text`() {
        // Given
        val newText = "新名称"
        
        // When
        viewModel.updateEditText(newText)
        
        // Then
        assertEquals(newText, viewModel.state.value.editText)
    }
    
    @Test
    fun `showStockActions should update dialog and selected item`() {
        // Given
        val item = InventoryItemEntity(
            id = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )
        
        // When
        viewModel.showStockActions(item)
        
        // Then
        val state = viewModel.state.value
        assertEquals(item, state.selectedItem)
        assertEquals(DialogState.StockActionDialog, state.dialogState)
    }
    
    @Test
    fun `showRecordInput should update stock action state`() {
        // When
        viewModel.showRecordInput(StockAction.Outbound)
        
        // Then
        val state = viewModel.state.value
        assertEquals(StockAction.Outbound, state.stockActionState.action)
    }
    
    @Test
    fun `updateRecordQuantity should update quantity`() {
        // Given
        val quantity = "50"
        
        // When
        viewModel.updateRecordQuantity(quantity)
        
        // Then
        assertEquals(quantity, viewModel.state.value.stockActionState.quantity)
    }
    
    @Test
    fun `updateRecordOperator should update operator`() {
        // Given
        val operator = "张三"
        
        // When
        viewModel.updateRecordOperator(operator)
        
        // Then
        assertEquals(operator, viewModel.state.value.stockActionState.operator)
    }
    
    @Test
    fun `copyItem should store item in state`() {
        // Given
        val item = InventoryItemEntity(
            id = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )
        
        // When
        viewModel.copyItem(item)
        
        // Then
        assertEquals(item, viewModel.state.value.copiedItem)
    }
    
    @Test
    fun `dismissDialog should set dialog to None`() {
        // Given
        viewModel.showSearchDialog()
        
        // When
        viewModel.dismissDialog()
        
        // Then
        assertEquals(DialogState.None, viewModel.state.value.dialogState)
    }
    
    @Test
    fun `addCategory should call repository`() = runTest {
        // Given
        val categoryName = "新分类"
        
        // When
        viewModel.addCategory(categoryName)
        advanceUntilIdle()
        
        // Then
        verify(mockCategoryRepository).addCategory(
            org.mockito.kotlin.argThat { category -> category.name == categoryName }
        )
    }
}
