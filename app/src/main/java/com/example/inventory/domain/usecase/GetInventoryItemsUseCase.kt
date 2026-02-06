package com.example.inventory.domain.usecase

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.InventoryRepository
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * 获取库存商品列表用例
 * 
 * 返回响应式的商品列表流
 */
class GetInventoryItemsUseCase(
    private val repository: InventoryRepository
) : NoParamUseCase<Flow<PagingData<InventoryItemEntity>>>() {
    
    override suspend fun invoke(): Result<Flow<PagingData<InventoryItemEntity>>> {
        return try {
            val itemsFlow = repository.getItems()
            Result.success(itemsFlow)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
