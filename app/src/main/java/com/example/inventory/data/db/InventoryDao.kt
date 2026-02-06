package com.example.inventory.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.StockRecordEntity

import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY id DESC")
    fun getAllItems(): PagingSource<Int, InventoryItemEntity>

    @Query("SELECT * FROM inventory_items ORDER BY id DESC")
    suspend fun getAllItemsSnapshot(): List<InventoryItemEntity>

    @Query("SELECT MAX(lastModified) FROM inventory_items")
    suspend fun getMaxLastModified(): Long?

    @Insert
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<InventoryItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: InventoryItemEntity): Int

    @Update
    suspend fun updateItems(items: List<InventoryItemEntity>): Int

    @Query("DELETE FROM inventory_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long): Int

    @Query("DELETE FROM inventory_items WHERE id IN (:itemIds)")
    suspend fun deleteItems(itemIds: List<Long>): Int

    @Query("DELETE FROM inventory_items")
    suspend fun clearItems(): Int

    @Insert
    suspend fun insertRecord(record: StockRecordEntity): Long

    @Insert
    suspend fun insertRecords(records: List<StockRecordEntity>): List<Long>

    @Query("SELECT * FROM stock_records WHERE itemId = :itemId ORDER BY timestamp DESC")
    suspend fun getRecordsForItem(itemId: Long): List<StockRecordEntity>

    @Query("UPDATE inventory_items SET quantity = quantity + :change WHERE id = :itemId")
    suspend fun updateItemQuantity(itemId: Long, change: Int): Int

    @androidx.room.Transaction
    suspend fun updateItemQuantities(updates: Map<Long, Int>): Int {
        var count = 0
        updates.forEach { (itemId, change) ->
            count += updateItemQuantity(itemId, change)
        }
        return count
    }

    @Query("SELECT * FROM inventory_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<Long>): List<InventoryItemEntity>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE name LIKE :query
           OR brand LIKE :query
           OR model LIKE :query
           OR barcode LIKE :query
        ORDER BY id DESC
        """
    )
    suspend fun searchItems(query: String): List<InventoryItemEntity>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE name LIKE :query
           OR brand LIKE :query
           OR model LIKE :query
           OR barcode LIKE :query
        ORDER BY id DESC
        """
    )
    fun searchItemsPaging(query: String): PagingSource<Int, InventoryItemEntity>
    
    /**
     * 在指定分类中搜索商品（使用 SQL JOIN，避免重复查询）
     * 
     * @param query 搜索关键词（支持名称、品牌、型号、条码）
     * @param categoryId 分类ID
     * @return 符合条件的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        INNER JOIN item_categories ic ON i.id = ic.itemId
        WHERE ic.categoryId = :categoryId
          AND (i.name LIKE :query
               OR i.brand LIKE :query
               OR i.model LIKE :query
               OR i.barcode LIKE :query)
        ORDER BY i.id DESC
        """
    )
    suspend fun searchItemsInCategory(query: String, categoryId: Long): List<InventoryItemEntity>

    @Query(
        """
        SELECT i.* FROM inventory_items i
        INNER JOIN item_categories ic ON i.id = ic.itemId
        WHERE ic.categoryId = :categoryId
          AND (i.name LIKE :query
               OR i.brand LIKE :query
               OR i.model LIKE :query
               OR i.barcode LIKE :query)
        ORDER BY i.id DESC
        """
    )
    fun searchItemsInCategoryPaging(query: String, categoryId: Long): PagingSource<Int, InventoryItemEntity>

    @Query(
        """
        SELECT i.* FROM inventory_items i
        INNER JOIN item_categories ic ON i.id = ic.itemId
        WHERE ic.categoryId = :categoryId
        ORDER BY i.id DESC
        """
    )
    fun getItemsByCategoryPaging(categoryId: Long): PagingSource<Int, InventoryItemEntity>
    
    // ==================== FTS 全文搜索 ====================
    
    /**
     * FTS 全文搜索（基础搜索）
     * 
     * 使用 FTS4 虚拟表进行高性能全文搜索
     * 
     * @param query FTS 查询字符串（支持前缀匹配）
     * @return 匹配的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        JOIN inventory_items_fts fts ON i.rowid = fts.rowid
        WHERE fts.inventory_items_fts MATCH :query
        ORDER BY i.id DESC
        """
    )
    suspend fun searchItemsFts(query: String): List<InventoryItemEntity>
    
    /**
     * FTS 全文搜索（带分类过滤）
     * 
     * @param query FTS 查询字符串
     * @param categoryId 分类ID
     * @return 匹配的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        JOIN inventory_items_fts fts ON i.rowid = fts.rowid
        JOIN item_categories ic ON i.id = ic.itemId
        WHERE fts.inventory_items_fts MATCH :query
          AND ic.categoryId = :categoryId
        ORDER BY i.id DESC
        """
    )
    suspend fun searchItemsInCategoryFts(query: String, categoryId: Long): List<InventoryItemEntity>
    
    /**
     * FTS 全文搜索（带列表过滤）
     * 
     * @param listId 列表ID
     * @param query FTS 查询字符串
     * @return 匹配的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        JOIN inventory_items_fts fts ON i.rowid = fts.rowid
        WHERE fts.inventory_items_fts MATCH :query
          AND i.listId = :listId
        ORDER BY i.id DESC
        """
    )
    suspend fun searchItemsInListFts(listId: Long, query: String): List<InventoryItemEntity>
    
    /**
     * FTS 全文搜索（带列表和分类过滤）
     * 
     * @param listId 列表ID
     * @param query FTS 查询字符串
     * @param categoryId 分类ID
     * @return 匹配的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        JOIN inventory_items_fts fts ON i.rowid = fts.rowid
        JOIN item_categories ic ON i.id = ic.itemId
        WHERE fts.inventory_items_fts MATCH :query
          AND i.listId = :listId
          AND ic.categoryId = :categoryId
        ORDER BY i.id DESC
        """
    )
    suspend fun searchItemsInListAndCategoryFts(listId: Long, query: String, categoryId: Long): List<InventoryItemEntity>
    
    // ==================== 多列表支持 ====================
    
    /**
     * 按列表ID查询商品（分页）
     * 
     * @param listId 列表ID
     * @return 分页数据源
     */
    @Query("SELECT * FROM inventory_items WHERE listId = :listId ORDER BY id DESC")
    fun getItemsByListId(listId: Long): PagingSource<Int, InventoryItemEntity>
    
    /**
     * 获取指定列表的商品数量
     * 
     * @param listId 列表ID
     * @return 商品数量
     */
    @Query("SELECT COUNT(*) FROM inventory_items WHERE listId = :listId")
    suspend fun getItemCountByListId(listId: Long): Int
    
    /**
     * 在指定列表中搜索商品
     * 
     * @param listId 列表ID
     * @param query 搜索关键词（支持名称、品牌、型号、条码）
     * @return 符合条件的商品列表
     */
    @Query(
        """
        SELECT * FROM inventory_items
        WHERE listId = :listId
          AND (name LIKE :query
               OR brand LIKE :query
               OR model LIKE :query
               OR barcode LIKE :query)
        ORDER BY id DESC
        """
    )
    suspend fun searchItemsInList(listId: Long, query: String): List<InventoryItemEntity>
    
    /**
     * 在指定列表和分类中搜索商品
     * 
     * @param listId 列表ID
     * @param query 搜索关键词
     * @param categoryId 分类ID
     * @return 符合条件的商品列表
     */
    @Query(
        """
        SELECT i.* FROM inventory_items i
        INNER JOIN item_categories ic ON i.id = ic.itemId
        WHERE i.listId = :listId
          AND ic.categoryId = :categoryId
          AND (i.name LIKE :query
               OR i.brand LIKE :query
               OR i.model LIKE :query
               OR i.barcode LIKE :query)
        ORDER BY i.id DESC
        """
    )
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
    @Query(
        """
        SELECT i.* FROM inventory_items i
        INNER JOIN item_categories ic ON i.id = ic.itemId
        WHERE i.listId = :listId
          AND ic.categoryId = :categoryId
        ORDER BY i.id DESC
        """
    )
    suspend fun getItemsByListAndCategory(listId: Long, categoryId: Long): List<InventoryItemEntity>
    
    /**
     * 移动商品到其他列表
     * 
     * @param itemIds 商品ID列表
     * @param targetListId 目标列表ID
     * @return 更新的行数
     */
    @Query("UPDATE inventory_items SET listId = :targetListId WHERE id IN (:itemIds)")
    suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int
    
    /**
     * 更新商品并添加记录（事务操作）
     * 
     * 使用 @Transaction 注解确保两个操作的原子性
     * 
     * @param item 要更新的商品
     * @param record 库存记录
     */
    @androidx.room.Transaction
    suspend fun updateItemWithRecord(item: InventoryItemEntity, record: StockRecordEntity) {
        updateItem(item)
        insertRecord(record)
    }
}
