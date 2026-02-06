package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.StockAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 库存记录 ViewModel
 * 
 * 职责：
 * - 管理库存记录的增删改查
 * - 处理入库/出库操作
 * - 加载商品的历史记录
 */
class StockRecordViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    
    private val _records = MutableStateFlow<List<StockRecordEntity>>(emptyList())
    val records: StateFlow<List<StockRecordEntity>> = _records.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * 加载指定商品的库存记录
     */
    fun loadRecords(itemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = inventoryRepository.getRecords(itemId)
                _records.value = result
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 添加库存记录并更新商品数量
     * 
     * @param item 商品实体
     * @param action 操作类型（入库/出库）
     * @param quantity 数量（正数）
     * @param operatorName 操作员姓名
     * @param remark 备注
     * @return 操作结果（成功返回更新后的商品，失败返回null）
     */
    suspend fun addRecord(
        item: InventoryItemEntity,
        action: StockAction,
        quantity: Int,
        operatorName: String,
        remark: String
    ): Result<InventoryItemEntity> {
        // 验证数量
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("数量必须大于0"))
        }
        
        // 验证操作员姓名
        if (operatorName.isBlank()) {
            return Result.failure(IllegalArgumentException("操作员姓名不能为空"))
        }
        
        // 计算变化量
        val change = when (action) {
            StockAction.Inbound -> quantity
            StockAction.Outbound -> {
                // 验证出库数量
                if (quantity > item.quantity) {
                    return Result.failure(IllegalArgumentException("出库数量不能大于当前库存(${item.quantity})"))
                }
                -quantity
            }
            StockAction.Count -> 0 // 盘点不改变数量
        }
        
        return try {
            // 创建库存记录
            val record = StockRecordEntity(
                itemId = item.id,
                change = change,
                operatorName = operatorName.trim(),
                remark = remark.trim(),
                timestamp = System.currentTimeMillis()
            )
            
            // 使用事务方法同时更新商品和添加记录，确保数据一致性
            val updatedItem = inventoryRepository.updateItemWithRecord(
                item = item,
                change = change,
                record = record
            )
            
            // 重新加载记录
            loadRecords(item.id)
            
            Result.success(updatedItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 清除记录列表
     */
    fun clearRecords() {
        _records.value = emptyList()
    }
}
