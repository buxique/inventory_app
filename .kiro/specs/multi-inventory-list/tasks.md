# 多库存列表管理系统 - 任务列表

**功能名称**: 多库存列表管理系统  
**版本**: 1.0  
**创建日期**: 2026-01-25

---

## 任务状态说明

- `[ ]` - 未开始
- `[-]` - 进行中
- `[x]` - 已完成
- `[~]` - 已排队

---

## 阶段1: 数据库架构

### 1.1 创建数据模型

- [ ] 1.1.1 创建 `InventoryListEntity` 数据类
  - 文件: `app/src/main/java/com/example/inventory/data/model/InventoryModels.kt`
  - 包含字段: id, name, displayOrder, createdAt, lastModified, isDefault
  - 添加 Room 注解: @Entity, @PrimaryKey, @Index

- [ ] 1.1.2 修改 `InventoryItemEntity` 添加 `listId` 字段
  - 文件: `app/src/main/java/com/example/inventory/data/model/InventoryModels.kt`
  - 添加 `listId: Long` 字段
  - 添加外键约束注解
  - 添加 `listId` 索引

### 1.2 创建 DAO 接口

- [ ] 1.2.1 创建 `InventoryListDao` 接口
  - 文件: `app/src/main/java/com/example/inventory/data/db/InventoryListDao.kt`
  - 实现方法:
    - `getAllLists(): Flow<List<InventoryListEntity>>`
    - `getAllListsSnapshot(): List<InventoryListEntity>`
    - `getListById(id: Long): InventoryListEntity?`
    - `getListByName(name: String): InventoryListEntity?`
    - `getDefaultList(): InventoryListEntity?`
    - `getMaxDisplayOrder(): Int?`
    - `insertList(list: InventoryListEntity): Long`
    - `updateLists(lists: List<InventoryListEntity>): Int`
    - `updateListName(id: Long, newName: String, timestamp: Long): Int`
    - `deleteList(id: Long): Int`

- [ ] 1.2.2 修改 `InventoryDao` 添加列表过滤方法
  - 文件: `app/src/main/java/com/example/inventory/data/db/InventoryDao.kt`
  - 新增方法:
    - `getItemsByListId(listId: Long): PagingSource<Int, InventoryItemEntity>`
    - `getItemCountByListId(listId: Long): Int`
    - `searchItemsInList(listId: Long, query: String): List<InventoryItemEntity>`
    - `moveItemsToList(itemIds: List<Long>, targetListId: Long): Int`

### 1.3 数据库迁移

- [ ] 1.3.1 实现数据库迁移 `MIGRATION_7_8`
  - 文件: `app/src/main/java/com/example/inventory/data/db/InventoryDatabase.kt`
  - 创建 `inventory_lists` 表
  - 创建默认列表"仓库1"
  - 为 `inventory_items` 添加 `listId` 字段（默认值1）
  - 创建索引

- [ ] 1.3.2 更新 `InventoryDatabase` 版本号和 DAO
  - 文件: `app/src/main/java/com/example/inventory/data/db/InventoryDatabase.kt`
  - 版本号从 7 升级到 8
  - 添加 `InventoryListEntity` 到 entities 列表
  - 添加 `abstract fun inventoryListDao(): InventoryListDao`
  - 添加 `MIGRATION_7_8` 到迁移列表

### 1.4 数据库测试

- [ ] 1.4.1 编写 `InventoryListDao` 单元测试
  - 文件: `app/src/test/java/com/example/inventory/data/db/InventoryListDaoTest.kt`
  - 测试所有 CRUD 操作
  - 测试名称唯一性
  - 测试默认列表查询

- [ ] 1.4.2 编写数据库迁移测试
  - 文件: `app/src/test/java/com/example/inventory/data/db/MigrationTest.kt`
  - 测试从版本7迁移到版本8
  - 验证现有数据迁移到"仓库1"
  - 验证索引创建

---

## 阶段2: Repository 层

### 2.1 创建 InventoryListRepository

- [ ] 2.1.1 创建 `InventoryListRepository` 接口
  - 文件: `app/src/main/java/com/example/inventory/data/repository/InventoryListRepository.kt`
  - 定义所有列表管理方法

- [ ] 2.1.2 实现 `InventoryListRepositoryImpl`
  - 文件: `app/src/main/java/com/example/inventory/data/repository/InventoryListRepositoryImpl.kt`
  - 实现所有接口方法
  - 实现 `getNextDefaultName()` 逻辑
  - 实现名称唯一性检查

### 2.2 修改 InventoryRepository

