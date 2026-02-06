# 多库存列表管理系统 - 设计文档

**功能名称**: 多库存列表管理系统  
**版本**: 1.0  
**创建日期**: 2026-01-25  
**状态**: 设计阶段

---

## 1. 架构设计概述

### 1.1 设计目标

实现多库存列表管理功能，支持用户创建、管理和切换多个独立的库存列表。采用分层架构设计，确保代码可维护性和可扩展性。

### 1.2 核心原则

- **单一职责**: 每个组件只负责一个明确的功能
- **数据隔离**: 不同列表的数据完全独立
- **向后兼容**: 现有数据自动迁移，不影响用户体验
- **性能优先**: 使用索引和分页优化查询性能

### 1.3 技术栈

- **数据库**: Room (SQLite)
- **UI框架**: Jetpack Compose
- **异步处理**: Kotlin Coroutines + Flow
- **依赖注入**: 手动依赖注入（AppContainer）
- **分页**: Paging 3

---

## 2. 数据库设计

### 2.1 新增表：inventory_lists

**表结构**:

```kotlin
@Entity(
    tableName = "inventory_lists",
    indices = [
        Index(value = ["display_order"])
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
```

**字段说明**:
- `id`: 列表唯一标识，自增主键
- `name`: 列表名称，用户可自定义，默认"仓库1"、"仓库2"等
- `displayOrder`: 显示顺序，用于列表排序
- `createdAt`: 创建时间戳（毫秒）
- `lastModified`: 最后修改时间戳（毫秒）
- `isDefault`: 是否为默认列表（首次启动时自动创建的列表）

**索引设计**:
- `display_order`: 用于按顺序查询列表

### 2.2 修改表：inventory_items

**新增字段**:

```kotlin
@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["barcode"], unique = false),
        Index(value = ["name"]),
        Index(value = ["list_id"]),  // 新增索引
        Index(value = ["lastModified"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = InventoryListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE  // 删除列表时级联删除商品
        )
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,  // 新增：所属列表ID
    val name: String,
    val brand: String,
    val model: String,
    val parameters: String,
    val barcode: String,
    val quantity: Int,
    val remark: String,
    val lastModified: Long = System.currentTimeMillis()
)
```

**变更说明**:
- 新增 `listId` 字段，关联到 `inventory_lists` 表
- 添加外键约束，删除列表时自动删除关联商品
- 添加 `list_id` 索引，优化按列表查询性能

### 2.3 数据库迁移

