package com.example.inventory.data.repository

import com.example.inventory.data.db.InventoryDao
import com.example.inventory.data.db.CategoryDao
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.ItemCategoryEntity
import com.example.inventory.data.model.StockRecordEntity
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.inventory.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * 库存仓库实现类
 * 
 * @param dao 库存数据访问对象
 * @param categoryDao 分类数据访问对象（必需，用于分类操作）
 */
class InventoryRepositoryImpl(
    private val dao: InventoryDao,
    private val categoryDao: CategoryDao  // 移除可空和默认值，改为必需参数
) : InventoryRepository {
    
    /**
     * 获取所有商品（Flow响应式）
     */
    override fun getItems(): Flow<PagingData<InventoryItemEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = Constants.Cache.ITEMS_CACHE_MAX_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.getAllItems() }
        ).flow
    }

    override suspend fun getAllItemsSnapshot(): List<InventoryItemEntity> {
        return dao.getAllItemsSnapshot()
    }

    override suspend fun getMaxLastModified(): Long? {
        return dao.getMaxLastModified()
    }

    override suspend fun addItem(item: InventoryItemEntity): Long {
        // 自动设置 lastModified
        return dao.insertItem(item.copy(lastModified = System.currentTimeMillis()))
    }

    override suspend fun updateItem(item: InventoryItemEntity): Int {
        // 自动更新 lastModified
        return dao.updateItem(item.copy(lastModified = System.currentTimeMillis()))
    }

    override suspend fun deleteItem(itemId: Long): Int {
        return dao.deleteItem(itemId)
    }

    override suspend fun clearItems(): Int {
        return dao.clearItems()
    }

    override suspend fun addRecord(record: StockRecordEntity): Long {
        return dao.insertRecord(record)
    }

    /**
     * 获取商品库存记录
     */
    override suspend fun getRecords(itemId: Long): List<StockRecordEntity> {
        if (itemId <= 0L) return emptyList()
        return dao.getRecordsForItem(itemId)
    }
    
    // ==================== 批量操作 ====================
    
    /**
     * 批量添加商品
     * 使用事务保证原子性
     */
    override suspend fun batchAddItems(items: List<InventoryItemEntity>): List<Long> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val newItems = items.map { it.copy(lastModified = now) }
        dao.insertItems(newItems)
    }
    
    /**
     * 批量更新商品
     */
    override suspend fun batchUpdateItems(items: List<InventoryItemEntity>): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        val updatedItems = items.map { it.copy(lastModified = now) }
        dao.updateItems(updatedItems)
    }
    
    /**
     * 批量删除商品
     */
    override suspend fun batchDeleteItems(itemIds: List<Long>): Int = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext 0
        dao.deleteItems(itemIds)
    }
    
    /**
     * 批量更新库存数量
     * @param updates Map<商品ID, 变化数量>
     */
    override suspend fun batchUpdateQuantity(updates: Map<Long, Int>): Int = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext 0
        dao.updateItemQuantities(updates)
    }
    
    /**
     * 批量添加库存记录
     */
    override suspend fun batchAddRecords(records: List<StockRecordEntity>): List<Long> = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext emptyList()
        dao.insertRecords(records)
    }
    
    /**
     * 批量将商品添加到分类
     */
    override suspend fun batchAddToCategory(itemIds: List<Long>, categoryId: Long) = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext
        val itemCategories = itemIds.map { ItemCategoryEntity(it, categoryId) }
        categoryDao.insertItemCategories(itemCategories)
    }
    
    /**
     * 批量从分类中移除商品
     */
    override suspend fun batchRemoveFromCategory(itemIds: List<Long>, categoryId: Long) = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext
        categoryDao.deleteItemCategories(itemIds, categoryId)
    }
    
    /**
     * 根据ID列表查询商品
     */
    override suspend fun getItemsByIds(ids: List<Long>): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        dao.getItemsByIds(ids)
    }
    
    /**
     * 搜索商品（按名称、品牌、型号、条码）
     */
    override suspend fun searchItems(query: String): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val searchPattern = "%$query%"
        dao.searchItems(searchPattern)
    }
    
    /**
     * 在指定分类中搜索商品（使用 SQL JOIN，避免重复查询）
     * 
     * @param query 搜索关键词
     * @param categoryId 分类ID
     * @return 符合条件的商品列表
     */
    override suspend fun searchItemsInCategory(query: String, categoryId: Long): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank() || categoryId <= 0L) return@withContext emptyList()
        val searchPattern = "%$query%"
        dao.searchItemsInCategory(searchPattern, categoryId)
    }

    override fun searchItemsPaging(query: String): Flow<PagingData<InventoryItemEntity>> {
        if (query.isBlank()) return flowOf(PagingData.empty())
        val searchPattern = "%$query%"
        return Pager(
            config = PagingConfig(
                pageSize = Constants.Cache.ITEMS_CACHE_MAX_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.searchItemsPaging(searchPattern) }
        ).flow
    }

    override fun searchItemsInCategoryPaging(query: String, categoryId: Long): Flow<PagingData<InventoryItemEntity>> {
        if (query.isBlank() || categoryId <= 0L) return flowOf(PagingData.empty())
        val searchPattern = "%$query%"
        return Pager(
            config = PagingConfig(
                pageSize = Constants.Cache.ITEMS_CACHE_MAX_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.searchItemsInCategoryPaging(searchPattern, categoryId) }
        ).flow
    }

    // ==================== FTS 全文搜索 ====================

    /**
     * 格式化 FTS 查询字符串
     * 添加通配符以支持前缀搜索
     */
    private fun formatFtsQuery(query: String): String {
        if (query.isBlank()) return ""
        // 将查询字符串拆分为单词，并为每个单词添加前缀通配符
        return query.trim().split("\\s+".toRegex())
            .joinToString(" ") { "$it*" }
    }
    
    /**
     * FTS 全文搜索（基础搜索）
     * 
     * 使用 FTS4 进行高性能全文搜索
     */
    override suspend fun searchItemsFts(query: String): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val ftsQuery = formatFtsQuery(query)
        dao.searchItemsFts(ftsQuery)
    }
    
    /**
     * FTS 全文搜索（带分类过滤）
     */
    override suspend fun searchItemsInCategoryFts(query: String, categoryId: Long): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank() || categoryId <= 0L) return@withContext emptyList()
        val ftsQuery = formatFtsQuery(query)
        dao.searchItemsInCategoryFts(ftsQuery, categoryId)
    }
    
    /**
     * FTS 全文搜索（带列表过滤）
     */
    override suspend fun searchItemsInListFts(listId: Long, query: String): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank() || listId <= 0L) return@withContext emptyList()
        val ftsQuery = formatFtsQuery(query)
        dao.searchItemsInListFts(listId, ftsQuery)
    }
    
    /**
     * FTS 全文搜索（带列表和分类过滤）
     */
    override suspend fun searchItemsInListAndCategoryFts(listId: Long, query: String, categoryId: Long): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank() || listId <= 0L || categoryId <= 0L) return@withContext emptyList()
        val ftsQuery = formatFtsQuery(query)
        dao.searchItemsInListAndCategoryFts(listId, ftsQuery, categoryId)
    }

    /**
     * 按分类获取商品
     */
    override suspend fun getItemsByCategory(categoryId: Long): List<InventoryItemEntity> = withContext(Dispatchers.IO) {
        if (categoryId <= 0L) return@withContext emptyList()
        val itemIds = categoryDao.getCategoryItems(categoryId)
        if (itemIds.isEmpty()) return@withContext emptyList()
        dao.getItemsByIds(itemIds)
    }

    override fun getItemsByCategoryPaging(categoryId: Long): Flow<PagingData<InventoryItemEntity>> {
        if (categoryId <= 0L) return flowOf(PagingData.empty())
        return Pager(
            config = PagingConfig(
                pageSize = Constants.Cache.ITEMS_CACHE_MAX_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.getItemsByCategoryPaging(categoryId) }
        ).flow
    }

    /**
     * 获取分类下的商品ID列表
     */
    override suspend fun getCategoryItemIds(categoryId: Long): List<Long> = withContext(Dispatchers.IO) {
        if (categoryId <= 0L) return@withContext emptyList()
        categoryDao.getCategoryItems(categoryId)
    }
    
    // ==================== 多列表支持 ====================
    
    /**
     * 按列表ID查询商品（分页）
     */
    override fun getItemsByListId(listId: Long): Flow<PagingData<InventoryItemEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = Constants.Cache.ITEMS_CACHE_MAX_SIZE,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.getItemsByListId(listId) }
        ).flow
    }
    
    /**
     * 获取指定列表的商品数量
     */
    override suspend fun getItemCountByListId(listId: Long): Int {
        return dao.getItemCountByListId(listId)
    }
    
    /**
     * 在指定列表中搜索商品
     */
    override suspend fun searchItemsInList(listId: Long, query: String): List<InventoryItemEntity> {
        val searchPattern = "%$query%"
        return dao.searchItemsInList(listId, searchPattern)
    }
    
    /**
     * 在指定列表和分类中搜索商品
     */
    override suspend fun searchItemsInListAndCategory(
        listId: Long,
        query: String,
        categoryId: Long
    ): List<InventoryItemEntity> {
        val searchPattern = "%$query%"
        return dao.searchItemsInListAndCategory(listId, searchPattern, categoryId)
    }
    
    /**
     * 按列表和分类查询商品
     */
    override suspend fun getItemsByListAndCategory(
        listId: Long,
        categoryId: Long
    ): List<InventoryItemEntity> {
        return dao.getItemsByListAndCategory(listId, categoryId)
    }
    
    /**
     * 移动商品到其他列表
     */
    override suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int {
        return dao.moveItemsToList(itemIds, targetListId)
    }
    
    /**
     * 更新商品库存并添加记录（事务操作）
     * 
     * 使用 Room 的 @Transaction 确保操作的原子性
     * 如果任一操作失败，整个事务回滚
     * 
     * @param item 要更新的商品
     * @param change 库存变化量
     * @param record 库存记录
     * @return 更新后的商品
     */
    override suspend fun updateItemWithRecord(
        item: InventoryItemEntity,
        change: Int,
        record: StockRecordEntity
    ): InventoryItemEntity = withContext(Dispatchers.IO) {
        // 计算新的库存数量
        val newQuantity = (item.quantity + change).coerceAtLeast(0)
        val updatedItem = item.copy(
            quantity = newQuantity,
            lastModified = System.currentTimeMillis()
        )
        
        // 使用事务执行更新和插入
        dao.updateItemWithRecord(updatedItem, record)
        
        // 返回更新后的商品
        updatedItem
    }
}
