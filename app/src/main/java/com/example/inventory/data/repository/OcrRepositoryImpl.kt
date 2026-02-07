package com.example.inventory.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.model.OcrImageMeta
import com.example.inventory.data.model.OcrLayoutType
import com.example.inventory.data.model.OcrPipelineOutput
import com.example.inventory.data.model.OcrResult
import com.example.inventory.data.model.OcrSceneType
import com.example.inventory.data.model.OcrToken
import com.example.inventory.data.model.OcrTableCell
import com.example.inventory.data.model.OcrTableResult
import com.example.inventory.data.repository.ocr.OcrBitmapUtils
import com.example.inventory.data.repository.ocr.OcrImageProcessor
import com.example.inventory.data.repository.ocr.OcrLayoutBackend
import com.example.inventory.data.repository.ocr.OcrOrientationBackend
import com.example.inventory.data.repository.ocr.OcrRectifyBackend
import com.example.inventory.data.repository.ocr.OcrInferenceBackend
import com.example.inventory.data.repository.ocr.OcrTableStructureBackend
import com.example.inventory.data.repository.ocr.OcrScene
import com.example.inventory.data.repository.ocr.OcrTextResult
import com.example.inventory.data.repository.ocr.OcrBox
import com.example.inventory.data.repository.ocr.PaddleLiteBackend
import com.example.inventory.data.repository.ocr.PaddleLiteEngine
import com.example.inventory.data.repository.ocr.PaddleLiteModelSpec
import com.example.inventory.util.AppLogger
import com.huaban.analysis.jieba.JiebaSegmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.abs

/**
 * OCR 识别仓库实现类（重构版）
 * 
 * ## 核心功能
 * 使用 PaddleLite 深度学习框架进行离线 OCR 文字识别
 * 
 * ### 支持的功能
 * - ✅ 本地离线识别（基于 PaddleOCR 模型）
 * - ✅ 智能场景分类（文档/商品照片）
 * - ✅ 自动图片预处理（降采样、校正、增强）
 * - ⏳ 在线识别（预留接口，未实现）
 * - ✅ 结果合并和去重
 * 
 * ## 识别流程
 * 
 * ```
 * 1. 加载图片
 *    ├─ 检查文件是否存在
 *    ├─ 获取图片尺寸（不加载到内存）
 *    └─ 根据尺寸决定是否降采样
 * 
 * 2. 场景分类
 *    ├─ 分析边缘密度和宽高比
 *    └─ 判断是文档还是商品照片
 * 
 * 3. 选择模型
 *    ├─ 文档场景：使用 VL 模型（如果可用）
 *    └─ 商品场景：使用标准模型
 * 
 * 4. 图片预处理
 *    ├─ 透视校正（仅文档）
 *    ├─ 对比度增强（仅文档）
 *    └─ 锐化处理（仅文档）
 * 
 * 5. OCR 识别
 *    ├─ 加载字典文件
 *    ├─ 预处理为模型输入格式
 *    ├─ 运行 PaddleLite 推理
 *    └─ 解码输出结果
 * 
 * 6. 资源清理
 *    └─ 回收 Bitmap 内存
 * ```
 * 
 * ## 性能优化
 * - 大图自动降采样（最大 4096x4096）
 * - 使用 Dispatchers.Default 进行 CPU 密集型操作
 * - 智能选择预处理策略（仅文档场景）
 * - 字典文件缓存（避免重复加载）
 * 
 * ## 错误处理
 * - 文件不存在：返回空结果
 * - 模型加载失败：返回空结果
 * - 识别异常：记录日志并返回空结果
 * - 自动清理 Bitmap 资源
 * 
 * ## 依赖组件
 * - **OcrImageProcessor**: 图片预处理（场景分类、校正、增强）
 * - **OcrBitmapUtils**: Bitmap 工具（降采样、透视变换、格式转换）
 * - **PaddleLiteEngine**: PaddleLite 引擎封装（模型管理、推理、解码）
 * 
 * @param context Android 上下文，用于访问 assets 和文件系统
 * 
 * @see OcrImageProcessor 图片预处理器
 * @see OcrBitmapUtils Bitmap 工具类
 * @see PaddleLiteEngine PaddleLite 引擎
 */
