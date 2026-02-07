package com.example.inventory.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.inventory.data.db.InventoryDatabase
import com.example.inventory.data.importer.AccessImporter
import com.example.inventory.data.importer.ExcelImporter
import com.example.inventory.data.importer.GenericDatabaseImporter
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.repository.AuthRepository
import com.example.inventory.data.repository.AuthRepositoryImpl
import com.example.inventory.data.repository.ExportRepository
import com.example.inventory.data.repository.ExportRepositoryImpl
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.data.repository.InventoryRepositoryImpl
import com.example.inventory.data.repository.InventoryListRepository
import com.example.inventory.data.repository.InventoryListRepositoryImpl
import com.example.inventory.data.repository.OcrRepository
import com.example.inventory.data.repository.OcrRepositoryImpl
import com.example.inventory.data.repository.ocr.OcrBitmapUtils
import com.example.inventory.data.repository.ocr.OcrLayoutBackend
import com.example.inventory.data.repository.ocr.OcrInferenceBackend
import com.example.inventory.data.repository.ocr.PaddleLiteBackend
import com.example.inventory.data.repository.ocr.PaddleLiteEngine
import com.example.inventory.data.repository.ocr.PaddleLiteModelSpec
import com.example.inventory.data.repository.ocr.OnnxRuntimeBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeModelSpec
import com.example.inventory.data.repository.ocr.OnnxLayoutModelSpec
import com.example.inventory.data.repository.ocr.OnnxOrientationModelSpec
import com.example.inventory.data.repository.ocr.OnnxRectifyModelSpec
import com.example.inventory.data.repository.ocr.OnnxTableStructureModelSpec
import com.example.inventory.data.repository.ocr.OnnxDetModelSpec
import com.example.inventory.data.repository.ocr.OnnxRuntimeLayoutBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeOrientationBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeRectifyBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeDetBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeTableLayoutBackend
import com.example.inventory.data.repository.ocr.OnnxRuntimeTableStructureBackend
import com.example.inventory.data.repository.S3StorageRepository
import com.example.inventory.data.repository.StorageRepository
import com.example.inventory.data.repository.SyncRepository
import com.example.inventory.data.repository.SyncRepositoryImpl
import com.example.inventory.data.repository.SearchHistoryRepository
import com.example.inventory.data.repository.SearchHistoryRepositoryImpl
import com.example.inventory.data.repository.CategoryRepository
import com.example.inventory.data.repository.CategoryRepositoryImpl
import com.example.inventory.util.ImageCache
import com.example.inventory.util.InventoryUndoManager
import com.example.inventory.util.NetworkMonitor
import com.example.inventory.util.Constants
import com.example.inventory.util.PrefsKeys
import com.example.inventory.util.SecurePreferencesManager
import com.example.inventory.util.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

