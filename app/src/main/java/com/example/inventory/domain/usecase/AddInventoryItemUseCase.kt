package com.example.inventory.domain.usecase

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.domain.model.AppException
import com.example.inventory.domain.util.safeCall
import com.example.inventory.util.Constants

/**
 * 添加库存商品用例
 * 
 * 封装添加商品的业务逻辑和验证规则
 */
class AddInventoryItemUseCase(
    private val repository: InventoryRepository
) : UseCase<AddInventoryItemUseCase.Params, Long>() {
    
    /**
     * 参数封装
     */
    data class Params(
        val item: InventoryItemEntity
    )
    
    override suspend fun invoke(params: Params): Result<Long> {
        return try {
            // 业务规则验证
            validateItem(params.item)
            
            // 执行添加操作
            val id = repository.addItem(params.item)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 验证商品数据
     * 
     * @param item 商品实体
     * @throws AppException.ValidationException 验证失败时抛出
     */
    private fun validateItem(item: InventoryItemEntity) {
        // 验证商品名称
        require(item.name.isNotBlank()) { 
            "商品名称不能为空" 
        }
        
        require(item.name.length <= 100) { 
            "商品名称不能超过100个字符" 
        }
        
        // 验证库存数量
        require(item.quantity >= 0) { 
            "库存数量不能为负数" 
        }
        
        require(item.quantity <= Constants.Import.MAX_QUANTITY) { 
            "库存数量不能超过 ${Constants.Import.MAX_QUANTITY}" 
        }
        
        // 验证条码格式（如果提供）
        if (item.barcode.isNotBlank()) {
            require(item.barcode.matches(Regex("^[0-9]{8,13}$"))) { 
                "条码格式不正确，应为8-13位数字" 
            }
        }
    }
}
