package com.example.inventory.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Fts4

/**
 * 库存列表实体
 * 
 * 用于管理多个独立的库存列表（如"仓库1"、"仓库2"等）
 */
@Entity(
    tableName = "inventory_lists",
    indices = [
        Index(value = ["displayOrder"])  // 显示顺序索引
    ]
)
data class InventoryListEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    val name: String,              // 列表名称（如"仓库1"）
    val displayOrder: Int = 0,     // 显示顺序
    val createdAt: Long,           // 创建时间戳
    val lastModified: Long,        // 最后修改时间戳
    val isDefault: Boolean = false // 是否为默认列表
)

/**
 * FTS4 全文搜索虚拟表
 * 
 * 用于对商品信息进行高性能全文搜索
 * 映射到 inventory_items 表的特定列
 */
@Fts4(contentEntity = InventoryItemEntity::class)
@Entity(tableName = "inventory_items_fts")
data class InventoryItemFts(
    val name: String,
    val brand: String,
    val model: String,
    val barcode: String,
    val location: String,
    val remark: String
)

/**
 * 库存商品实体
 * 
 * 每个商品属于一个库存列表
 */
@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["barcode"], unique = false),  // 条码索引
        Index(value = ["name"]),                     // 名称索引，提升搜索性能
        Index(value = ["listId"]),                   // 列表ID索引，提升按列表查询性能
        Index(value = ["lastModified"]),             // 最后修改时间索引
        Index(value = ["location"]),                 // 库位索引，支持按库位查询
        Index(value = ["unit"])                      // 单位索引，支持按单位统计
    ],
    foreignKeys = [
        ForeignKey(
            entity = InventoryListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE  // 删除列表时级联删除商品
        )
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val brand: String,
    val model: String,
    val parameters: String,
    val barcode: String,
    val quantity: Int,
    val unit: String = "个",      // 计量单位，默认为"个"
    val location: String = "",    // 库位，如"A区-01货架-03层"
    val remark: String,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_records",
    indices = [
        Index(value = ["itemId"]),  // 外键索引
        Index(value = ["timestamp"])  // 时间戳索引，提升排序性能
    ]
)
data class StockRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val change: Int,
    val operatorName: String,
    val remark: String,
    val timestamp: Long
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val role: String
)