/**
 * 应用容器
 * 
 * 负责创建和管理应用级别的依赖项
 * 
 * @param context Application 实例（从 InventoryApplication 传入）
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    
    /**
     * Application 实例
     * 
     * 用于需要 Application 类型的场景（如 AndroidViewModel）
     */
    val application: Application = context.applicationContext as Application
    
    // 网络监控
    val networkMonitor: NetworkMonitor = NetworkMonitor(appContext)
    
    // 图片缓存
    val imageCache: ImageCache = ImageCache.getInstance(appContext)
    
    // 撤销/重做管理器
    val undoManager: InventoryUndoManager = InventoryUndoManager(maxHistorySize = Constants.Undo.MAX_HISTORY_SIZE)
    
    private val database: InventoryDatabase = Room.databaseBuilder(
        appContext,
        InventoryDatabase::class.java,
        "inventory.db"
    )
        .setQueryExecutor(Executors.newSingleThreadExecutor())
        .setTransactionExecutor(Executors.newSingleThreadExecutor())
        .addMigrations(
            InventoryDatabase.MIGRATION_1_2,
            InventoryDatabase.MIGRATION_2_3,
            InventoryDatabase.MIGRATION_3_4,
            InventoryDatabase.MIGRATION_4_5,
            InventoryDatabase.MIGRATION_5_6,
            InventoryDatabase.MIGRATION_6_7,  // 添加复合索引
            InventoryDatabase.MIGRATION_7_8,  // 多库存列表支持
            InventoryDatabase.MIGRATION_8_9,  // 添加 unit 和 location 字段
            InventoryDatabase.MIGRATION_9_10
        )
        .build()

    val inventoryRepository: InventoryRepository = InventoryRepositoryImpl(
        database.inventoryDao(),
        database.categoryDao(),
        database
    )
    val inventoryListRepository: InventoryListRepository = InventoryListRepositoryImpl(
        database.inventoryListDao()
    )
    val categoryRepository: CategoryRepository = CategoryRepositoryImpl(
        database.categoryDao(),
        database.inventoryDao()
    )
    val authRepository: AuthRepository = AuthRepositoryImpl(appContext)
    private val ocrBitmapUtils = OcrBitmapUtils()
    private val paddleBackend = PaddleLiteBackend(
        engine = PaddleLiteEngine(appContext),
        bitmapUtils = ocrBitmapUtils,
        modelSpec = PaddleLiteModelSpec(
            detModel = Constants.Ocr.MODEL_DET,
            recModel = Constants.Ocr.MODEL_REC,
            clsModel = Constants.Ocr.MODEL_CLS,
            dict = Constants.Ocr.DICT_PATH
        ),
        vlModelSpec = PaddleLiteModelSpec(
            detModel = "ppocr_vl/vl_det_slim_opt.nb",
            recModel = "ppocr_vl/vl_rec_slim_opt.nb",
            clsModel = "ppocr_vl/vl_cls_opt.nb",
            dict = "ppocr_vl/vl_keys.txt"
        )
    )
    private val onnxBackend = OnnxRuntimeBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        modelSpec = OnnxRuntimeModelSpec(
            recModel = Constants.Ocr.ONNX_REC_MODEL,
            dict = Constants.Ocr.ONNX_DICT_PATH,
            inputHeight = Constants.Ocr.INPUT_HEIGHT,
            inputWidth = Constants.Ocr.INPUT_WIDTH,
            detModel = Constants.Ocr.ONNX_DET_MODEL
        )
    )
    private val openOcrBackend = OnnxRuntimeBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        modelSpec = OnnxRuntimeModelSpec(
            recModel = Constants.Ocr.OPENOCR_REC_MODEL,
            dict = Constants.Ocr.OPENOCR_DICT_PATH,
            inputHeight = Constants.Ocr.INPUT_HEIGHT,
            inputWidth = Constants.Ocr.INPUT_WIDTH,
            detModel = Constants.Ocr.OPENOCR_DET_MODEL
        )
    )
    private val ocrBackendModeProvider = OcrBackendModeProvider(appContext)
    private val ocrBackend: OcrInferenceBackend = RuntimeSwitchingOcrBackend(
        modeProvider = ocrBackendModeProvider,
        paddleBackend = paddleBackend,
        onnxBackend = onnxBackend,
        openOcrBackend = openOcrBackend
    )
    private val ocrDocLayoutBackend = OnnxRuntimeLayoutBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        spec = OnnxLayoutModelSpec(
            model = Constants.Ocr.ONNX_LAYOUT_MODEL,
            inputSize = Constants.Ocr.LAYOUT_INPUT_SIZE
        )
    )
    private val ocrTableLayoutBackend = run {
        val detBackend = OnnxRuntimeDetBackend(
            context = appContext,
            bitmapUtils = ocrBitmapUtils,
            spec = OnnxDetModelSpec(
                model = Constants.Ocr.ONNX_TABLE_LAYOUT_MODEL,
                inputSize = Constants.Ocr.DET_INPUT_SIZE
            )
        )
        OnnxRuntimeTableLayoutBackend(detBackend)
    }
    private val ocrLayoutBackend = object : OcrLayoutBackend {
        override fun classify(bitmap: android.graphics.Bitmap): com.example.inventory.data.model.OcrLayoutType? {
            return ocrDocLayoutBackend.classify(bitmap) ?: ocrTableLayoutBackend.classify(bitmap)
        }
    }
    private val ocrOrientationBackend = OnnxRuntimeOrientationBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        spec = OnnxOrientationModelSpec(
            model = Constants.Ocr.ONNX_DOC_ORI_MODEL,
            inputSize = Constants.Ocr.ORI_INPUT_SIZE
        )
    )
    private val ocrTextlineOrientationBackend = OnnxRuntimeOrientationBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        spec = OnnxOrientationModelSpec(
            model = Constants.Ocr.ONNX_TEXTLINE_ORI_MODEL,
            inputSize = Constants.Ocr.ORI_INPUT_SIZE
        )
    )
    private val ocrRectifyBackend = OnnxRuntimeRectifyBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        spec = OnnxRectifyModelSpec(
            model = Constants.Ocr.ONNX_RECTIFY_MODEL
        )
    )
    private val ocrTableStructureBackend = OnnxRuntimeTableStructureBackend(
        context = appContext,
        bitmapUtils = ocrBitmapUtils,
        spec = OnnxTableStructureModelSpec(
            model = Constants.Ocr.ONNX_TABLE_STRUCTURE_MODEL,
            inputSize = Constants.Ocr.DET_INPUT_SIZE
        )
    )
    val ocrRepository: OcrRepository = OcrRepositoryImpl(
        appContext,
        ocrBackend,
        ocrLayoutBackend,
        ocrOrientationBackend,
        ocrTextlineOrientationBackend,
        ocrRectifyBackend,
        ocrTableStructureBackend
    )
    val storageRepository: StorageRepository = S3StorageRepository(appContext)
    val exportRepository: ExportRepository = ExportRepositoryImpl(appContext)
    val searchHistoryRepository: SearchHistoryRepository = SearchHistoryRepositoryImpl(database.searchHistoryDao())
    val syncRepository: SyncRepository = SyncRepositoryImpl(
        appContext,
        exportRepository,
        storageRepository,
        inventoryRepository
    )
    val importCoordinator = ImportCoordinator(
        listOf(ExcelImporter(), AccessImporter(appContext.cacheDir), GenericDatabaseImporter())
    )

    fun clearOnnxSessions() {
        com.example.inventory.data.repository.ocr.clearOnnxSessionCache()
    }
}

