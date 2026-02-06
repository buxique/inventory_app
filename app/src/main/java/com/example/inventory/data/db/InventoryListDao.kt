package com.example.inventory.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.inventory.data.model.InventoryListEntity
import kotlinx.coroutines.flow.Flow

/**
 * 库存列表数据访问对象
 * 
 * 提供库存列表的 CRUD 操作
 */
@Dao
interface InventoryListDao {
    
    /**
     * 获取所有列表（Flow，自动更新）
     * 
     * @return 按显示顺序排序的列表 Flow
     */
    @Query("SELECT * FROM inventory_lists ORDER BY displayOrder ASC")
    fun getAllLists(): Flow<List<InventoryListEntity>>
    
    /**
     * 获取所有列表（快照，一次性查询）
     * 
     * @return 按显示顺序排序的列表
     */
    @Query("SELECT * FROM inventory_lists ORDER BY displayOrder ASC")
    suspend fun getAllListsSnapshot(): List<InventoryListEntity>
    
    /**
     * 根据ID获取列表
     * 
     * @param id 列表ID
     * @return 列表实体，如果不存在则返回 null
     */
    @Query("SELECT * FROM inventory_lists WHERE id = :id")
    suspend fun getListById(id: Long): InventoryListEntity?
    
    /**
     * 根据名称获取列表
     * 
     * @param name 列表名称
     * @return 列表实体，如果不存在则返回 null
     */
    @Query("SELECT * FROM inventory_lists WHERE name = :name")
    suspend fun getListByName(name: String): InventoryListEntity?
    
    /**
     * 获取默认列表
     * 
     * @return 默认列表实体，如果不存在则返回 null
     */
    @Query("SELECT * FROM inventory_lists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultList(): InventoryListEntity?
    
    /**
     * 获取最大显示顺序
     * 
     * @return 最大显示顺序，如果没有列表则返回 null
     */
    @Query("SELECT MAX(displayOrder) FROM inventory_lists")
    suspend fun getMaxDisplayOrder(): Int?
    
    /**
     * 插入新列表
     * 
     * @param list 列表实体
     * @return 新列表的ID
     */
    @Insert
    suspend fun insertList(list: InventoryListEntity): Long
    
    /**
     * 批量更新列表
     * 
     * @param lists 列表实体列表
     * @return 更新的行数
     */
    @Update
    suspend fun updateLists(lists: List<InventoryListEntity>): Int
    
    /**
     * 更新列表名称
     * 
     * @param id 列表ID
     * @param newName 新名称
     * @param timestamp 更新时间戳
     * @return 更新的行数
     */
    @Query("UPDATE inventory_lists SET name = :newName, lastModified = :timestamp WHERE id = :id")
    suspend fun updateListName(id: Long, newName: String, timestamp: Long): Int
    
    /**
     * 删除列表
     * 
     * 注意：由于外键约束，删除列表会级联删除所有关联商品
     * 
     * @param id 列表ID
     * @return 删除的行数
     */
    @Query(
        """
        DELETE FROM inventory_lists
        WHERE id = :id
          AND isDefault = 0
          AND NOT EXISTS (SELECT 1 FROM inventory_items WHERE listId = :id)
        """
    )
    suspend fun deleteList(id: Long): Int
}
