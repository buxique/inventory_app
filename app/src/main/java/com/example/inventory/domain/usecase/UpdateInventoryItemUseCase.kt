package com.example.inventory.domain.usecase

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.domain.util.mapToAppException
import com.example.inventory.util.Constants
import kotlinx.coroutines.CancellationException

/**
 * 更新库存商品用例
 * 
 * 封装更新商品的业务逻辑和验证规则
 */
class UpdateInventoryItemUseCase(
    private val repository: InventoryRepository
) : UseCase<UpdateInventoryItemUseCase.Params, Int>() {
    
    /**
     * 参数封装
     */
    data class Params(
        val item: InventoryItemEntity
    )
    
    override suspend fun invoke(params: Params): Result<Int> {
        return try {
            // 业务规则验证
            validateItem(params.item)
            
            // 执行更新操作
            val rowsAffected = repository.updateItem(params.item)
            
            if (rowsAffected == 0) {
                throw IllegalStateException("商品不存在或更新失败")
            }
            
            Result.success(rowsAffected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapToAppException(e))
        }
    }
    
    /**
     * 验证商品数据
     */
    private fun validateItem(item: InventoryItemEntity) {
        require(item.id > 0) { 
            "商品ID无效" 
        }
        
        require(item.name.isNotBlank()) { 
            "商品名称不能为空" 
        }
        
        require(item.name.length <= 100) { 
            "商品名称不能超过100个字符" 
        }
        
        require(item.quantity >= 0) { 
            "库存数量不能为负数" 
        }
        
        require(item.quantity <= Constants.Import.MAX_QUANTITY) { 
            "库存数量不能超过 ${Constants.Import.MAX_QUANTITY}" 
        }
        
        if (item.barcode.isNotBlank()) {
            require(item.barcode.matches(Regex("^[0-9]{8,13}$"))) { 
                "条码格式不正确" 
            }
        }
    }
}
