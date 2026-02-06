package com.example.inventory.data.db

import androidx.room.*
import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.model.ItemCategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long): Int

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC")
    suspend fun getSubcategories(parentId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE parentId IS NULL ORDER BY sortOrder ASC")
    suspend fun getRootCategories(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM item_categories WHERE categoryId = :categoryId")
    suspend fun getCategoryItemCount(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemCategories(itemCategories: List<ItemCategoryEntity>)

    @Query("DELETE FROM item_categories WHERE itemId IN (:itemIds) AND categoryId = :categoryId")
    suspend fun deleteItemCategories(itemIds: List<Long>, categoryId: Long)

    @Query("DELETE FROM item_categories WHERE categoryId = :categoryId")
    suspend fun deleteItemCategoriesByCategoryId(categoryId: Long)

    @Query("""
        SELECT c.* FROM categories c
        INNER JOIN item_categories ic ON c.id = ic.categoryId
        WHERE ic.itemId = :itemId
    """)
    suspend fun getItemCategories(itemId: Long): List<CategoryEntity>

    @Query("SELECT itemId FROM item_categories WHERE categoryId = :categoryId")
    suspend fun getCategoryItems(categoryId: Long): List<Long>

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :categoryId")
    suspend fun updateCategorySortOrder(categoryId: Long, sortOrder: Int)
    
    /**
     * 优化的分类统计查询（避免 N+1 问题）
     * 
     * 使用 SQL JOIN 一次性获取所有分类的统计信息
     * 
     * @return 分类统计数据传输对象列表
     */
    @Query("""
        SELECT 
            c.id as categoryId,
            c.name as categoryName,
            c.description as categoryDescription,
            c.parentId as parentId,
            c.sortOrder as sortOrder,
            c.icon as icon,
            c.color as color,
            c.createdAt as createdAt,
            c.updatedAt as updatedAt,
            COUNT(DISTINCT ic.itemId) as itemCount,
            COALESCE(SUM(i.quantity), 0) as totalQuantity,
            (SELECT COUNT(*) FROM categories sc WHERE sc.parentId = c.id) as subcategoryCount
        FROM categories c
        LEFT JOIN item_categories ic ON c.id = ic.categoryId
        LEFT JOIN inventory_items i ON ic.itemId = i.id
        GROUP BY c.id, c.name, c.description, c.parentId, c.sortOrder, c.icon, c.color, c.createdAt, c.updatedAt
    """)
    suspend fun getAllCategoryStatsOptimized(): List<CategoryStatsDto>
}

/**
 * 分类统计数据传输对象
 * 
 * 用于从数据库查询中接收统计信息
 */
data class CategoryStatsDto(
    val categoryId: Long,
    val categoryName: String,
    val categoryDescription: String,
    val parentId: Long?,
    val sortOrder: Int,
    val icon: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
    val totalQuantity: Int,
    val subcategoryCount: Int
)
