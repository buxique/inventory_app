package com.example.inventory.data.repository

import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * 库存仓库接口
 * 
 * 所有挂起函数可能抛出以下异常：
 * - [IllegalArgumentException]: 参数校验失败
 * - [IllegalStateException]: 数据库状态异常
 * - [java.io.IOException]: IO 操作失败
 * - [android.database.SQLException]: 数据库操作失败
 */
interface InventoryRepository {
    // 单项操作
    fun getItems(): Flow<PagingData<InventoryItemEntity>>
    suspend fun getAllItemsSnapshot(): List<InventoryItemEntity>
    suspend fun getMaxLastModified(): Long?
    suspend fun addItem(item: InventoryItemEntity): Long
    suspend fun updateItem(item: InventoryItemEntity): Int
    suspend fun deleteItem(itemId: Long): Int
    suspend fun clearItems(): Int
    suspend fun addRecord(record: StockRecordEntity): Long
    suspend fun getRecords(itemId: Long): List<StockRecordEntity>
    
    // 批量操作
    suspend fun batchAddItems(items: List<InventoryItemEntity>): List<Long>
    suspend fun batchUpdateItems(items: List<InventoryItemEntity>): Int
    suspend fun batchDeleteItems(itemIds: List<Long>): Int
    suspend fun batchUpdateQuantity(updates: Map<Long, Int>): Int
    suspend fun batchAddRecords(records: List<StockRecordEntity>): List<Long>
    
    // 批量分类操作
    suspend fun batchAddToCategory(itemIds: List<Long>, categoryId: Long)
    suspend fun batchRemoveFromCategory(itemIds: List<Long>, categoryId: Long)
    
    // 查询优化
    suspend fun getItemsByIds(ids: List<Long>): List<InventoryItemEntity>
    suspend fun searchItems(query: String): List<InventoryItemEntity>
    suspend fun searchItemsInCategory(query: String, categoryId: Long): List<InventoryItemEntity>
    suspend fun getItemsByCategory(categoryId: Long): List<InventoryItemEntity>
    fun searchItemsPaging(query: String): Flow<PagingData<InventoryItemEntity>>
    fun searchItemsInCategoryPaging(query: String, categoryId: Long): Flow<PagingData<InventoryItemEntity>>
    fun getItemsByCategoryPaging(categoryId: Long): Flow<PagingData<InventoryItemEntity>>
    suspend fun getCategoryItemIds(categoryId: Long): List<Long>
    
    // FTS 全文搜索
    suspend fun searchItemsFts(query: String): List<InventoryItemEntity>
    suspend fun searchItemsInCategoryFts(query: String, categoryId: Long): List<InventoryItemEntity>
    suspend fun searchItemsInListFts(listId: Long, query: String): List<InventoryItemEntity>
    suspend fun searchItemsInListAndCategoryFts(listId: Long, query: String, categoryId: Long): List<InventoryItemEntity>
    
    // ==================== 多列表支持 ====================
    
    /**
     * 按列表ID查询商品（分页）
     * 
     * @param listId 列表ID
     * @return 分页数据流
     */
    fun getItemsByListId(listId: Long): Flow<PagingData<InventoryItemEntity>>
    
    /**
     * 获取指定列表的商品数量
     * 
     * @param listId 列表ID
     * @return 商品数量
     */
    suspend fun getItemCountByListId(listId: Long): Int
    
    /**
     * 在指定列表中搜索商品
     * 
     * @param listId 列表ID
     * @param query 搜索关键词
     * @return 符合条件的商品列表
     */
    suspend fun searchItemsInList(listId: Long, query: String): List<InventoryItemEntity>
    
    /**
     * 在指定列表和分类中搜索商品
     * 
     * @param listId 列表ID
     * @param query 搜索关键词
     * @param categoryId 分类ID
     * @return 符合条件的商品列表
     */
    suspend fun searchItemsInListAndCategory(
        listId: Long,
        query: String,
        categoryId: Long
    ): List<InventoryItemEntity>
    
    /**
     * 按列表和分类查询商品
     * 
     * @param listId 列表ID
     * @param categoryId 分类ID
     * @return 符合条件的商品列表
     */
    suspend fun getItemsByListAndCategory(listId: Long, categoryId: Long): List<InventoryItemEntity>
    
    /**
     * 移动商品到其他列表
     * 
     * @param itemIds 商品ID列表
     * @param targetListId 目标列表ID
     * @return 更新的行数
     */
    suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int
    
    /**
     * 更新商品库存并添加记录（事务操作）
     * 
     * 使用数据库事务确保库存更新和记录添加的原子性
     * 
     * @param item 要更新的商品
     * @param change 库存变化量（正数为入库，负数为出库）
     * @param record 库存记录
     * @return 更新后的商品
     */
    suspend fun updateItemWithRecord(
        item: InventoryItemEntity,
        change: Int,
        record: StockRecordEntity
    ): InventoryItemEntity
}