private class OcrBackendModeProvider(private val context: Context) {
    @Volatile
    private var lastMode: String? = null
    
    /**
     * 缓存的 OCR 后端模式
     * 
     * 使用 @Volatile 确保多线程可见性
     * 默认值为 AUTO，通过 Flow 异步更新
     */
    @Volatile
    private var cachedMode: String = Constants.Ocr.BACKEND_AUTO
    
    /**
     * 协程作用域，用于监听设置变化
     * 使用 SupervisorJob 确保单个失败不影响其他任务
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // 异步监听 DataStore 变化，更新缓存
        // 这样避免了每次 getMode() 都阻塞主线程
        context.settingsDataStore.data
            .map { prefs -> prefs[PrefsKeys.OCR_BACKEND_PREF_KEY] ?: Constants.Ocr.BACKEND_AUTO }
            .onEach { mode ->
                cachedMode = mode
                maybeClearOnnx(mode)
            }
            .launchIn(scope)
    }

    /**
     * 获取当前 OCR 后端模式
     * 
     * 直接返回缓存值，不阻塞调用线程
     * 首次调用可能返回默认值，后续会通过 Flow 自动更新
     */
    fun getMode(): String = cachedMode

    private fun maybeClearOnnx(mode: String) {
        val previous = lastMode
        if (previous != null && previous != mode) {
            com.example.inventory.data.repository.ocr.clearOnnxSessionCache()
        }
        lastMode = mode
    }
}

private class RuntimeSwitchingOcrBackend(
    private val modeProvider: OcrBackendModeProvider,
    private val paddleBackend: OcrInferenceBackend,
    private val onnxBackend: OcrInferenceBackend,
    private val openOcrBackend: OcrInferenceBackend
) : OcrInferenceBackend {
    override fun isAvailable(): Boolean {
        return paddleBackend.isAvailable() || onnxBackend.isAvailable() || openOcrBackend.isAvailable()
    }

    override fun infer(scene: com.example.inventory.data.repository.ocr.OcrScene, bitmap: android.graphics.Bitmap): com.example.inventory.data.repository.ocr.OcrTextResult? {
        val mode = modeProvider.getMode()
        val primary = when (mode) {
            Constants.Ocr.BACKEND_OPENOCR -> openOcrBackend
            Constants.Ocr.BACKEND_ONNX -> onnxBackend
            Constants.Ocr.BACKEND_PADDLE -> paddleBackend
            else -> null
        }
        val primaryResult = primary?.infer(scene, bitmap)
        if (primaryResult != null) return primaryResult

        val order = when (mode) {
            Constants.Ocr.BACKEND_OPENOCR -> listOf(onnxBackend, paddleBackend)
            Constants.Ocr.BACKEND_ONNX -> listOf(paddleBackend)
            Constants.Ocr.BACKEND_PADDLE -> listOf(onnxBackend)
            else -> listOf(onnxBackend, paddleBackend)
        }
        for (backend in order) {
            val result = backend.infer(scene, bitmap)
            if (result != null) return result
        }
        return null
    }

    override fun detect(scene: com.example.inventory.data.repository.ocr.OcrScene, bitmap: android.graphics.Bitmap): List<com.example.inventory.data.repository.ocr.OcrBox>? {
        val mode = modeProvider.getMode()
        val primary = when (mode) {
            Constants.Ocr.BACKEND_OPENOCR -> openOcrBackend
            Constants.Ocr.BACKEND_ONNX -> onnxBackend
            Constants.Ocr.BACKEND_PADDLE -> paddleBackend
            else -> null
        }
        val primaryResult = primary?.detect(scene, bitmap)
        if (!primaryResult.isNullOrEmpty()) return primaryResult

        val order = when (mode) {
            Constants.Ocr.BACKEND_OPENOCR -> listOf(onnxBackend, paddleBackend)
            Constants.Ocr.BACKEND_ONNX -> listOf(paddleBackend)
            Constants.Ocr.BACKEND_PADDLE -> listOf(onnxBackend)
            else -> listOf(onnxBackend, paddleBackend)
        }
        for (backend in order) {
            val result = backend.detect(scene, bitmap)
            if (!result.isNullOrEmpty()) return result
        }
        return null
    }
}