- [ ] 2.2.1 修改 `InventoryRepository` 接口
  - 文件: `app/src/main/java/com/example/inventory/data/repository/InventoryRepository.kt`
  - 新增方法:
    - `getItemsByListId(listId: Long): Flow<PagingData<InventoryItemEntity>>`
    - `getItemCountByListId(listId: Long): Int`
    - `searchItemsInList(listId: Long, query: String): List<InventoryItemEntity>`
    - `moveItemsToList(itemIds: List<Long>, targetListId: Long): Int`

- [ ] 2.2.2 实现 `InventoryRepositoryImpl` 新方法
  - 文件: `app/src/main/java/com/example/inventory/data/repository/InventoryRepositoryImpl.kt`
  - 实现所有新增方法
  - 使用 Pager 配置分页

### 2.3 Repository 测试

- [ ] 2.3.1 编写 `InventoryListRepository` 单元测试
  - 文件: `app/src/test/java/com/example/inventory/data/repository/InventoryListRepositoryTest.kt`
  - 测试创建列表
  - 测试重命名列表
  - 测试删除列表
  - 测试名称生成逻辑

- [ ] 2.3.2 编写 `InventoryRepository` 新方法测试
  - 文件: `app/src/test/java/com/example/inventory/data/repository/InventoryRepositoryTest.kt`
  - 测试按列表查询
  - 测试商品移动
  - 测试列表内搜索

---

## 阶段3: ViewModel 层

### 3.1 创建 InventoryListViewModel

- [ ] 3.1.1 创建 `InventoryListViewModel` 类
  - 文件: `app/src/main/java/com/example/inventory/ui/viewmodel/InventoryListViewModel.kt`
  - 实现状态管理: lists, currentListId, currentList
  - 实现方法:
    - `createList(name: String?): Long`
    - `switchToList(listId: Long)`
    - `renameList(listId: Long, newName: String): Result<Unit>`
    - `deleteList(listId: Long): Result<Unit>`

### 3.2 创建 ImportViewModel

- [ ] 3.2.1 创建 `ImportProgress` 数据类
  - 文件: `app/src/main/java/com/example/inventory/ui/state/ImportUiState.kt`
  - 包含字段: isImporting, currentCount, totalCount, progress, error

- [ ] 3.2.2 创建 `ImportViewModel` 类
  - 文件: `app/src/main/java/com/example/inventory/ui/viewmodel/ImportViewModel.kt`
  - 实现进度状态管理
  - 实现方法:
    - `importFile(listId: Long, fileUri: Uri, fileType: FileType): Result<Int>`
    - `cancelImport()`
    - `resetProgress()`

### 3.3 修改 InventoryViewModel

- [ ] 3.3.1 修改 `InventoryViewModel` 支持多列表
  - 文件: `app/src/main/java/com/example/inventory/ui/viewmodel/InventoryViewModel.kt`
  - 添加 `InventoryListViewModel` 依赖
  - 修改 `itemsFlow` 支持列表过滤
  - 修改 `addItem` 方法指定列表ID

### 3.4 ViewModel 测试

- [ ] 3.4.1 编写 `InventoryListViewModel` 单元测试
  - 文件: `app/src/test/java/com/example/inventory/ui/viewmodel/InventoryListViewModelTest.kt`
  - 测试创建列表
  - 测试切换列表
  - 测试重命名列表
  - 测试删除列表
  - 测试边界条件（删除最后一个列表）

- [ ] 3.4.2 编写 `ImportViewModel` 单元测试
  - 文件: `app/src/test/java/com/example/inventory/ui/viewmodel/ImportViewModelTest.kt`
  - 测试导入流程
  - 测试进度更新
  - 测试取消导入
  - 测试错误处理

---

## 阶段4: UI 组件

### 4.1 列表底部新增按钮

- [ ] 4.1.1 创建 `AddItemButton` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/AddItemButton.kt`
  - 实现按钮样式
  - 添加点击事件

- [ ] 4.1.2 集成到 `InventoryListContent`
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryListContent.kt`
  - 在 LazyColumn 底部添加按钮
  - 连接到手动添加对话框

### 4.2 列表切换器

- [ ] 4.2.1 创建 `InventoryListPager` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryListPager.kt`
  - 使用 HorizontalPager 实现滑动切换
  - 实现页面变化监听
  - 添加页面指示器

### 4.3 列表选择下拉菜单

- [ ] 4.3.1 创建 `InventoryListDropdown` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryListDropdown.kt`
  - 实现下拉菜单
  - 显示所有列表
  - 标记当前列表
  - 添加新建/重命名/删除功能

- [ ] 4.3.2 创建 `RenameListDialog` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryDialogs.kt`
  - 实现重命名对话框
  - 验证名称唯一性
  - 验证名称长度

- [ ] 4.3.3 创建 `DeleteListConfirmDialog` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryDialogs.kt`
  - 实现删除确认对话框
  - 显示警告信息

### 4.4 导入进度对话框

