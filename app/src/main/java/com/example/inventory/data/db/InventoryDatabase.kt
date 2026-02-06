package com.example.inventory.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.inventory.data.model.CategoryEntity
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.data.model.InventoryListEntity
import com.example.inventory.data.model.ItemCategoryEntity
import com.example.inventory.data.model.SearchHistoryEntity
import com.example.inventory.data.model.StockRecordEntity
import com.example.inventory.data.model.UserEntity
import com.example.inventory.data.model.InventoryItemFts

@Database(
    entities = [
        InventoryItemEntity::class,
        InventoryListEntity::class,  // 新增：库存列表实体
        InventoryItemFts::class,     // 新增：FTS 实体
        StockRecordEntity::class, 
        UserEntity::class,
        SearchHistoryEntity::class,
        CategoryEntity::class,
        ItemCategoryEntity::class
    ],
    version = 10,  // 版本号从 9 升级到 10
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryListDao(): InventoryListDao  // 新增：库存列表 DAO
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun categoryDao(): CategoryDao
    
    companion object {
        // 从版本9升级到10：添加 FTS 全文搜索支持
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 FTS4 虚拟表
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `inventory_items_fts` 
                    USING FTS4(
                        `name`, `brand`, `model`, `barcode`, `location`, `remark`, 
                        content=`inventory_items`
                    )
                    """
                )
                
                // 重建 FTS 数据：将现有数据插入到 FTS 表
                db.execSQL(
                    """
                    INSERT INTO inventory_items_fts(rowid, name, brand, model, barcode, location, remark)
                    SELECT id, name, brand, model, barcode, location, remark FROM inventory_items
                    """
                )
                
                // 创建触发器：当 inventory_items 插入数据时，同步插入到 FTS 表
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `inventory_items_ai` AFTER INSERT ON `inventory_items`
                    BEGIN
                        INSERT INTO inventory_items_fts(rowid, name, brand, model, barcode, location, remark)
                        VALUES (new.id, new.name, new.brand, new.model, new.barcode, new.location, new.remark);
                    END
                    """
                )
                
                // 创建触发器：当 inventory_items 删除数据时，同步删除 FTS 表数据
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `inventory_items_ad` AFTER DELETE ON `inventory_items`
                    BEGIN
                        INSERT INTO inventory_items_fts(inventory_items_fts, rowid, name, brand, model, barcode, location, remark)
                        VALUES('delete', old.id, old.name, old.brand, old.model, old.barcode, old.location, old.remark);
                    END
                    """
                )
                
                // 创建触发器：当 inventory_items 更新数据时，同步更新 FTS 表数据
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `inventory_items_au` AFTER UPDATE ON `inventory_items`
                    BEGIN
                        INSERT INTO inventory_items_fts(inventory_items_fts, rowid, name, brand, model, barcode, location, remark)
                        VALUES('delete', old.id, old.name, old.brand, old.model, old.barcode, old.location, old.remark);
                        INSERT INTO inventory_items_fts(rowid, name, brand, model, barcode, location, remark)
                        VALUES (new.id, new.name, new.brand, new.model, new.barcode, new.location, new.remark);
                    END
                    """
                )
            }
        }

        // 从版本8升级到9：添加 unit 和 location 字段
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 检查 unit 列是否已存在
                val tableInfo = db.query("PRAGMA table_info(inventory_items)")
                var hasUnit = false
                var hasLocation = false
                
                val nameIndex = tableInfo.getColumnIndex("name")
                while (tableInfo.moveToNext()) {
                    if (nameIndex != -1) {
                        val columnName = tableInfo.getString(nameIndex)
                        when (columnName) {
                            "unit" -> hasUnit = true
                            "location" -> hasLocation = true
                        }
                    }
                }
                tableInfo.close()
                
                // 添加 unit 字段（如果不存在）
                if (!hasUnit) {
                    db.execSQL(
                        "ALTER TABLE inventory_items ADD COLUMN unit TEXT NOT NULL DEFAULT '个'"
                    )
                }
                
                // 添加 location 字段（如果不存在）
                if (!hasLocation) {
                    db.execSQL(
                        "ALTER TABLE inventory_items ADD COLUMN location TEXT NOT NULL DEFAULT ''"
                    )
                }
                
                // 创建索引以支持按库位和单位查询
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_items_location " +
                    "ON inventory_items(location)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_items_unit " +
                    "ON inventory_items(unit)"
                )
            }
        }
        
        // 从版本7升级到8：添加多库存列表支持
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建 inventory_lists 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS inventory_lists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // 2. 创建索引
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_lists_displayOrder " +
                    "ON inventory_lists(displayOrder)"
                )
                
                // 3. 检查是否已有默认列表，如果没有则创建
                val cursor = db.query("SELECT COUNT(*) FROM inventory_lists WHERE isDefault = 1")
                cursor.moveToFirst()
                val hasDefault = cursor.getInt(0) > 0
                cursor.close()
                
                if (!hasDefault) {
                    val now = System.currentTimeMillis()
                    db.execSQL("""
                        INSERT INTO inventory_lists (name, displayOrder, createdAt, lastModified, isDefault)
                        VALUES ('仓库1', 0, $now, $now, 1)
                    """)
                }
                
                // 4. 检查 listId 列是否已存在
                val tableInfo = db.query("PRAGMA table_info(inventory_items)")
                var hasListId = false
                val nameIndex = tableInfo.getColumnIndex("name")
                while (tableInfo.moveToNext()) {
                    if (nameIndex != -1) {
                        val columnName = tableInfo.getString(nameIndex)
                        if (columnName == "listId") {
                            hasListId = true
                            break
                        }
                    }
                }
                tableInfo.close()
                
                // 5. 如果 listId 列不存在，需要重建表以添加外键约束
                if (!hasListId) {
                    // 5.1 创建新表（带外键约束）
                    db.execSQL("""
                        CREATE TABLE inventory_items_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            listId INTEGER NOT NULL DEFAULT 1,
                            name TEXT NOT NULL,
                            brand TEXT NOT NULL,
                            model TEXT NOT NULL,
                            parameters TEXT NOT NULL,
                            barcode TEXT NOT NULL,
                            quantity INTEGER NOT NULL,
                            remark TEXT NOT NULL,
                            lastModified INTEGER NOT NULL,
                            FOREIGN KEY(listId) REFERENCES inventory_lists(id) ON DELETE CASCADE
                        )
                    """)
                    
                    // 5.2 复制数据
                    db.execSQL("""
                        INSERT INTO inventory_items_new (id, listId, name, brand, model, parameters, barcode, quantity, remark, lastModified)
                        SELECT id, 1, name, brand, model, parameters, barcode, quantity, remark, lastModified
                        FROM inventory_items
                    """)
                    
                    // 5.3 删除旧表
                    db.execSQL("DROP TABLE inventory_items")
                    
                    // 5.4 重命名新表
                    db.execSQL("ALTER TABLE inventory_items_new RENAME TO inventory_items")
                    
                    // 5.5 重建索引
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_inventory_items_barcode " +
                        "ON inventory_items(barcode)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_inventory_items_name " +
                        "ON inventory_items(name)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_inventory_items_listId " +
                        "ON inventory_items(listId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_inventory_items_lastModified " +
                        "ON inventory_items(lastModified)"
                    )
                }
            }
        }
        
        // 从版本6升级到7：添加复合索引优化查询性能
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 inventory_items 添加 lastModified 索引
                // 用于按时间排序查询
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_items_lastModified " +
                    "ON inventory_items(lastModified DESC)"
                )
                
                // 为 stock_records 添加复合索引
                // 用于按商品ID和时间查询记录
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_records_itemId_timestamp " +
                    "ON stock_records(itemId, timestamp DESC)"
                )
                
                // 为 item_categories 添加复合索引
                // 用于按分类查询商品
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_categories_categoryId_itemId " +
                    "ON item_categories(categoryId, itemId)"
                )
            }
        }
        
        // 从版本5升级到6：添加 lastModified 字段
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 inventory_items 表添加 lastModified 字段，默认值为当前时间
                db.execSQL(
                    "ALTER TABLE inventory_items ADD COLUMN lastModified INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}"
                )
            }
        }

        // 从版本1升级到2：添加索引
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为inventory_items表添加索引
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_items_barcode ON inventory_items(barcode)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_inventory_items_name ON inventory_items(name)"
                )
                    
                // 为stock_records表添加索引
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_records_itemId ON stock_records(itemId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_records_timestamp ON stock_records(timestamp)"
                )
            }
        }
            
        // 从版本2升级到3：添加搜索历史表
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建搜索历史表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        searchType TEXT NOT NULL DEFAULT 'item',
                        timestamp INTEGER NOT NULL,
                        searchCount INTEGER NOT NULL DEFAULT 1,
                        resultCount INTEGER NOT NULL DEFAULT 0,
                        isFavorite INTEGER NOT NULL DEFAULT 0
                    )
                """)
                    
                // 添加索引
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_timestamp ON search_history(timestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_searchType ON search_history(searchType)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_search_history_query")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query_searchType " +
                        "ON search_history(query, searchType)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建分类表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `parentId` INTEGER, 
                        `sortOrder` INTEGER NOT NULL, 
                        `icon` TEXT NOT NULL, 
                        `color` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                // 创建物品-分类关联表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `item_categories` (
                        `itemId` INTEGER NOT NULL, 
                        `categoryId` INTEGER NOT NULL, 
                        PRIMARY KEY(`itemId`, `categoryId`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_categories_itemId` ON `item_categories` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_categories_categoryId` ON `item_categories` (`categoryId`)")
            }
        }
    }
}
