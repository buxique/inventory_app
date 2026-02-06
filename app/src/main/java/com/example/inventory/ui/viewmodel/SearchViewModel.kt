package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.InventoryRepository
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 搜索和过滤 ViewModel
 * 
 * 职责：
 * - 管理搜索查询
 * - 管理分类过滤
 * - 提供分页数据流
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val inventoryRepository: InventoryRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()
    
    // 分页数据流（支持搜索与分类过滤）
    val itemsFlow: Flow<PagingData<InventoryItemEntity>> = combine(
        _searchQuery,
        _selectedCategoryId
    ) { query, categoryId ->
        query to categoryId
    }.flatMapLatest { (query, categoryId) ->
        when {
            // 同时有搜索和分类：使用组合查询
            query.isNotBlank() && categoryId != null -> {
                inventoryRepository.searchItemsInCategoryPaging(query, categoryId)
            }
            // 仅搜索
            query.isNotBlank() -> inventoryRepository.searchItemsPaging(query)
            // 仅分类
            categoryId != null -> inventoryRepository.getItemsByCategoryPaging(categoryId)
            // 无过滤：使用分页查询
            else -> inventoryRepository.getItems()
        }
    }.cachedIn(viewModelScope)
    
    /**
     * 更新搜索查询
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }
    
    /**
     * 清除搜索
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    /**
     * 选择分类
     */
    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }
    
    /**
     * 清除分类过滤
     */
    fun clearCategoryFilter() {
        _selectedCategoryId.value = null
    }
}