- [ ] 4.4.1 创建 `ImportProgressDialog` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/ImportProgressDialog.kt`
  - 显示进度条
  - 显示百分比
  - 显示数量统计
  - 添加取消按钮

### 4.5 修改现有组件

- [ ] 4.5.1 修改 `ImportBottomSheet` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryDialogs.kt`
  - 移除"手动添加商品"和"拍照添加"选项
  - 保留导入选项
  - 添加"手动创建空列表"选项
  - 更新标题为"导入或新建库存列表"

- [ ] 4.5.2 修改 `InventoryTopBar` 组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/inventory/components/InventoryTopBar.kt`
  - 将标题替换为 `InventoryListDropdown`
  - 保留搜索、分类、设置按钮

### 4.6 集成到主屏幕

- [ ] 4.6.1 修改 `InventoryListScreen` 集成所有组件
  - 文件: `app/src/main/java/com/example/inventory/ui/screens/InventoryListScreen.kt`
  - 使用 `InventoryListPager` 包裹内容
  - 集成 `ImportProgressDialog`
  - 更新 FAB 对话框逻辑
  - 处理列表切换事件

---

## 阶段5: 依赖注入与配置

### 5.1 更新 AppContainer

- [ ] 5.1.1 添加新的 Repository 和 ViewModel
  - 文件: `app/src/main/java/com/example/inventory/data/AppContainer.kt`
  - 添加 `InventoryListDao` 实例
  - 添加 `InventoryListRepository` 实例
  - 添加 `InventoryListViewModel` 工厂
  - 添加 `ImportViewModel` 工厂

### 5.2 更新 ViewModelFactory

- [ ] 5.2.1 修改 `AppViewModelFactory` 支持新 ViewModel
  - 文件: `app/src/main/java/com/example/inventory/ui/viewmodel/AppViewModelFactory.kt`
  - 添加 `InventoryListViewModel` 创建逻辑
  - 添加 `ImportViewModel` 创建逻辑
  - 修改 `InventoryViewModel` 添加 `InventoryListViewModel` 依赖

---

## 阶段6: 集成测试

### 6.1 端到端测试

- [ ] 6.1.1 测试创建列表流程
  - 创建空列表 → 验证列表存在
  - 导入文件创建列表 → 验证数据正确

- [ ] 6.1.2 测试切换列表流程
  - 滑动切换 → 验证数据更新
  - 下拉菜单切换 → 验证数据更新

- [ ] 6.1.3 测试数据隔离
  - 在列表1添加商品 → 切换到列表2 → 验证列表2不包含列表1的商品

- [ ] 6.1.4 测试删除列表
  - 删除列表 → 验证商品被级联删除
  - 尝试删除最后一个列表 → 验证被阻止

### 6.2 性能测试

- [ ] 6.2.1 测试列表切换性能
  - 测量切换响应时间 < 300ms

- [ ] 6.2.2 测试导入性能
  - 导入1000条数据 < 5秒

- [ ] 6.2.3 测试滑动流畅度
  - 测量帧率 ≥ 60fps

### 6.3 UI 测试

- [ ] 6.3.1 测试列表底部按钮显示
- [ ] 6.3.2 测试滑动切换列表
- [ ] 6.3.3 测试下拉菜单选择
- [ ] 6.3.4 测试进度条显示

---

## 阶段7: 文档与发布

### 7.1 文档编写

- [ ] 7.1.1 编写用户使用文档
  - 如何创建列表
  - 如何切换列表
  - 如何导入数据
  - 如何管理列表

- [ ] 7.1.2 更新 CHANGELOG
  - 记录所有新功能
  - 记录 API 变更
  - 记录数据库变更

- [ ] 7.1.3 编写开发者文档
  - 架构设计说明
  - API 使用指南
  - 数据库 Schema 说明

### 7.2 代码审查

- [ ] 7.2.1 代码质量检查
  - 运行 Lint 检查
  - 修复所有警告
  - 确保代码风格一致

- [ ] 7.2.2 安全审查
  - 检查 SQL 注入风险
  - 检查数据泄漏风险
  - 检查权限使用

### 7.3 发布准备

- [ ] 7.3.1 版本号更新
  - 更新 build.gradle.kts 版本号
  - 更新版本说明

- [ ] 7.3.2 构建发布版本
  - 生成签名 APK
  - 测试发布版本

---

## 总结

**总任务数**: 60+  
**预计工时**: 12天  
**优先级**: P0 - 核心功能

**关键里程碑**:
- 阶段1完成: 数据库架构就绪
- 阶段3完成: 核心逻辑实现
- 阶段4完成: UI 功能完整
- 阶段6完成: 质量保证通过

---

**创建日期**: 2026-01-25  
**最后更新**: 2026-01-25
