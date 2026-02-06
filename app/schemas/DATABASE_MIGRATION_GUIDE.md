# 数据库迁移指南

## 概述

本项目使用Room数据库，迁移方案位于 `InventoryDatabase.kt`。
本文档记录每个版本的数据库变更和迁移策略。

---

## 版本历史

### Version 1（初始版本）

**数据表结构：**

1. **inventory_items** - 商品信息表
   - `id`: Long (主键，自增)
   - `name`: String (商品名称)
   - `brand`: String (品牌)
   - `model`: String (型号)
   - `parameters`: String (参数规格)
   - `barcode`: String (条码)
   - `quantity`: Int (当前库存数量)
   - `remark`: String (备注)

2. **stock_records** - 库存记录表
   - `id`: Long (主键，自增)
   - `itemId`: Long (关联商品ID)
   - `change`: Int (变化数量)
   - `operatorName`: String (操作人)
   - `remark`: String (备注)
   - `timestamp`: Long (时间戳)

3. **users** - 用户表
   - `id`: Long (主键，自增)
   - `username`: String (用户名，唯一)
   - `passwordHash`: String (密码哈希)
   - `role`: String (角色)

**问题：**
- 没有数据库索引，查询性能较差
- 按条码查询商品时需要全表扫描
- 按时间范围查询库存记录时效率低

---

### Version 2（当前版本）

**更新时间：** 2026-01

**变更内容：**

1. **添加索引** 以优化查询性能：
   - `inventory_items` 表：
     - `barcode` 字段索引（非唯一）- 优化条码扫描查询
     - `name` 字段索引 - 优化商品名称搜索
   
   - `stock_records` 表：
     - `itemId` 字段索引 - 优化按商品查询记录
     - `timestamp` 字段索引 - 优化按时间范围查询

**迁移SQL：**
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_barcode ON inventory_items(barcode)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_name ON inventory_items(name)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_records_itemId ON stock_records(itemId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_records_timestamp ON stock_records(timestamp)")
    }
}
```

**迁移影响：**
- ✅ 无数据丢失
- ✅ 不需要数据转换
- ✅ 向后兼容
- ⚠️ 首次迁移时会重建索引（用户量大时可能需要几秒）

**性能提升：**
- 按条码查询：从 O(n) 优化到 O(log n)
- 按时间范围查询记录：从 O(n) 优化到 O(log n)
- 按商品ID查询记录：从 O(n) 优化到 O(log n)

---

## 迁移最佳实践

### 1. 添加新迁移时的步骤

```kotlin
// 1. 更新数据库版本号
@Database(
    entities = [...],
    version = 3, // 增加版本号
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {
    
    companion object {
        // 2. 添加新的迁移对象
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 3. 编写迁移SQL
                database.execSQL("ALTER TABLE inventory_items ADD COLUMN supplier TEXT NOT NULL DEFAULT ''")
            }
        }
        
        // 4. 在getInstance中注册迁移
        @Volatile
        private var INSTANCE: InventoryDatabase? = null
        
        fun getInstance(context: Context): InventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "inventory.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // 添加到这里
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

### 2. 测试迁移

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InventoryDatabase::class.java
    )

    @Test
    fun migrate1To2() {
        // 创建v1数据库
        val db = helper.createDatabase(TEST_DB, 1)
        
        // 插入测试数据
        db.execSQL("INSERT INTO inventory_items VALUES (1, 'Test', 'Brand', 'Model', '', '123', 10, '')")
        db.close()

        // 执行迁移
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // 验证迁移结果
        val newDb = helper.getMigratableDatabase(TEST_DB, 2)
        val cursor = newDb.query("SELECT * FROM inventory_items WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        cursor.close()
    }
}
```

### 3. Schema导出

确保在 `build.gradle.kts` 中配置Schema导出：

```kotlin
android {
    defaultConfig {
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}
```

Schema文件会自动生成在 `app/schemas/` 目录下，方便对比版本差异。

---

## 常见问题

### Q1: 如果迁移失败会怎样？

A: Room会抛出 `IllegalStateException`，应用会崩溃。建议：
- 在 `ExportRepository` 中实现自动备份
- 用户可以通过"设置"页面手动恢复备份

### Q2: 能否跳过某个版本的迁移？

A: 不建议。Room要求提供所有版本的迁移路径。例如：
- 用户从v1升级到v3：需要 MIGRATION_1_2 + MIGRATION_2_3
- 如果缺少任何一个，迁移会失败

### Q3: 如何处理破坏性变更？

A: 对于无法自动迁移的变更（如更改主键类型），需要：
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 创建新表
        database.execSQL("CREATE TABLE inventory_items_new (...)")
        
        // 2. 复制数据（带转换）
        database.execSQL("INSERT INTO inventory_items_new SELECT ... FROM inventory_items")
        
        // 3. 删除旧表
        database.execSQL("DROP TABLE inventory_items")
        
        // 4. 重命名新表
        database.execSQL("ALTER TABLE inventory_items_new RENAME TO inventory_items")
    }
}
```

---

## 未来规划

### 计划中的改进（v3）

- [ ] 添加商品分类表 `categories`
- [ ] 为商品添加图片字段
- [ ] 添加供应商信息表
- [ ] 为用户表添加头像和邮箱字段

### 性能监控

建议在迁移时添加日志监控：

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val startTime = System.currentTimeMillis()
        AppLogger.d("开始迁移数据库从版本$X到$Y")
        
        try {
            // 执行迁移...
            
            val duration = System.currentTimeMillis() - startTime
            AppLogger.logDatabase("迁移成功", "从v$X到v$Y，耗时${duration}ms")
        } catch (e: Exception) {
            AppLogger.e("迁移失败：${e.message}")
            throw e
        }
    }
}
```

---

## 参考资料

- [Room官方文档 - 迁移数据库](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [SQLite索引优化](https://www.sqlite.org/optoverview.html)
- [项目Wiki - 数据库设计](链接待补充)

---

**最后更新：** 2026-01-21  
**维护人员：** 开发团队