class OcrRepositoryImpl(
    private val context: Context,
    private val backend: OcrInferenceBackend? = null,
    private val layoutBackend: OcrLayoutBackend? = null,
    private val orientationBackend: OcrOrientationBackend? = null,
    private val textlineOrientationBackend: OcrOrientationBackend? = null,
    private val rectifyBackend: OcrRectifyBackend? = null,
    private val tableStructureBackend: OcrTableStructureBackend? = null
) : OcrRepository {
    
    private val bitmapUtils = OcrBitmapUtils()
    private val preprocessor = OcrPreprocessor(
        imageProcessor = OcrImageProcessor(layoutBackend, rectifyBackend),
        bitmapUtils = bitmapUtils,
        orientationBackend = orientationBackend,
        textlineOrientationBackend = textlineOrientationBackend
    )
    
    /**
     * 标准 OCR 模型规格
     * 
     * 基于 PaddleOCR v4 的轻量级模型
     * - 检测模型：ppocrv4_det.nb
     * - 识别模型：ppocrv4_rec.nb
     * - 分类模型：ch_ppocr_mobile_v2.0_cls_opt.nb
     * - 字典文件：ppocr_keys_v1.txt（6623 个中文字符）
     * 
     * 适用场景：商品照片、一般文字识别
     */
    private val modelSpec = PaddleLiteModelSpec(
        detModel = "ppocr/ppocrv4_det.nb",
        recModel = "ppocr/ppocrv4_rec.nb",
        clsModel = "ppocr/ch_ppocr_mobile_v2.0_cls_opt.nb",
        dict = "ppocr/ppocr_keys_v1.txt"
    )
    
    /**
     * VL（Vision-Language）模型规格
     * 
     * 针对文档场景优化的模型
     * - 更高的识别精度
     * - 更好的版面分析能力
     * 
     * 适用场景：文档扫描、表格识别
     * 
     * 注意：仅当 assets 中存在 VL 模型文件时才会使用
     */
    private val vlModelSpec = PaddleLiteModelSpec(
        detModel = "ppocr_vl/vl_det_slim_opt.nb",
        recModel = "ppocr_vl/vl_rec_slim_opt.nb",
        clsModel = "ppocr_vl/vl_cls_opt.nb",
        dict = "ppocr_vl/vl_keys.txt"
    )
    private val inferenceBackend: OcrInferenceBackend = backend ?: PaddleLiteBackend(
        engine = PaddleLiteEngine(context),
        bitmapUtils = bitmapUtils,
        modelSpec = modelSpec,
        vlModelSpec = vlModelSpec
    )
    private val inferenceEngine = OcrInferenceEngine(inferenceBackend)
    private val postprocessor = OcrPostprocessor()

    /**
     * 本地 OCR 识别（主入口）
     * 
     * ## 功能说明
     * 使用 PaddleLite 进行离线 OCR 文字识别
     * 
     * ## 执行流程
     * 1. **文件检查**: 验证文件是否存在
     * 2. **图片加载**: 支持大图自动降采样（最大 4096x4096）
     * 3. **场景分类**: 判断是文档还是商品照片
     * 4. **模型选择**: 根据场景选择合适的模型
     * 5. **图片预处理**: 
     *    - 文档场景：透视校正 + 对比度增强 + 锐化
     *    - 商品场景：跳过预处理
     * 6. **OCR 识别**: 调用 PaddleLite 进行推理
     * 7. **结果构建**: 封装为 OcrResult 对象
     * 8. **资源清理**: 回收 Bitmap 内存
     * 
     * ## 性能优化
     * - 使用 Dispatchers.Default 进行 CPU 密集型操作
     * - 大图自动降采样，避免 OOM
     * - 智能预处理策略，仅文档场景启用
     * - 字典文件缓存，避免重复加载
     * 
     * ## 错误处理
     * - 文件不存在：返回空结果
     * - 图片加载失败：返回空结果
     * - 模型文件缺失：返回空结果
     * - 识别异常：记录日志并返回空结果
     * - 自动清理 Bitmap 资源（finally 块）
     * 
     * ## 示例
     * ```kotlin
     * val file = File("/path/to/image.jpg")
     * val result = ocrRepository.recognizeLocal(file)
     * 
     * result.groups.forEach { group ->
     *     println("识别文本: ${group.tokens.joinToString { it.text }}")
     *     println("置信度: ${group.confidence}")
     * }
     * ```
     * 
     * @param file 待识别的图片文件
     * @return OCR 识别结果，包含识别的文本组和置信度
     * 
     * @see OcrResult 识别结果封装
     * @see OcrGroup 文本组（一行或一个区域的文本）
     * @see OcrToken 文本标记（单个字符或词）
     */
    override suspend fun recognizeLocal(file: File): OcrResult = withContext(Dispatchers.IO) {
        recognizeLocalPipeline(file).result
    }

    override suspend fun recognizeLocalPipeline(file: File): OcrPipelineOutput = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyPipelineOutput()

        val bitmap = loadBitmapWithSampling(file) ?: return@withContext emptyPipelineOutput()

        try {
            withContext(Dispatchers.Default) {
                var corrected = bitmap
                var enhanced = bitmap
                var oriented = bitmap
                try {
                    val preprocessOutput = preprocessor.preprocess(bitmap)
                    oriented = preprocessOutput.oriented
                    corrected = preprocessOutput.corrected
                    enhanced = preprocessOutput.enhanced

                    val detected = inferenceEngine.detect(
                        scene = preprocessOutput.scene,
                        bitmap = enhanced
                    )

                    val tableOutput = if (preprocessOutput.layout == OcrLayoutType.Table) {
                        buildTableResult(
                            scene = preprocessOutput.scene,
                            bitmap = enhanced
                        )
                    } else {
                        null
                    }

                    val result = when {
                        tableOutput != null -> tableOutput.first
                        detected.isNullOrEmpty() -> {
                            val inferenceResult = inferenceEngine.infer(
                                scene = preprocessOutput.scene,
                                bitmap = enhanced
                            ) ?: return@withContext emptyPipelineOutput()
                            postprocessor.buildResult(
                                textResult = inferenceResult,
                                bitmap = enhanced
                            )
                        }
                        else -> {
                            buildDetectedResult(
                                scene = preprocessOutput.scene,
                                bitmap = enhanced,
                                boxes = detected
                            )
                        }
                    }

                    buildPipelineOutput(
                        scene = preprocessOutput.scene,
                        layout = preprocessOutput.layout,
                        bitmap = enhanced,
                        result = result,
                        table = tableOutput?.second
                    )
                } finally {
                    cleanupBitmaps(bitmap, oriented, corrected, enhanced)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.e("OCR 识别失败: ${e.message}", "OCR", e)
            bitmap.safeRecycle()
            emptyPipelineOutput()
        } catch (e: Exception) {
            AppLogger.e("OCR 识别失败: ${e.message}", "OCR", e)
            bitmap.safeRecycle()
            emptyPipelineOutput()
        }
    }
    
    /**
     * 在线 OCR 识别（预留接口）
     * 
     * ## 功能说明
     * 调用云端 OCR 服务进行识别（当前未实现）
     * 
     * ## 设计目的
     * - 作为本地识别的补充
     * - 处理复杂场景（手写、多语言等）
     * - 提供更高的识别精度
     * 
     * ## 实现建议
     * 可以集成以下服务：
     * - 百度 OCR API
     * - 腾讯云 OCR
     * - 阿里云 OCR
     * - Google Cloud Vision API
     * 
     * ## 注意事项
     * - 需要网络连接
     * - 需要 API 密钥配置
     * - 需要处理网络超时
     * - 需要考虑隐私和安全
     * 
     * @param file 待识别的图片文件
     * @return OCR 识别结果（当前返回空结果）
     */
    override suspend fun recognizeOnline(file: File): OcrResult {
        // TODO: 实现在线 OCR 识别
        // 1. 检查网络连接
        // 2. 上传图片到云端
        // 3. 调用 OCR API
        // 4. 解析返回结果
        // 5. 转换为 OcrResult 格式
        return OcrResult(emptyList())
    }

    /**
     * 合并本地和在线识别结果
     * 
     * ## 合并策略
     * 1. **去重**: 相同文本的结果只保留一个
     * 2. **优选**: 保留置信度最高的结果
     * 3. **补充**: 合并不同的识别结果
     * 
     * ## 执行流程
     * 1. 合并两个结果的所有文本组
     * 2. 按文本内容分组
     * 3. 每组选择置信度最高的结果
     * 4. 为没有 ID 的组生成 UUID
     * 
     * ## 使用场景
     * - 本地识别 + 在线识别的结果合并
     * - 多次识别结果的合并
     * - 不同模型结果的融合
     * 
     * ## 示例
     * ```kotlin
     * val localResult = recognizeLocal(file)
     * val onlineResult = recognizeOnline(file)
     * val merged = mergeResults(localResult, onlineResult)
     * ```
     * 
     * @param local 本地识别结果
     * @param online 在线识别结果
     * @return 合并后的识别结果
     */
    override suspend fun mergeResults(local: OcrResult, online: OcrResult): OcrResult {
        val merged = (local.groups + online.groups)
            .groupBy { it.tokens.joinToString { token -> token.text } }
            .map { (_, groups) ->
                val best = groups.maxByOrNull { it.confidence } ?: groups.first()
                OcrGroup(
                    id = best.id.ifBlank { UUID.randomUUID().toString() },
                    tokens = best.tokens,
                    confidence = best.confidence,
                    box = best.box
                )
            }
        return OcrResult(merged)
    }
    
    /**
     * 加载图片（支持智能降采样）
     * 
     * ## 功能说明
     * 根据图片尺寸自动决定是否降采样，避免大图导致 OOM
     * 
     * ## 降采样策略
     * - 图片尺寸 ≤ 4096x4096：原图加载
     * - 图片尺寸 > 4096x4096：自动降采样
     * 
     * ## 降采样算法
     * 使用 inSampleSize 参数：
     * - inSampleSize = 2：宽高各缩小一半，内存占用减少 75%
     * - inSampleSize = 4：宽高各缩小四分之一，内存占用减少 93.75%
     * 
     * ## 性能优化
     * - 第一次解码：inJustDecodeBounds = true，只读取尺寸
     * - 第二次解码：inJustDecodeBounds = false，加载图片
     * - 避免加载超大图片到内存
     * 
     * @param file 图片文件
     * @return Bitmap 对象，加载失败返回 null
     */
    private fun loadBitmapWithSampling(file: File): android.graphics.Bitmap? {
        // 先获取图片尺寸，不加载到内存
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        
        // 如果图片太大，使用采样率
        if (options.outWidth > OcrBitmapUtils.MAX_IMAGE_DIMENSION || 
            options.outHeight > OcrBitmapUtils.MAX_IMAGE_DIMENSION) {
            options.inSampleSize = bitmapUtils.calculateInSampleSize(
                options, 
                OcrBitmapUtils.MAX_IMAGE_DIMENSION, 
                OcrBitmapUtils.MAX_IMAGE_DIMENSION
            )
        }
        
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
    
    /**
     * 清理 Bitmap 资源
     * 
     * ## 功能说明
     * 安全地回收 Bitmap 内存，避免内存泄漏
     * 
     * ## 清理策略
     * - 如果 enhanced != original：回收 enhanced
     * - 如果 corrected != original 且 corrected != enhanced：回收 corrected
     * - 最后回收 original
     * 
     * ## 注意事项
     * - 避免重复回收同一个 Bitmap
     * - 按照创建的逆序回收
     * - 确保所有 Bitmap 都被回收
     * 
     * @param original 原始 Bitmap
     * @param corrected 校正后的 Bitmap
     * @param enhanced 增强后的 Bitmap
     */
    private fun cleanupBitmaps(
        original: android.graphics.Bitmap,
        oriented: android.graphics.Bitmap,
        corrected: android.graphics.Bitmap,
        enhanced: android.graphics.Bitmap
    ) {
        if (enhanced != original && enhanced != oriented && enhanced != corrected) {
            enhanced.safeRecycle()
        }
        if (corrected != original && corrected != oriented) {
            corrected.safeRecycle()
        }
        if (oriented != original) {
            oriented.safeRecycle()
        }
        original.safeRecycle()
    }

    private fun buildDetectedResult(
        scene: OcrScene,
        bitmap: android.graphics.Bitmap,
        boxes: List<OcrBox>
    ): OcrResult {
        if (boxes.isEmpty()) return OcrResult(emptyList())
        val ordered = boxes.sortedWith(compareBy<OcrBox> { it.top }.thenBy { it.left })
        val groups = mutableListOf<OcrGroup>()
        for (box in ordered) {
            val crop = bitmapUtils.cropBitmap(bitmap, boxToList(box)) ?: continue
            try {
                val textResult = inferenceEngine.infer(scene, crop) ?: continue
                if (textResult.text.isBlank()) continue
                val tokenBox = boxToList(box)
                val token = OcrToken(
                    text = textResult.text,
                    confidence = textResult.confidence,
                    box = tokenBox
                )
                groups.add(
                    OcrGroup(
                        id = UUID.randomUUID().toString(),
                        tokens = listOf(token),
                        confidence = textResult.confidence,
                        box = tokenBox
                    )
                )
            } finally {
                crop.safeRecycle()
            }
        }
        return OcrResult(groups)
    }

    private fun buildTableResult(
        scene: OcrScene,
        bitmap: android.graphics.Bitmap
    ): Pair<OcrResult, OcrTableResult>? {
        val backend = tableStructureBackend ?: return null
        val cells = backend.detectCells(bitmap) ?: return null
        if (cells.isEmpty()) return null
        val ordered = cells.sortedWith(compareBy<OcrBox> { it.top }.thenBy { it.left })
        val groups = mutableListOf<OcrGroup>()
        val tableCells = mutableListOf<OcrTableCell>()
        val tableBoxes = mutableListOf<OcrBox>()
        for (cellBox in ordered) {
            val cellCrop = bitmapUtils.cropBitmap(bitmap, boxToList(cellBox)) ?: continue
            try {
                val cellTokens = mutableListOf<OcrToken>()
                val cellText = StringBuilder()
                var confidenceSum = 0f
                var confidenceCount = 0
                val innerDetected = inferenceEngine.detect(scene, cellCrop)
                if (innerDetected.isNullOrEmpty()) {
                    val textResult = inferenceEngine.infer(scene, cellCrop)
                    if (textResult != null && textResult.text.isNotBlank()) {
                        val tokenBox = boxToList(cellBox)
                        val token = OcrToken(
                            text = textResult.text,
                            confidence = textResult.confidence,
                            box = tokenBox
                        )
                        cellTokens.add(token)
                        cellText.append(textResult.text)
                        confidenceSum += textResult.confidence
                        confidenceCount++
                        groups.add(
                            OcrGroup(
                                id = UUID.randomUUID().toString(),
                                tokens = listOf(token),
                                confidence = textResult.confidence,
                                box = tokenBox
                            )
                        )
                    }
                } else {
                    val orderedInner = innerDetected.sortedWith(compareBy<OcrBox> { it.top }.thenBy { it.left })
                    for (inner in orderedInner) {
                        val innerCrop = bitmapUtils.cropBitmap(cellCrop, boxToList(inner)) ?: continue
                        try {
                            val textResult = inferenceEngine.infer(scene, innerCrop) ?: continue
                            if (textResult.text.isBlank()) continue
                            val tokenBox = listOf(
                                cellBox.left + inner.left,
                                cellBox.top + inner.top,
                                cellBox.left + inner.right,
                                cellBox.top + inner.bottom
                            )
                            val token = OcrToken(
                                text = textResult.text,
                                confidence = textResult.confidence,
                                box = tokenBox
                            )
                            cellTokens.add(token)
                            cellText.append(textResult.text)
                            confidenceSum += textResult.confidence
                            confidenceCount++
                            groups.add(
                                OcrGroup(
                                    id = UUID.randomUUID().toString(),
                                    tokens = listOf(token),
                                    confidence = textResult.confidence,
                                    box = tokenBox
                                )
                            )
                        } finally {
                            innerCrop.safeRecycle()
                        }
                    }
                }
                if (cellTokens.isNotEmpty()) {
                    val cellConfidence = if (confidenceCount == 0) 0f else confidenceSum / confidenceCount
                    tableCells.add(
                        OcrTableCell(
                            id = UUID.randomUUID().toString(),
                            text = cellText.toString(),
                            confidence = cellConfidence,
                            box = boxToList(cellBox)
                        )
                    )
                    tableBoxes.add(cellBox)
                }
            } finally {
                cellCrop.safeRecycle()
            }
        }
        if (tableCells.isEmpty()) return null
        val indices = assignRowColIndices(tableBoxes)
        val indexedCells = tableCells.mapIndexed { index, cell ->
            cell.copy(
                rowIndex = indices.rowIndex.getOrNull(index),
                colIndex = indices.colIndex.getOrNull(index),
                rowSpan = indices.rowSpan.getOrNull(index) ?: 1,
                colSpan = indices.colSpan.getOrNull(index) ?: 1
            )
        }
        return Pair(OcrResult(groups), OcrTableResult(indexedCells))
    }

    private fun buildPipelineOutput(
        scene: OcrScene,
        layout: OcrLayoutType,
        bitmap: android.graphics.Bitmap,
        result: OcrResult,
        table: OcrTableResult? = null
    ): OcrPipelineOutput {
        return OcrPipelineOutput(
            scene = mapScene(scene),
            layout = layout,
            image = OcrImageMeta(bitmap.width, bitmap.height),
            result = result,
            table = table
        )
    }

    private fun mapScene(scene: OcrScene): OcrSceneType {
        return when (scene) {
            OcrScene.Document -> OcrSceneType.Document
            OcrScene.ItemPhoto -> OcrSceneType.ItemPhoto
        }
    }

    private fun emptyPipelineOutput(): OcrPipelineOutput {
        return OcrPipelineOutput(
            scene = OcrSceneType.ItemPhoto,
            layout = OcrLayoutType.TextLabel,
            image = OcrImageMeta(0, 0),
            result = OcrResult(emptyList()),
            table = null
        )
    }

    private fun boxToList(box: OcrBox): List<Float> {
        return listOf(box.left, box.top, box.right, box.bottom)
    }

    private data class TableIndexResult(
        val rowIndex: List<Int>,
        val colIndex: List<Int>,
        val rowSpan: List<Int>,
        val colSpan: List<Int>
    )

    private fun assignRowColIndices(boxes: List<OcrBox>): TableIndexResult {
        if (boxes.isEmpty()) {
            return TableIndexResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
        val heights = boxes.map { it.bottom - it.top }.filter { it > 1f }
        val widths = boxes.map { it.right - it.left }.filter { it > 1f }
        val avgHeight = if (heights.isEmpty()) 0f else heights.average().toFloat()
        val avgWidth = if (widths.isEmpty()) 0f else widths.average().toFloat()
        val rowThreshold = if (avgHeight > 0f) avgHeight * 0.5f else 8f
        val colThreshold = if (avgWidth > 0f) avgWidth * 0.5f else 8f
        val rowCenters = buildClusters(boxes.map { (it.top + it.bottom) / 2f }, rowThreshold)
        val colCenters = buildClusters(boxes.map { (it.left + it.right) / 2f }, colThreshold)
        val rowBands = rowCenters.map { center -> Pair(center - rowThreshold, center + rowThreshold) }
        val colBands = colCenters.map { center -> Pair(center - colThreshold, center + colThreshold) }

        val rowIndices = ArrayList<Int>(boxes.size)
        val colIndices = ArrayList<Int>(boxes.size)
        val rowSpans = ArrayList<Int>(boxes.size)
        val colSpans = ArrayList<Int>(boxes.size)

        for (box in boxes) {
            val rowHits = overlappingBands(box.top, box.bottom, rowBands)
            val colHits = overlappingBands(box.left, box.right, colBands)
            val rowIndex = if (rowHits.isNotEmpty()) rowHits.first() else centerIndex((box.top + box.bottom) / 2f, rowCenters)
            val colIndex = if (colHits.isNotEmpty()) colHits.first() else centerIndex((box.left + box.right) / 2f, colCenters)
            rowIndices.add(rowIndex)
            colIndices.add(colIndex)
            rowSpans.add(if (rowHits.isNotEmpty()) rowHits.size else 1)
            colSpans.add(if (colHits.isNotEmpty()) colHits.size else 1)
        }

        return TableIndexResult(rowIndices, colIndices, rowSpans, colSpans)
    }

    private fun buildClusters(values: List<Float>, threshold: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val clusters = mutableListOf<MutableList<Float>>()
        for (value in sorted) {
            if (clusters.isEmpty()) {
                clusters.add(mutableListOf(value))
                continue
            }
            val current = clusters.last()
            val avg = current.average().toFloat()
            if (abs(value - avg) <= threshold) {
                current.add(value)
            } else {
                clusters.add(mutableListOf(value))
            }
        }
        return clusters.map { it.average().toFloat() }
    }

    private fun centerIndex(value: Float, centers: List<Float>): Int {
        if (centers.isEmpty()) return 0
        var bestIndex = 0
        var bestDist = abs(value - centers[0])
        for (i in 1 until centers.size) {
            val dist = abs(value - centers[i])
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun overlappingBands(start: Float, end: Float, bands: List<Pair<Float, Float>>): List<Int> {
        if (bands.isEmpty()) return emptyList()
        val hits = mutableListOf<Int>()
        val span = (end - start).coerceAtLeast(1f)
        for (i in bands.indices) {
            val band = bands[i]
            val overlap = overlapLength(start, end, band.first, band.second)
            val bandSize = (band.second - band.first).coerceAtLeast(1f)
            val ratio = overlap / minOf(span, bandSize)
            if (ratio >= 0.3f) {
                hits.add(i)
            }
        }
        return hits
    }

    private fun overlapLength(aStart: Float, aEnd: Float, bStart: Float, bEnd: Float): Float {
        val left = maxOf(aStart, bStart)
        val right = minOf(aEnd, bEnd)
        return (right - left).coerceAtLeast(0f)
    }
}

private data class OcrPreprocessOutput(
    val scene: OcrScene,
    val layout: OcrLayoutType,
    val oriented: android.graphics.Bitmap,
    val corrected: android.graphics.Bitmap,
    val enhanced: android.graphics.Bitmap
)

/**
 * OCR 预处理模块
 *
 * 负责完成场景分类、版面分类与图像增强等预处理步骤。
 */
private class OcrPreprocessor(
    private val imageProcessor: OcrImageProcessor,
    private val bitmapUtils: OcrBitmapUtils,
    private val orientationBackend: OcrOrientationBackend? = null,
    private val textlineOrientationBackend: OcrOrientationBackend? = null
) {
    fun preprocess(bitmap: android.graphics.Bitmap): OcrPreprocessOutput {
        val angle = orientationBackend?.classifyAngle(bitmap) ?: 0
        val orientedDoc = if (angle == 0) bitmap else bitmapUtils.rotateBitmap(bitmap, angle)
        val textlineAngle = textlineOrientationBackend?.classifyAngle(orientedDoc) ?: 0
        val oriented = if (textlineAngle == 0) {
            orientedDoc
        } else {
            val rotated = bitmapUtils.rotateBitmap(orientedDoc, textlineAngle)
            if (orientedDoc != bitmap) {
                orientedDoc.safeRecycle()
            }
            rotated
        }
        val scene = imageProcessor.classifyScene(oriented)
        val layout = imageProcessor.classifyLayout(oriented)
        val corrected = imageProcessor.correctBitmap(oriented, scene, bitmapUtils)
        val enhanced = imageProcessor.enhanceBitmap(corrected, scene)
        return OcrPreprocessOutput(
            scene = scene,
            layout = layout,
            oriented = oriented,
            corrected = corrected,
            enhanced = enhanced
        )
    }
}

/**
 * OCR 推理模块
 *
 * 负责模型选择、输入准备与推理执行。
 */
private class OcrInferenceEngine(
    private val backend: OcrInferenceBackend
) {
    fun infer(scene: OcrScene, bitmap: android.graphics.Bitmap): OcrTextResult? {
        if (!backend.isAvailable()) {
            return null
        }
        return runCatching {
            backend.infer(scene, bitmap)
        }.onFailure { e ->
            AppLogger.e("OCR 识别失败: ${e.message}", "OCR", e)
        }.getOrNull()
    }

    fun detect(scene: OcrScene, bitmap: android.graphics.Bitmap): List<OcrBox>? {
        if (!backend.isAvailable()) {
            return null
        }
        return runCatching {
            backend.detect(scene, bitmap)
        }.onFailure { e ->
            AppLogger.e("OCR 检测失败: ${e.message}", "OCR", e)
        }.getOrNull()
    }
}

private fun android.graphics.Bitmap.safeRecycle() {
    if (!isRecycled) {
        recycle()
    }
}

/**
 * OCR 后处理模块
 *
 * 负责将模型输出组装成可叠加的文本组与 bbox。
 */
private class OcrPostprocessor {
    private val segmenter by lazy { JiebaSegmenter() }

    fun buildResult(textResult: OcrTextResult, bitmap: android.graphics.Bitmap): OcrResult {
        if (textResult.text.isBlank()) {
            return OcrResult(emptyList())
        }

        val fullText = textResult.text
        val lineBox = listOf(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val words: List<String> = if (containsChinese(fullText)) {
            segmenter.sentenceProcess(fullText).filter { it.isNotBlank() }
        } else {
            fullText.split(Regex("[\\s,;:]+")).filter { it.isNotBlank() }
        }

        if (words.isEmpty()) return OcrResult(emptyList())

        val wordGroups = mutableListOf<OcrGroup>()
        var currentX = lineBox[0]
        val lineWidth = lineBox[2] - lineBox[0]
        val totalCharCountInLine = words.joinToString("").length
        if (totalCharCountInLine == 0) return OcrResult(emptyList())

        for (word in words) {
            val wordCharCount = word.length
            val wordWidth = (wordCharCount.toFloat() / totalCharCountInLine) * lineWidth
            val wordBox = listOf(currentX, lineBox[1], currentX + wordWidth, lineBox[3])
            currentX += wordWidth

            val wordToken = OcrToken(text = word, confidence = textResult.confidence, box = wordBox)
            val wordGroup = OcrGroup(
                id = UUID.randomUUID().toString(),
                tokens = listOf(wordToken),
                confidence = textResult.confidence,
                box = wordBox
            )
            wordGroups.add(wordGroup)
        }

        return OcrResult(wordGroups)
    }

    private fun containsChinese(text: String): Boolean {
        val chineseRegex = "[\u4e00-\u9fa5]".toRegex()
        return chineseRegex.containsMatchIn(text)
    }
}
