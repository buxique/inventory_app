package com.example.inventory.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 商品分类实体
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,              // 分类名称
    val description: String = "",  // 分类描述
    val parentId: Long? = null,    // 父分类ID（支持多级分类）
    val sortOrder: Int = 0,        // 排序顺序
    val icon: String = "",         // 图标（可选）
    val color: String = "",        // 颜色标识（可选）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 商品-分类关联实体
 */
@Entity(
    tableName = "item_categories",
    primaryKeys = ["itemId", "categoryId"],
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["categoryId"])
    ]
)
data class ItemCategoryEntity(
    val itemId: Long,       // 商品ID
    val categoryId: Long    // 分类ID
)

/**
 * 分类统计信息
 */
data class CategoryStats(
    val category: CategoryEntity,
    val itemCount: Int,          // 商品数量
    val totalQuantity: Int,      // 总库存
    val subcategoryCount: Int    // 子分类数量
)

/**
 * 分类树节点
 */
data class CategoryNode(
    val category: CategoryEntity,
    val children: List<CategoryNode>,
    val itemCount: Int
)
