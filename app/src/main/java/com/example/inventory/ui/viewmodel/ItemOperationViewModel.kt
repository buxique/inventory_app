package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.domain.util.mapToAppException
import com.example.inventory.domain.usecase.AddInventoryItemUseCase
import com.example.inventory.domain.usecase.DeleteInventoryItemUseCase
import com.example.inventory.domain.usecase.UpdateInventoryItemUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 商品操作 ViewModel
 * 
 * 职责：
 * - 添加商品
 * - 更新商品
 * - 删除商品
 * - 复制粘贴商品
 */
class ItemOperationViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    
    private val addInventoryItemUseCase = AddInventoryItemUseCase(inventoryRepository)
    private val updateInventoryItemUseCase = UpdateInventoryItemUseCase(inventoryRepository)
    private val deleteInventoryItemUseCase = DeleteInventoryItemUseCase(inventoryRepository)
    
    private val _copiedItem = MutableStateFlow<InventoryItemEntity?>(null)
    val copiedItem: StateFlow<InventoryItemEntity?> = _copiedItem.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 添加商品
     */
    suspend fun addItem(item: InventoryItemEntity): Result<Long> {
        return addInventoryItemUseCase.invoke(AddInventoryItemUseCase.Params(item))
    }
    
    /**
     * 更新商品
     */
    suspend fun updateItem(item: InventoryItemEntity): Result<Unit> {
        return updateInventoryItemUseCase.invoke(UpdateInventoryItemUseCase.Params(item))
            .map { Unit }
    }
    
    /**
     * 删除商品
     */
    suspend fun deleteItem(itemId: Long): Result<Unit> {
        return deleteInventoryItemUseCase.invoke(DeleteInventoryItemUseCase.Params(itemId))
            .map { Unit }
    }
    
    /**
     * 批量添加商品
     */
    suspend fun batchAddItems(items: List<InventoryItemEntity>): Result<Unit> {
        return try {
            inventoryRepository.batchAddItems(items)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapToAppException(e))
        }
    }
    
    /**
     * 复制商品
     */
    fun copyItem(item: InventoryItemEntity) {
        _copiedItem.value = item
    }
    
    /**
     * 粘贴商品（创建副本）
     */
    suspend fun pasteItem(): Result<Long> {
        val base = _copiedItem.value
            ?: return Result.failure(IllegalStateException("没有可粘贴的商品"))
        
        return try {
            val newItem = base.copy(
                id = 0,
                name = base.name.ifBlank { "商品" } + "-副本",
                lastModified = System.currentTimeMillis()
            )
            val id = inventoryRepository.addItem(newItem)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapToAppException(e))
        }
    }
    
    /**
     * 清除复制的商品
     */
    fun clearCopiedItem() {
        _copiedItem.value = null
    }
    
    /**
     * 检查是否有可粘贴的商品
     */
    fun hasCopiedItem(): Boolean = _copiedItem.value != null
}
