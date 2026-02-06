package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分类管理 ViewModel
 * 
 * 职责：
 * - 加载分类列表
 * - 添加新分类
 * - 删除分类
 */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadCategories()
    }
    
    /**
     * 加载所有分类
     */
    fun loadCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = categoryRepository.getAllCategories()
                _categories.value = result
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 添加新分类
     * 
     * @return 是否添加成功
     */
    suspend fun addCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return false
        }
        
        return try {
            categoryRepository.addCategory(CategoryEntity(name = trimmed))
            loadCategories()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 删除分类
     */
    suspend fun deleteCategory(categoryId: Long): Boolean {
        return try {
            categoryRepository.deleteCategory(categoryId)
            loadCategories()
            true
        } catch (e: Exception) {
            false
        }
    }
}