**迁移版本**: 7 → 8

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. 创建 inventory_lists 表
        database.execSQL("""
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
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_lists_displayOrder " +
            "ON inventory_lists(displayOrder)"
        )
        
        // 3. 创建默认列表"仓库1"
        val now = System.currentTimeMillis()
        database.execSQL("""
            INSERT INTO inventory_lists (name, displayOrder, createdAt, lastModified, isDefault)
            VALUES ('仓库1', 0, $now, $now, 1)
        """)
        
        // 4. 为 inventory_items 添加 list_id 字段（默认值为1，即"仓库1"）
        database.execSQL(
            "ALTER TABLE inventory_items ADD COLUMN listId INTEGER NOT NULL DEFAULT 1"
        )
        
        // 5. 创建索引
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_inventory_items_listId " +
            "ON inventory_items(listId)"
        )
    }
}
```

**迁移策略**:
1. 创建 `inventory_lists` 表
2. 自动创建默认列表"仓库1"（id=1）
3. 为 `inventory_items` 添加 `listId` 字段，默认值为1
4. 所有现有商品自动归属到"仓库1"
5. 用户无感知，数据完整保留

---

## 3. Repository 层设计

### 3.1 InventoryListRepository

**接口定义**:

```kotlin
interface InventoryListRepository {
    // 查询操作
    fun getAllLists(): Flow<List<InventoryListEntity>>
    suspend fun getListById(id: Long): InventoryListEntity?
    suspend fun getDefaultList(): InventoryListEntity?
    
    // 创建操作
    suspend fun createList(name: String? = null): Long
    
    // 更新操作
    suspend fun renameList(id: Long, newName: String): Int
    suspend fun updateListOrder(lists: List<InventoryListEntity>): Int
    
    // 删除操作
    suspend fun deleteList(id: Long): Int
    
    // 工具方法
    suspend fun getNextDefaultName(): String
    suspend fun isNameExists(name: String): Boolean
}
```

**实现类**:

```kotlin
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
```

### 3.2 修改 InventoryRepository

**新增方法**:

```kotlin
interface InventoryRepository {
    // 现有方法...
    
    // 新增：按列表ID查询
    fun getItemsByListId(listId: Long): Flow<PagingData<InventoryItemEntity>>
    suspend fun getItemCountByListId(listId: Long): Int
    
    // 修改：添加商品时指定列表ID
    suspend fun addItem(item: InventoryItemEntity, listId: Long): Long
    
    // 新增：在列表间移动商品
    suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int
}
```

### 3.3 InventoryListDao

**DAO接口**:

```kotlin
@Dao
interface InventoryListDao {
    @Query("SELECT * FROM inventory_lists ORDER BY displayOrder ASC")
    fun getAllLists(): Flow<List<InventoryListEntity>>
    
    @Query("SELECT * FROM inventory_lists ORDER BY displayOrder ASC")
    suspend fun getAllListsSnapshot(): List<InventoryListEntity>
    
    @Query("SELECT * FROM inventory_lists WHERE id = :id")
    suspend fun getListById(id: Long): InventoryListEntity?
    
    @Query("SELECT * FROM inventory_lists WHERE name = :name")
    suspend fun getListByName(name: String): InventoryListEntity?
    
    @Query("SELECT * FROM inventory_lists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultList(): InventoryListEntity?
    
    @Query("SELECT MAX(displayOrder) FROM inventory_lists")
    suspend fun getMaxDisplayOrder(): Int?
    
    @Insert
    suspend fun insertList(list: InventoryListEntity): Long
    
    @Update
    suspend fun updateLists(lists: List<InventoryListEntity>): Int
    
    @Query("UPDATE inventory_lists SET name = :newName, lastModified = :timestamp WHERE id = :id")
    suspend fun updateListName(id: Long, newName: String, timestamp: Long): Int
    
    @Query("DELETE FROM inventory_lists WHERE id = :id")
    suspend fun deleteList(id: Long): Int
}
```

### 3.4 修改 InventoryDao

**新增查询方法**:

```kotlin
@Dao
interface InventoryDao {
    // 现有方法...
    
    // 新增：按列表ID查询
    @Query("SELECT * FROM inventory_items WHERE listId = :listId ORDER BY id DESC")
    fun getItemsByListId(listId: Long): PagingSource<Int, InventoryItemEntity>
    
    @Query("SELECT COUNT(*) FROM inventory_items WHERE listId = :listId")
    suspend fun getItemCountByListId(listId: Long): Int
    
    // 新增：移动商品到其他列表
    @Query("UPDATE inventory_items SET listId = :targetListId WHERE id IN (:itemIds)")
    suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int
    
    // 修改：搜索时过滤列表
    @Query("""
        SELECT * FROM inventory_items
        WHERE listId = :listId
          AND (name LIKE :query
               OR brand LIKE :query
               OR model LIKE :query
               OR barcode LIKE :query)
        ORDER BY id DESC
    """)
    suspend fun searchItemsInList(listId: Long, query: String): List<InventoryItemEntity>
}
```

---

## 4. ViewModel 层设计

### 4.1 InventoryListViewModel

**职责**: 管理库存列表的创建、切换、重命名和删除

```kotlin
class InventoryListViewModel(
    private val listRepository: InventoryListRepository
) : ViewModel() {
    
    // 所有列表
    val lists: StateFlow<List<InventoryListEntity>> = listRepository
        .getAllLists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 当前选中的列表ID
    private val _currentListId = MutableStateFlow<Long?>(null)
    val currentListId: StateFlow<Long?> = _currentListId.asStateFlow()
    
    // 当前列表
    val currentList: StateFlow<InventoryListEntity?> = combine(
        lists,
        currentListId
    ) { allLists, currentId ->
        allLists.find { it.id == currentId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
    init {
        // 初始化时加载默认列表
        viewModelScope.launch {
            val defaultList = listRepository.getDefaultList()
            _currentListId.value = defaultList?.id
        }
    }
    
    /**
     * 创建新列表
     * 
     * @param name 列表名称（可选，默认自动生成"仓库N"）
     * @return 新列表的ID
     */
    suspend fun createList(name: String? = null): Long {
        return listRepository.createList(name)
    }
    
    /**
     * 切换到指定列表
     */
    fun switchToList(listId: Long) {
        _currentListId.value = listId
    }
    
    /**
     * 重命名列表
     */
    suspend fun renameList(listId: Long, newName: String): Result<Unit> {
        if (newName.isBlank()) {
            return Result.failure(IllegalArgumentException("列表名称不能为空"))
        }
        if (newName.length > 20) {
            return Result.failure(IllegalArgumentException("列表名称不能超过20个字符"))
        }
        if (listRepository.isNameExists(newName)) {
            return Result.failure(IllegalArgumentException("列表名称已存在"))
        }
        
        val result = listRepository.renameList(listId, newName)
        return if (result > 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("重命名失败"))
        }
    }
    
    /**
     * 删除列表
     * 
     * 注意：不能删除最后一个列表
     */
    suspend fun deleteList(listId: Long): Result<Unit> {
        val allLists = lists.value
        if (allLists.size <= 1) {
            return Result.failure(IllegalStateException("不能删除最后一个列表"))
        }
        
        val result = listRepository.deleteList(listId)
        
        // 如果删除的是当前列表，切换到第一个列表
        if (listId == _currentListId.value) {
            val firstList = allLists.firstOrNull { it.id != listId }
            _currentListId.value = firstList?.id
        }
        
        return if (result > 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("删除失败"))
        }
    }
}
```

### 4.2 修改 InventoryViewModel

**新增功能**: 支持多列表过滤

```kotlin
class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val importCoordinator: ImportCoordinator,
    private val categoryRepository: CategoryRepository,
    private val listViewModel: InventoryListViewModel  // 新增依赖
) : ViewModel() {
    
    // 现有代码...
    
    // 修改：分页数据流支持列表过滤
    val itemsFlow: Flow<PagingData<InventoryItemEntity>> = combine(
        searchQuery,
        selectedCategoryId,
        listViewModel.currentListId  // 新增：监听当前列表
    ) { query, categoryId, listId ->
        Triple(query, categoryId, listId)
    }.flatMapLatest { (query, categoryId, listId) ->
        // 如果没有选中列表，返回空数据
        if (listId == null) {
            return@flatMapLatest flow { emit(PagingData.empty()) }
        }
        
        when {
            // 同时有搜索和分类
            query.isNotBlank() && categoryId != null -> {
                flow {
                    val items = inventoryRepository.searchItemsInListAndCategory(
                        listId, query, categoryId
                    )
                    emit(PagingData.from(items))
                }
            }
            // 仅搜索
            query.isNotBlank() -> flow {
                emit(PagingData.from(
                    inventoryRepository.searchItemsInList(listId, query)
                ))
            }
            // 仅分类
            categoryId != null -> flow {
                emit(PagingData.from(
                    inventoryRepository.getItemsByListAndCategory(listId, categoryId)
                ))
            }
            // 仅列表
            else -> inventoryRepository.getItemsByListId(listId)
        }
    }.cachedIn(viewModelScope)
    
    // 新增：添加商品到当前列表
    fun addItemToCurrentList(item: InventoryItemEntity) {
        val listId = listViewModel.currentListId.value ?: return
        viewModelScope.launch {
            inventoryRepository.addItem(item.copy(listId = listId), listId)
            setMessage(UiMessage.Success("已添加商品"))
        }
    }
}
```

### 4.3 ImportViewModel

**职责**: 管理文件导入流程和进度显示

```kotlin
data class ImportProgress(
    val isImporting: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
    val error: String? = null
)

class ImportViewModel(
    private val importCoordinator: ImportCoordinator,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()
    
    private var importJob: Job? = null
    
    /**
     * 导入文件到指定列表
     * 
     * @param listId 目标列表ID
     * @param fileUri 文件URI
     * @param fileType 文件类型（excel, access, database）
     * @return 导入的商品数量
     */
    suspend fun importFile(
        listId: Long,
        fileUri: Uri,
        fileType: FileType
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _progress.value = ImportProgress(isImporting = true)
            
            // 读取文件内容
            val items = importCoordinator.importByFileType(fileType, fileUri)
            
            if (items.isEmpty()) {
                _progress.value = ImportProgress(error = "文件中没有有效数据")
                return@withContext Result.failure(Exception("文件中没有有效数据"))
            }
            
            // 批量导入，显示进度
            val totalCount = items.size
            val batchSize = 100
            var importedCount = 0
            
            items.chunked(batchSize).forEach { batch ->
                // 设置 listId
                val itemsWithListId = batch.map { it.copy(listId = listId) }
                inventoryRepository.batchAddItems(itemsWithListId)
                
                importedCount += batch.size
                _progress.value = ImportProgress(
                    isImporting = true,
                    currentCount = importedCount,
                    totalCount = totalCount,
                    progress = importedCount.toFloat() / totalCount
                )
                
                // 避免阻塞UI
                delay(50)
            }
            
            _progress.value = ImportProgress(isImporting = false)
            Result.success(totalCount)
            
        } catch (e: Exception) {
            _progress.value = ImportProgress(
                isImporting = false,
                error = e.message ?: "导入失败"
            )
            Result.failure(e)
        }
    }
    
    /**
     * 取消导入
     */
    fun cancelImport() {
        importJob?.cancel()
        _progress.value = ImportProgress(isImporting = false)
    }
    
    /**
     * 重置进度
     */
    fun resetProgress() {
        _progress.value = ImportProgress()
    }
}
```

---

## 5. UI 组件设计

### 5.1 列表底部新增按钮

**组件**: `AddItemButton`

```kotlin
@Composable
fun AddItemButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.width(200.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("新增")
        }
    }
}
```

**使用位置**: 在 `InventoryListContent` 的 LazyColumn 底部

### 5.2 列表切换器（HorizontalPager）

**组件**: `InventoryListPager`

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryListPager(
    lists: List<InventoryListEntity>,
    currentListId: Long?,
    onListChange: (Long) -> Unit,
    content: @Composable (InventoryListEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = lists.indexOfFirst { it.id == currentListId }.coerceAtLeast(0),
        pageCount = { lists.size }
    )
    
    // 监听页面变化
    LaunchedEffect(pagerState.currentPage) {
        val currentList = lists.getOrNull(pagerState.currentPage)
        currentList?.let { onListChange(it.id) }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = modifier
    ) { page ->
        val list = lists[page]
        content(list)
    }
}
```

**使用位置**: 包裹整个 `InventoryListScreen` 的内容区域

### 5.3 列表选择下拉菜单

**组件**: `InventoryListDropdown`

```kotlin
@Composable
fun InventoryListDropdown(
    lists: List<InventoryListEntity>,
    currentListId: Long?,
    onListSelect: (Long) -> Unit,
    onRenameList: (Long, String) -> Unit,
    onDeleteList: (Long) -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameListId by remember { mutableStateOf<Long?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteListId by remember { mutableStateOf<Long?>(null) }
    
    Box(modifier = modifier) {
        // 当前列表名称 + 下拉图标
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lists.find { it.id == currentListId }?.name ?: "未选择",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "选择列表"
            )
        }
        
        // 下拉菜单
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(list.name)
                            if (list.id == currentListId) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "当前列表",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onListSelect(list.id)
                        expanded = false
                    }
                )
            }
            
            Divider()
            
            // 新建列表
            DropdownMenuItem(
                text = { Text("+ 新建列表") },
                onClick = {
                    onCreateList()
                    expanded = false
                }
            )
        }
    }
    
    // 重命名对话框
    if (showRenameDialog && renameListId != null) {
        RenameListDialog(
            currentName = lists.find { it.id == renameListId }?.name ?: "",
            onConfirm = { newName ->
                onRenameList(renameListId!!, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
    
    // 删除确认对话框
    if (showDeleteConfirm && deleteListId != null) {
        DeleteListConfirmDialog(
            listName = lists.find { it.id == deleteListId }?.name ?: "",
            onConfirm = {
                onDeleteList(deleteListId!!)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
```

**使用位置**: 替换 `InventoryTopBar` 中的标题部分

### 5.4 导入进度对话框

**组件**: `ImportProgressDialog`

```kotlin
@Composable
fun ImportProgressDialog(
    visible: Boolean,
    progress: ImportProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    AlertDialog(
        onDismissRequest = { /* 导入时不允许点击外部关闭 */ },
        title = { Text("正在导入...") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 进度条
                LinearProgressIndicator(
                    progress = progress.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 进度文本
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 数量统计
                Text(
                    text = "已导入: ${progress.currentCount} / ${progress.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 错误信息
                progress.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (progress.isImporting) {
                TextButton(onClick = onCancel) {
                    Text("取消导入")
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text("关闭")
                }
            }
        }
    )
}
```

**使用位置**: 在 `InventoryListScreen` 中，导入文件时显示

### 5.5 修改 ImportBottomSheet

**变更内容**: 移除"手动添加商品"和"拍照添加"选项，仅保留导入选项

```kotlin
@Composable
fun ImportBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onImportExcel: () -> Unit,
    onImportAccess: () -> Unit,
    onImportDatabase: () -> Unit,
    onManualCreate: () -> Unit,  // 新增：手动创建空列表
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "导入或新建库存列表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 手动添加（创建空列表）
            ImportOption(
                icon = Icons.Default.Add,
                title = "手动添加",
                description = "创建空白列表，手动添加商品",
                onClick = {
                    onManualCreate()
                    onDismiss()
                }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 导入 Excel
            ImportOption(
                icon = Icons.Default.Description,
                title = "导入 Excel",
                description = "从 .xlsx 或 .xls 文件导入",
                onClick = {
                    onImportExcel()
                    onDismiss()
                }
            )
            
            // 导入 Access
            ImportOption(
                icon = Icons.Default.Storage,
                title = "导入 Access",
                description = "从 .mdb 或 .accdb 文件导入",
                onClick = {
                    onImportAccess()
                    onDismiss()
                }
            )
            
            // 导入数据库文件
            ImportOption(
                icon = Icons.Default.Database,
                title = "导入数据库文件",
                description = "从 .db 文件导入",
                onClick = {
                    onImportDatabase()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 6. 数据流设计

### 6.1 创建新列表流程

```
用户点击右下角"+" → 显示 ImportBottomSheet
  ↓
选择"手动添加" → 创建空列表 → 切换到新列表 → 显示空列表界面
  ↓
选择"导入Excel" → 创建空列表 → 打开文件选择器 → 显示进度对话框 → 导入完成 → 切换到新列表
```

**代码实现**:

```kotlin
// 在 InventoryViewModel 中
fun handleImportOption(option: ImportOption) {
    viewModelScope.launch {
        // 1. 创建新列表
        val listId = listViewModel.createList()
        
        // 2. 切换到新列表
        listViewModel.switchToList(listId)
        
        // 3. 根据选项执行操作
        when (option) {
            ImportOption.Manual -> {
                // 显示手动添加对话框
                showManualAddDialog()
            }
            ImportOption.Excel -> {
                // 打开文件选择器
                openFilePicker(FileType.EXCEL, listId)
            }
            ImportOption.Access -> {
                openFilePicker(FileType.ACCESS, listId)
            }
            ImportOption.Database -> {
                openFilePicker(FileType.DATABASE, listId)
            }
        }
    }
}
```

### 6.2 切换列表流程

**方式1: 滑动切换**

```
用户左右滑动 → HorizontalPager 切换页面 → 更新 currentListId → itemsFlow 自动更新
```

**方式2: 下拉菜单切换**

```
用户点击标题 → 显示下拉菜单 → 选择列表 → 更新 currentListId → itemsFlow 自动更新
```

### 6.3 导入文件流程

```
用户选择文件 → 显示进度对话框 → 批量导入（每批100条）→ 更新进度 → 导入完成 → 关闭对话框
```

**代码实现**:

```kotlin
// 在 Activity 中处理文件选择结果
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    if (requestCode == REQUEST_FILE_PICKER && resultCode == RESULT_OK) {
        val uri = data?.data ?: return
        val listId = pendingImportListId ?: return
        
        viewModelScope.launch {
            importViewModel.importFile(listId, uri, pendingFileType)
                .onSuccess { count ->
                    showMessage("成功导入 $count 个商品")
                }
                .onFailure { error ->
                    showMessage("导入失败: ${error.message}")
                }
        }
    }
}
```

---

## 7. 状态管理设计

### 7.1 InventoryListUiState

```kotlin
data class InventoryListUiState(
    val lists: List<InventoryListEntity> = emptyList(),
    val currentListId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showListSelector: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renameListId: Long? = null,
    val showDeleteConfirm: Boolean = false,
    val deleteListId: Long? = null
)
```

### 7.2 ImportUiState

```kotlin
data class ImportUiState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val error: String? = null,
    val showProgressDialog: Boolean = false
)
```

---

## 8. 性能优化

### 8.1 数据库优化

1. **索引优化**:
   - `inventory_items.listId`: 加速按列表查询
   - `inventory_lists.displayOrder`: 加速列表排序

2. **外键约束**:
   - 使用 `CASCADE DELETE` 自动删除关联商品
   - 避免手动清理孤立数据

3. **批量操作**:
   - 导入时使用批量插入（每批100条）
   - 减少数据库事务次数

### 8.2 UI 优化

1. **分页加载**:
   - 使用 Paging 3 库
   - 每页加载20条数据
   - 支持预加载

2. **状态缓存**:
   - 使用 `cachedIn(viewModelScope)` 缓存分页数据
   - 切换列表时保留滚动位置

3. **懒加载**:
   - 使用 `LazyColumn` 渲染列表
   - 只渲染可见区域的商品

### 8.3 内存优化

1. **Flow 生命周期**:
   - 使用 `WhileSubscribed(5000)` 策略
   - 5秒无订阅者后停止收集

2. **避免内存泄漏**:
   - 使用 `viewModelScope` 管理协程
   - 及时取消长时间运行的任务

---

## 9. 错误处理

### 9.1 数据库错误

| 错误类型 | 处理方式 |
|---------|---------|
| 迁移失败 | 回滚到旧版本，提示用户 |
| 外键约束失败 | 显示错误消息，阻止操作 |
| 磁盘空间不足 | 提示用户清理空间 |

### 9.2 导入错误

| 错误类型 | 处理方式 |
|---------|---------|
| 文件格式错误 | 显示错误消息，保留空列表 |
| 文件读取失败 | 显示错误消息，允许重试 |
| 数据格式错误 | 跳过错误行，继续导入 |
| 导入中断 | 保留已导入数据，允许重试 |

### 9.3 用户操作错误

| 错误类型 | 处理方式 |
|---------|---------|
| 删除最后一个列表 | 阻止操作，显示提示 |
| 列表名称重复 | 显示错误消息，要求修改 |
| 列表名称为空 | 显示错误消息，要求输入 |
| 列表名称过长 | 显示错误消息，限制20字符 |

---

## 10. 测试策略

### 10.1 单元测试

**测试范围**:
- `InventoryListRepository`: CRUD 操作
- `InventoryListViewModel`: 状态管理
- `ImportViewModel`: 导入逻辑
- 数据库迁移逻辑

**测试工具**:
- JUnit 4
- MockK
- Coroutines Test
- Room Testing

### 10.2 集成测试

**测试场景**:
- 创建列表 → 添加商品 → 切换列表 → 验证数据隔离
- 导入文件 → 验证数据正确性
- 删除列表 → 验证级联删除
- 重命名列表 → 验证名称唯一性

### 10.3 UI 测试

**测试场景**:
- 滑动切换列表
- 下拉菜单选择列表
- 导入进度显示
- 列表底部新增按钮

**测试工具**:
- Compose Testing
- Espresso

---

## 11. 正确性属性（Property-Based Testing）

### 11.1 数据隔离属性

**属性1.1**: 不同列表的商品完全独立

```kotlin
@Test
fun `property - items in different lists are isolated`() {
    forAll { (list1Items: List<Item>, list2Items: List<Item>) ->
        // 创建两个列表
        val list1Id = createList("列表1")
        val list2Id = createList("列表2")
        
        // 添加商品
        addItems(list1Id, list1Items)
        addItems(list2Id, list2Items)
        
        // 验证：列表1的商品不包含列表2的商品
        val list1Result = getItems(list1Id)
        val list2Result = getItems(list2Id)
        
        list1Result.intersect(list2Result.toSet()).isEmpty()
    }
}
```

**验证**: Requirements 2.2, 2.4, 2.5

### 11.2 级联删除属性

**属性1.2**: 删除列表时自动删除所有关联商品

```kotlin
@Test
fun `property - deleting list cascades to items`() {
    forAll { items: List<Item> ->
        // 创建列表并添加商品
        val listId = createList()
        addItems(listId, items)
        
        // 删除列表
        deleteList(listId)
        
        // 验证：所有商品都被删除
        val remainingItems = getItems(listId)
        remainingItems.isEmpty()
    }
}
```

**验证**: Requirements 3.1.2

### 11.3 名称唯一性属性

**属性1.3**: 列表名称必须唯一

```kotlin
@Test
fun `property - list names are unique`() {
    forAll { name: String ->
        // 创建第一个列表
        val list1Id = createList(name)
        
        // 尝试创建同名列表
        val result = runCatching { createList(name) }
        
        // 验证：第二次创建失败
        result.isFailure
    }
}
```

**验证**: Requirements 2.3

### 11.4 导入幂等性属性

**属性1.4**: 导入相同文件多次，数据不重复

```kotlin
@Test
fun `property - importing same file twice does not duplicate data`() {
    forAll { fileContent: List<Item> ->
        val listId = createList()
        
        // 第一次导入
        importFile(listId, fileContent)
        val count1 = getItemCount(listId)
        
        // 第二次导入
        importFile(listId, fileContent)
        val count2 = getItemCount(listId)
        
        // 验证：数量翻倍（因为没有去重逻辑）
        count2 == count1 * 2
    }
}
```

**验证**: Requirements 2.6

---

## 12. 实施计划

### 阶段1: 数据库架构（2天）

- [ ] 创建 `InventoryListEntity` 模型
- [ ] 创建 `InventoryListDao` 接口
- [ ] 实现数据库迁移 `MIGRATION_7_8`
- [ ] 修改 `InventoryItemEntity` 添加 `listId`
- [ ] 修改 `InventoryDao` 添加列表过滤方法
- [ ] 编写数据库单元测试

### 阶段2: Repository 层（2天）

- [ ] 创建 `InventoryListRepository` 接口
- [ ] 实现 `InventoryListRepositoryImpl`
- [ ] 修改 `InventoryRepository` 支持列表过滤
- [ ] 修改 `InventoryRepositoryImpl` 实现新方法
- [ ] 编写 Repository 单元测试

### 阶段3: ViewModel 层（2天）

- [ ] 创建 `InventoryListViewModel`
- [ ] 修改 `InventoryViewModel` 支持多列表
- [ ] 创建 `ImportViewModel`
- [ ] 编写 ViewModel 单元测试

### 阶段4: UI 组件（3天）

- [ ] 实现 `AddItemButton` 组件
- [ ] 实现 `InventoryListPager` 组件
- [ ] 实现 `InventoryListDropdown` 组件
- [ ] 实现 `ImportProgressDialog` 组件
- [ ] 修改 `ImportBottomSheet` 组件
- [ ] 修改 `InventoryTopBar` 集成下拉菜单
- [ ] 编写 UI 测试

### 阶段5: 集成与测试（2天）

- [ ] 集成所有组件到 `InventoryListScreen`
- [ ] 修改 `AppContainer` 添加新依赖
- [ ] 端到端测试
- [ ] 性能测试
- [ ] Bug 修复

### 阶段6: 文档与发布（1天）

- [ ] 编写用户文档
- [ ] 更新 CHANGELOG
- [ ] 代码审查
- [ ] 发布版本

**总计**: 12天

---

## 13. 风险与缓解

### 13.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 数据迁移失败 | 高 | 中 | 充分测试，实现回滚机制 |
| 性能下降 | 中 | 中 | 使用索引，优化查询 |
| UI 复杂度增加 | 中 | 高 | 渐进式重构，保持简洁 |
| 内存泄漏 | 中 | 低 | 使用 viewModelScope，及时取消任务 |

### 13.2 用户体验风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 学习曲线陡峭 | 中 | 中 | 提供引导教程 |
| 误操作删除列表 | 高 | 低 | 添加确认对话框 |
| 导入失败数据丢失 | 高 | 低 | 保留原文件，支持重试 |
| 切换列表卡顿 | 中 | 低 | 使用缓存，优化动画 |

---

## 14. 后续优化

### 14.1 短期优化（1-2周）

- 列表排序功能（拖拽排序）
- 列表图标自定义
- 列表统计信息（商品数量、总价值等）
- 列表搜索功能

### 14.2 中期优化（1-2月）

- 列表间商品移动/复制
- 列表导出功能（Excel, PDF）
- 列表模板功能
- 列表备份与恢复

### 14.3 长期优化（3-6月）

- 多用户协作
- 云端同步
- 列表权限管理
- 列表分享功能

---

## 15. 参考资料

- [Material Design 3 - Navigation](https://m3.material.io/components/navigation-drawer)
- [Jetpack Compose - HorizontalPager](https://developer.android.com/jetpack/compose/layouts/pager)
- [Room - Database Migration](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Paging 3 Library](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)
- [Kotlin Coroutines - Flow](https://kotlinlang.org/docs/flow.html)

---

## 16. 附录

### 16.1 数据库 Schema 变更

**变更前（Version 7）**:

```sql
CREATE TABLE inventory_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    brand TEXT NOT NULL,
    model TEXT NOT NULL,
    parameters TEXT NOT NULL,
    barcode TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    remark TEXT NOT NULL,
    lastModified INTEGER NOT NULL
);
```

**变更后（Version 8）**:

```sql
CREATE TABLE inventory_lists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    displayOrder INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    lastModified INTEGER NOT NULL,
    isDefault INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE inventory_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    listId INTEGER NOT NULL,  -- 新增字段
    name TEXT NOT NULL,
    brand TEXT NOT NULL,
    model TEXT NOT NULL,
    parameters TEXT NOT NULL,
    barcode TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    remark TEXT NOT NULL,
    lastModified INTEGER NOT NULL,
    FOREIGN KEY (listId) REFERENCES inventory_lists(id) ON DELETE CASCADE
);
```

### 16.2 API 变更

**新增 API**:

```kotlin
// InventoryListRepository
suspend fun createList(name: String?): Long
suspend fun renameList(id: Long, newName: String): Int
suspend fun deleteList(id: Long): Int

// InventoryRepository
fun getItemsByListId(listId: Long): Flow<PagingData<InventoryItemEntity>>
suspend fun moveItemsToList(itemIds: List<Long>, targetListId: Long): Int
```

**修改 API**:

```kotlin
// InventoryRepository
suspend fun addItem(item: InventoryItemEntity, listId: Long): Long  // 新增 listId 参数
```

---

**文档版本**: 1.0  
**最后更新**: 2026-01-25  
**审批状态**: 待审批
