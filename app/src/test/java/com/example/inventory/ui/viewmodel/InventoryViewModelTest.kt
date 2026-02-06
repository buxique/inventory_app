package com.example.inventory.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.DialogState
import com.example.inventory.ui.state.StockAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals

/**
 * InventoryViewModelRefactored 单元测试
 * 
 * 覆盖常用对话框与交互状态
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
    
    private lateinit var viewModel: InventoryViewModelRefactored
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock 分类仓库返回空列表
        runTest {
            whenever(mockCategoryRepository.getAllCategories()).thenReturn(emptyList())
        }
        
        val searchViewModel = SearchViewModel(mockRepository, mockCategoryRepository)
        val categoryViewModel = CategoryViewModel(mockCategoryRepository)
        val itemOperationViewModel = ItemOperationViewModel(mockRepository)
        val stockRecordViewModel = StockRecordViewModel(mockRepository)

        viewModel = InventoryViewModelRefactored(
            inventoryRepository = mockRepository,
            importCoordinator = mockImportCoordinator,
            searchViewModel = searchViewModel,
            categoryViewModel = categoryViewModel,
            itemOperationViewModel = itemOperationViewModel,
            stockRecordViewModel = stockRecordViewModel,
            dialogManager = DialogStateManager()
        )
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `init should load categories`() = runTest {
        val expectedCategories = listOf(
            CategoryEntity(id = 1L, name = "分类1"),
            CategoryEntity(id = 2L, name = "分类2")
        )
        whenever(mockCategoryRepository.getAllCategories()).thenReturn(expectedCategories)
        clearInvocations(mockCategoryRepository)
        
        val searchViewModel = SearchViewModel(mockRepository, mockCategoryRepository)
        val categoryViewModel = CategoryViewModel(mockCategoryRepository)
        val itemOperationViewModel = ItemOperationViewModel(mockRepository)
        val stockRecordViewModel = StockRecordViewModel(mockRepository)
        val newViewModel = InventoryViewModelRefactored(
            inventoryRepository = mockRepository,
            importCoordinator = mockImportCoordinator,
            searchViewModel = searchViewModel,
            categoryViewModel = categoryViewModel,
            itemOperationViewModel = itemOperationViewModel,
            stockRecordViewModel = stockRecordViewModel,
            dialogManager = DialogStateManager()
        )
        advanceUntilIdle()
        val state = newViewModel.uiState.first { it.categories == expectedCategories }
        
        verify(mockCategoryRepository).getAllCategories()
        assertEquals(expectedCategories, state.categories)
    }
    
    @Test
    fun `showSearchDialog should update dialog state`() = runTest {
        viewModel.showSearchDialog()
        
        val state = viewModel.uiState.first { it.dialogState == DialogState.SearchDialog }
        assertEquals(DialogState.SearchDialog, state.dialogState)
    }
    
    @Test
    fun `updateSearchQuery should update search query`() = runTest {
        val query = "测试查询"
        
        viewModel.updateSearchQuery(query)
        
        val state = viewModel.uiState.first { it.searchQuery == query }
        assertEquals(query, state.searchQuery)
    }
    
    @Test
    fun `clearSearch should reset search query`() = runTest {
        viewModel.updateSearchQuery("测试")
        
        viewModel.clearSearch()
        
        val state = viewModel.uiState.first { it.searchQuery.isEmpty() }
        assertEquals("", state.searchQuery)
    }
    
    @Test
    fun `showEdit should update edit state`() = runTest {
        val item = InventoryItemEntity(
            id = 1,
            listId = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )

        viewModel.showEdit(item)

        val state = viewModel.uiState.first { it.dialogState == DialogState.EditDialog }
        assertEquals(item, state.selectedItem)
        assertEquals(item.name, state.editText)
        assertEquals(DialogState.EditDialog, state.dialogState)
    }
    
    @Test
    fun `updateEditText should update edit text`() = runTest {
        val newText = "新名称"
        
        viewModel.updateEditText(newText)
        
        val state = viewModel.uiState.first { it.editText == newText }
        assertEquals(newText, state.editText)
    }
    
    @Test
    fun `showStockActions should update dialog and selected item`() = runTest {
        val item = InventoryItemEntity(
            id = 1,
            listId = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )
        
        viewModel.showStockActions(item)
        
        val state = viewModel.uiState.first { it.dialogState == DialogState.StockActionDialog }
        assertEquals(item, state.selectedItem)
        assertEquals(DialogState.StockActionDialog, state.dialogState)
    }
    
    @Test
    fun `showRecordInput should update stock action state`() = runTest {
        viewModel.showRecordInput(StockAction.Outbound)
        
        val state = viewModel.uiState.first { it.stockActionState.action == StockAction.Outbound }
        assertEquals(StockAction.Outbound, state.stockActionState.action)
    }
    
    @Test
    fun `updateRecordQuantity should update quantity`() = runTest {
        val quantity = "50"
        
        viewModel.updateRecordQuantity(quantity)
        
        val state = viewModel.uiState.first { it.stockActionState.quantity == quantity }
        assertEquals(quantity, state.stockActionState.quantity)
    }
    
    @Test
    fun `updateRecordOperator should update operator`() = runTest {
        val operator = "张三"
        
        viewModel.updateRecordOperator(operator)
        
        val state = viewModel.uiState.first { it.stockActionState.operator == operator }
        assertEquals(operator, state.stockActionState.operator)
    }
    
    @Test
    fun `copyItem should store item in state`() = runTest {
        val item = InventoryItemEntity(
            id = 1,
            listId = 1,
            name = "测试商品",
            brand = "品牌",
            model = "型号",
            parameters = "",
            barcode = "",
            quantity = 10,
            remark = ""
        )
        
        viewModel.copyItem(item)
        
        assertEquals(item, viewModel.itemOperationViewModel.copiedItem.first())
    }
    
    @Test
    fun `dismissDialog should set dialog to None`() = runTest {
        viewModel.showSearchDialog()
        
        viewModel.dismissDialog()
        
        val state = viewModel.uiState.first { it.dialogState == DialogState.None }
        assertEquals(DialogState.None, state.dialogState)
    }
    
    @Test
    fun `addCategory should call repository`() = runTest {
        val categoryName = "新分类"
        whenever(mockCategoryRepository.addCategory(any())).thenAnswer { }
        
        viewModel.addCategory(categoryName)
        advanceUntilIdle()
        
        verify(mockCategoryRepository).addCategory(
            org.mockito.kotlin.argThat { category -> category.name == categoryName }
        )
    }
}
