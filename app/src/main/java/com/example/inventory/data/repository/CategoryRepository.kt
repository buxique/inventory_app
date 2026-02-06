package com.example.inventory.data.repository

import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.model.CategoryNode
import com.example.inventory.data.model.CategoryStats
import com.example.inventory.data.model.ItemCategoryEntity
import com.example.inventory.data.db.InventoryDao
import com.example.inventory.data.db.CategoryDao

/**
 * 分类仓库接口
 */
interface CategoryRepository {
    // 分类CRUD
    suspend fun getAllCategories(): List<CategoryEntity>
    suspend fun getCategoryById(id: Long): CategoryEntity?
    suspend fun addCategory(category: CategoryEntity): Long
    suspend fun updateCategory(category: CategoryEntity): Int
    suspend fun deleteCategory(id: Long): Int
    
    // 层级关系
    suspend fun getSubcategories(parentId: Long): List<CategoryEntity>
    suspend fun getRootCategories(): List<CategoryEntity>
    suspend fun getCategoryTree(): List<CategoryNode>
    suspend fun getCategoryPath(categoryId: Long): List<CategoryEntity>
    
    // 商品-分类关联
    suspend fun addItemToCategory(itemId: Long, categoryId: Long)
    suspend fun removeItemFromCategory(itemId: Long, categoryId: Long)
    suspend fun getItemCategories(itemId: Long): List<CategoryEntity>
    suspend fun getCategoryItems(categoryId: Long): List<Long>  // 返回商品ID列表
    
    // 统计信息
    suspend fun getCategoryStats(categoryId: Long): CategoryStats
    suspend fun getAllCategoryStats(): List<CategoryStats>
    
    // 排序
    suspend fun reorderCategories(categoryIds: List<Long>)
}

/**
 * 分类仓库实现
 */
class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val inventoryDao: InventoryDao
) : CategoryRepository {
    
    override suspend fun getAllCategories(): List<CategoryEntity> {
        return categoryDao.getAllCategories()
    }
    
    override suspend fun getCategoryById(id: Long): CategoryEntity? {
        return categoryDao.getCategoryById(id)
    }
    
    override suspend fun addCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category)
    }
    
    override suspend fun updateCategory(category: CategoryEntity): Int {
        return categoryDao.updateCategory(category.copy(updatedAt = System.currentTimeMillis()))
    }
    
    override suspend fun deleteCategory(id: Long): Int {
        // 删除关联关系
        categoryDao.deleteItemCategoriesByCategoryId(id)
        // 删除分类
        return categoryDao.deleteCategory(id)
    }
    
    override suspend fun getSubcategories(parentId: Long): List<CategoryEntity> {
        return categoryDao.getSubcategories(parentId)
    }
    
    override suspend fun getRootCategories(): List<CategoryEntity> {
        return categoryDao.getRootCategories()
    }
    
    override suspend fun getCategoryTree(): List<CategoryNode> {
        val rootCategories = getRootCategories()
        return rootCategories.map { buildCategoryNode(it) }
    }
    
    private suspend fun buildCategoryNode(category: CategoryEntity): CategoryNode {
        val children = getSubcategories(category.id).map { buildCategoryNode(it) }
        val itemCount = categoryDao.getCategoryItemCount(category.id)
        return CategoryNode(
            category = category,
            children = children,
            itemCount = itemCount
        )
    }
    
    override suspend fun getCategoryPath(categoryId: Long): List<CategoryEntity> {
        val path = mutableListOf<CategoryEntity>()
        var currentId: Long? = categoryId
        
        while (currentId != null) {
            val category = getCategoryById(currentId) ?: break
            path.add(0, category)  // 添加到列表开头
            currentId = category.parentId
        }
        
        return path
    }
    
    override suspend fun addItemToCategory(itemId: Long, categoryId: Long) {
        categoryDao.insertItemCategories(listOf(ItemCategoryEntity(itemId, categoryId)))
    }
    
    override suspend fun removeItemFromCategory(itemId: Long, categoryId: Long) {
        categoryDao.deleteItemCategories(listOf(itemId), categoryId)
    }
    
    override suspend fun getItemCategories(itemId: Long): List<CategoryEntity> {
        return categoryDao.getItemCategories(itemId)
    }
    
    override suspend fun getCategoryItems(categoryId: Long): List<Long> {
        return categoryDao.getCategoryItems(categoryId)
    }
    
    override suspend fun getCategoryStats(categoryId: Long): CategoryStats {
        val category = getCategoryById(categoryId) ?: throw IllegalArgumentException("Category not found")
        val itemIds = getCategoryItems(categoryId)
        val items = inventoryDao.getItemsByIds(itemIds)
        val itemCount = items.size
        val totalQuantity = items.sumOf { it.quantity }
        val subcategoryCount = getSubcategories(categoryId).size
        
        return CategoryStats(
            category = category,
            itemCount = itemCount,
            totalQuantity = totalQuantity,
            subcategoryCount = subcategoryCount
        )
    }
    
    override suspend fun getAllCategoryStats(): List<CategoryStats> {
        // 使用优化的查询，避免 N+1 问题
        val statsDto = categoryDao.getAllCategoryStatsOptimized()
        return statsDto.map { dto ->
            CategoryStats(
                category = CategoryEntity(
                    id = dto.categoryId,
                    name = dto.categoryName,
                    description = dto.categoryDescription,
                    parentId = dto.parentId,
                    sortOrder = dto.sortOrder,
                    icon = dto.icon,
                    color = dto.color,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt
                ),
                itemCount = dto.itemCount,
                totalQuantity = dto.totalQuantity,
                subcategoryCount = dto.subcategoryCount
            )
        }
    }
    
    override suspend fun reorderCategories(categoryIds: List<Long>) {
        categoryIds.forEachIndexed { index, id ->
            categoryDao.updateCategorySortOrder(id, index)
        }
    }
}
