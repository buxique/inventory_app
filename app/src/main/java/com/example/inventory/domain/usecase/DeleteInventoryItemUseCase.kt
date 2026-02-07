package com.example.inventory.domain.usecase

import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.domain.util.mapToAppException
import kotlinx.coroutines.CancellationException

/**
 * 删除库存商品用例
 * 
 * 封装删除商品的业务逻辑
 */
class DeleteInventoryItemUseCase(
    private val repository: InventoryRepository
) : UseCase<DeleteInventoryItemUseCase.Params, Int>() {
    
    /**
     * 参数封装
     */
    data class Params(
        val itemId: Long
    )
    
    override suspend fun invoke(params: Params): Result<Int> {
        return try {
            // 验证ID
            require(params.itemId > 0) { 
                "商品ID无效" 
            }
            
            // 执行删除操作
            val rowsAffected = repository.deleteItem(params.itemId)
            
            if (rowsAffected == 0) {
                throw IllegalStateException("商品不存在或删除失败")
            }
            
            Result.success(rowsAffected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapToAppException(e))
        }
    }
}
