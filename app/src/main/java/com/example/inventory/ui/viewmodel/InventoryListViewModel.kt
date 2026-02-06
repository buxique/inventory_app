package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.InventoryListEntity
import com.example.inventory.data.repository.InventoryListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 视图模式枚举
 */
enum class InventoryViewMode {
    CARD, TABLE
}

/**
 * 库存列表视图模型
 * 
 * 负责管理库存列表的创建、切换、重命名和删除
 */
class InventoryListViewModel(
    private val listRepository: InventoryListRepository
) : ViewModel() {
    
    /**
     * 所有列表
     */
    val lists: StateFlow<List<InventoryListEntity>> = listRepository
        .getAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * 当前选中的列表ID
     */
    private val _currentListId = MutableStateFlow<Long?>(null)
    val currentListId: StateFlow<Long?> = _currentListId.asStateFlow()
    
    /**
     * 当前列表
     */
    val currentList: StateFlow<InventoryListEntity?> = combine(
        lists,
        currentListId
    ) { allLists, currentId ->
        allLists.find { it.id == currentId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * 当前列表的视图模式（卡片或表格）
     */
    private val _viewMode = MutableStateFlow(InventoryViewMode.CARD)
    val viewMode: StateFlow<InventoryViewMode> = _viewMode.asStateFlow()
    
    init {
        // 初始化时加载默认列表
        viewModelScope.launch {
            val defaultList = listRepository.getDefaultList()
            _currentListId.value = defaultList?.id
        }
    }

    /**
     * 切换视图模式
     */
    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == InventoryViewMode.CARD) {
            InventoryViewMode.TABLE
        } else {
            InventoryViewMode.CARD
        }
    }
    
    /**
     * 创建新列表
     * 
     * @param name 列表名称（可选，默认自动生成"仓库N"）
     * @return 新列表的ID
     */
    suspend fun createList(name: String? = null): Long {
        return listRepository.createList(name)
    }
    
    /**
     * 切换到指定列表
     * 
     * @param listId 列表ID
     */
    fun switchToList(listId: Long) {
        _currentListId.value = listId
    }
    
    /**
     * 重命名列表
     * 
     * @param listId 列表ID
     * @param newName 新名称
     * @return 成功返回 Result.success，失败返回 Result.failure
     */
    suspend fun renameList(listId: Long, newName: String): Result<Unit> {
        // 验证名称
        if (newName.isBlank()) {
            return Result.failure(IllegalArgumentException("列表名称不能为空"))
        }
        if (newName.length > 20) {
            return Result.failure(IllegalArgumentException("列表名称不能超过20个字符"))
        }
        
        // 检查名称是否已存在（排除当前列表）
        val currentList = listRepository.getListById(listId)
        if (currentList?.name != newName && listRepository.isNameExists(newName)) {
            return Result.failure(IllegalArgumentException("列表名称已存在"))
        }
        
        val result = listRepository.renameList(listId, newName)
        return if (result > 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("重命名失败"))
        }
    }
    
    /**
     * 删除列表
     * 
     * 注意：不能删除最后一个列表
     * 
     * @param listId 列表ID
     * @return 成功返回 Result.success，失败返回 Result.failure
     */
    suspend fun deleteList(listId: Long): Result<Unit> {
        val defaultListId = listRepository.getDefaultList()?.id
        if (defaultListId != null && listId == defaultListId) {
            return Result.failure(IllegalStateException("不能删除默认列表"))
        }
        val allLists = lists.value
        if (allLists.size <= 1) {
            return Result.failure(IllegalStateException("不能删除最后一个列表"))
        }
        
        val result = listRepository.deleteList(listId)
        
        // 如果删除的是当前列表，切换到第一个列表
        if (listId == _currentListId.value) {
            val firstList = allLists.firstOrNull { it.id != listId }
            _currentListId.value = firstList?.id
        }
        
        return if (result > 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("列表包含商品或为默认列表"))
        }
    }
    
    /**
     * 获取下一个默认名称
     * 
     * @return 下一个可用的默认名称（如"仓库3"）
     */
    suspend fun getNextDefaultName(): String {
        return listRepository.getNextDefaultName()
    }
}
