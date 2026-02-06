package com.example.inventory.data.repository

import com.example.inventory.data.db.InventoryListDao
import com.example.inventory.data.model.InventoryListEntity
import kotlinx.coroutines.flow.Flow

/**
 * 库存列表仓库实现
 * 
 * @param dao 库存列表数据访问对象
 */
class InventoryListRepositoryImpl(
    private val dao: InventoryListDao
) : InventoryListRepository {
    
    override fun getAllLists(): Flow<List<InventoryListEntity>> {
        return dao.getAllLists()
    }
    
    override suspend fun getListById(id: Long): InventoryListEntity? {
        return dao.getListById(id)
    }
    
    override suspend fun getDefaultList(): InventoryListEntity? {
        return dao.getDefaultList()
    }
    
    override suspend fun createList(name: String?): Long {
        val listName = name ?: getNextDefaultName()
        val now = System.currentTimeMillis()
        val maxOrder = dao.getMaxDisplayOrder() ?: -1
        
        val newList = InventoryListEntity(
            name = listName,
            displayOrder = maxOrder + 1,
            createdAt = now,
            lastModified = now,
            isDefault = false
        )
        
        return dao.insertList(newList)
    }
    
    override suspend fun renameList(id: Long, newName: String): Int {
        return dao.updateListName(id, newName, System.currentTimeMillis())
    }
    
    override suspend fun updateListOrder(lists: List<InventoryListEntity>): Int {
        return dao.updateLists(lists)
    }
    
    override suspend fun deleteList(id: Long): Int {
        return dao.deleteList(id)
    }
    
    override suspend fun getNextDefaultName(): String {
        val existingNames = dao.getAllListsSnapshot().map { it.name }
        var counter = 1
        while (true) {
            val name = "仓库$counter"
            if (name !in existingNames) {
                return name
            }
            counter++
        }
    }
    
    override suspend fun isNameExists(name: String): Boolean {
        return dao.getListByName(name) != null
    }
}
