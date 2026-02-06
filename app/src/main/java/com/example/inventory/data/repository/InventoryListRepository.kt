package com.example.inventory.data.repository

import com.example.inventory.data.model.InventoryListEntity
import kotlinx.coroutines.flow.Flow

/**
 * 库存列表仓库接口
 * 
 * 提供库存列表的管理功能
 */
interface InventoryListRepository {
    
    /**
     * 获取所有列表（Flow，自动更新）
     * 
     * @return 按显示顺序排序的列表 Flow
     */
    fun getAllLists(): Flow<List<InventoryListEntity>>
    
    /**
     * 根据ID获取列表
     * 
     * @param id 列表ID
     * @return 列表实体，如果不存在则返回 null
     */
    suspend fun getListById(id: Long): InventoryListEntity?
    
    /**
     * 获取默认列表
     * 
     * @return 默认列表实体，如果不存在则返回 null
     */
    suspend fun getDefaultList(): InventoryListEntity?
    
    /**
     * 创建新列表
     * 
     * @param name 列表名称（可选，默认自动生成"仓库N"）
     * @return 新列表的ID
     */
    suspend fun createList(name: String? = null): Long
    
    /**
     * 重命名列表
     * 
     * @param id 列表ID
     * @param newName 新名称
     * @return 更新的行数
     */
    suspend fun renameList(id: Long, newName: String): Int
    
    /**
     * 更新列表顺序
     * 
     * @param lists 列表实体列表（包含新的 displayOrder）
     * @return 更新的行数
     */
    suspend fun updateListOrder(lists: List<InventoryListEntity>): Int
    
    /**
     * 删除列表
     * 
     * 注意：由于外键约束，删除列表会级联删除所有关联商品
     * 
     * @param id 列表ID
     * @return 删除的行数
     */
    suspend fun deleteList(id: Long): Int
    
    /**
     * 获取下一个默认名称
     * 
     * 自动生成"仓库1"、"仓库2"等名称
     * 
     * @return 下一个可用的默认名称
     */
    suspend fun getNextDefaultName(): String
    
    /**
     * 检查名称是否已存在
     * 
     * @param name 列表名称
     * @return true 表示名称已存在，false 表示名称可用
     */
    suspend fun isNameExists(name: String): Boolean
}
