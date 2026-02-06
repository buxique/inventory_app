package com.example.inventory.ui.viewmodel

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.example.inventory.data.AppContainer

/**
 * ViewModel 工厂类
 * 
 * 负责创建所有 ViewModel 实例，支持：
 * - InventoryViewModelRefactored（重构后的主 ViewModel）
 * - 独立的子 ViewModel（SearchViewModel、CategoryViewModel 等）
 * - CaptureViewModel（拍照和 OCR）
 * - SettingsViewModel（设置）
 * - InventoryListViewModel（多列表管理）
 * - ImportViewModel（导入）
 */
class AppViewModelFactory(
    val container: AppContainer,  // 改为 public，以便在路由中访问
    owner: SavedStateRegistryOwner
) : AbstractSavedStateViewModelFactory(owner, null) {
    
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val viewModel = when {
            // 重构后的 InventoryViewModel（推荐使用）
            modelClass.isAssignableFrom(InventoryViewModelRefactored::class.java) ->
                InventoryViewModelRefactored(
                    inventoryRepository = container.inventoryRepository,
                    importCoordinator = container.importCoordinator,
                    searchViewModel = SearchViewModel(
                        container.inventoryRepository,
                        container.categoryRepository
                    ),
                    categoryViewModel = CategoryViewModel(
                        container.categoryRepository
                    ),
                    itemOperationViewModel = ItemOperationViewModel(
                        container.inventoryRepository
                    ),
                    stockRecordViewModel = StockRecordViewModel(
                        container.inventoryRepository
                    ),
                    dialogManager = DialogStateManager()
                )
            
            // 独立的搜索 ViewModel
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(
                    container.inventoryRepository,
                    container.categoryRepository
                )
            
            // 独立的分类 ViewModel
            modelClass.isAssignableFrom(CategoryViewModel::class.java) ->
                CategoryViewModel(
                    container.categoryRepository
                )
            
            // 独立的商品操作 ViewModel
            modelClass.isAssignableFrom(ItemOperationViewModel::class.java) ->
                ItemOperationViewModel(
                    container.inventoryRepository
                )
            
            // 独立的库存记录 ViewModel
            modelClass.isAssignableFrom(StockRecordViewModel::class.java) ->
                StockRecordViewModel(
                    container.inventoryRepository
                )
            
            // 拍照页面 ViewModel
            modelClass.isAssignableFrom(CaptureViewModel::class.java) ->
                CaptureViewModel(
                    container.ocrRepository
                )
            
            // 设置页面 ViewModel
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    application = container.application,
                    authRepository = container.authRepository,
                    exportRepository = container.exportRepository,
                    storageRepository = container.storageRepository,
                    inventoryRepository = container.inventoryRepository,
                    syncRepository = container.syncRepository
                )
            
            // 库存列表 ViewModel
            modelClass.isAssignableFrom(InventoryListViewModel::class.java) ->
                InventoryListViewModel(
                    container.inventoryListRepository
                )
            
            // 导入 ViewModel
            modelClass.isAssignableFrom(ImportViewModel::class.java) ->
                ImportViewModel(
                    container.importCoordinator,
                    container.inventoryRepository
                )
            
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return modelClass.cast(viewModel) ?: throw IllegalStateException("ViewModel cast failed")
    }
}
