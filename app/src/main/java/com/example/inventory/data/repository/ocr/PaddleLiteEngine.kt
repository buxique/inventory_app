package com.example.inventory.data.repository.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.LruCache
import com.example.inventory.util.Constants
import com.example.inventory.data.model.OcrLayoutType
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * PaddleLite 引擎封装类
 * 
 * ## 核心功能
 * 封装 PaddleLite 深度学习推理引擎，提供 OCR 模型管理和推理功能
 * 
 * ### 主要职责
 * 1. **模型文件管理**: 从 assets 复制模型到缓存目录
 * 2. **模型加载**: 创建和配置 PaddleLite 预测器
 * 3. **推理执行**: 运行 OCR 识别推理
 * 4. **结果解码**: 将模型输出转换为文本和置信度
 * 5. **字典管理**: 加载和缓存字符字典
 * 
 * ## 安全机制
 * 
 * ### 路径安全
 * - 防止路径遍历攻击（检查 ".." 和绝对路径）
 * - 验证文件路径在合法目录内
 * - 使用 canonicalPath 进行路径规范化
 * 
 * ### 类源验证
 * - 验证 PaddleLite 类来自可信源
 * - 检查 codeSource 是否包含 "paddle"
 * - 防止恶意类注入
 * 
 * ## 性能优化
 * 
 * ### 字典缓存
 * - 使用 ConcurrentHashMap 缓存已加载的字典
 * - 使用 computeIfAbsent 保证原子性
 * - 避免重复加载相同字典文件
 * 
 * ### 模型文件缓存
 * - 检查缓存目录中是否已存在模型文件
 * - 避免重复复制 assets 文件
 * - 减少 I/O 操作
 * 
 * ## 模型规格
 * 
 * ### 输入格式
 * - 高度：48 像素
 * - 宽度：320 像素
 * - 通道：3（RGB）
 * - 数据类型：Float32
 * - 数值范围：[0, 1]（归一化）
 * 
 * ### 输出格式
 * - 形状：[batch_size, seq_len, dict_size]
 * - 数据类型：Float32
 * - 含义：每个时间步的字符概率分布
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * val engine = PaddleLiteEngine(context)
 * 
 * // 1. 检查可用性
 * if (!engine.isPaddleLiteAvailable()) {
 *     println("PaddleLite 不可用")
 *     return
 * }
 * 
 * // 2. 准备模型文件
 * val spec = PaddleLiteModelSpec(...)
 * val files = engine.prepareModelFiles(spec) ?: return
 * 
 * // 3. 加载字典
 * val dict = engine.loadDict(files.dict)
 * 
 * // 4. 运行识别
 * val result = engine.runRecognizer(
 *     modelFile = files.recModel,
 *     inputData = preprocessedData,
 *     inputHeight = 48,
 *     inputWidth = 320,
 *     dict = dict
 * )
 * 
 * println("识别结果: ${result?.text}")
 * println("置信度: ${result?.confidence}")
 * ```
 * 
 * @param context Android 上下文，用于访问 assets 和文件系统
 * 
 * @see PaddleLiteModelSpec 模型规格定义
 * @see PaddleLiteModelFiles 模型文件集合
 * @see OcrTextResult OCR 识别结果
 */
class PaddleLiteEngine(private val context: Context) {
    
    companion object {
        /**
         * OCR 输入图片高度（像素）
         * 
         * PaddleOCR 模型的标准输入高度
         */
        const val OCR_INPUT_HEIGHT = 48
        
        /**
         * OCR 输入图片宽度（像素）
         * 
         * PaddleOCR 模型的标准输入宽度
         */
        const val OCR_INPUT_WIDTH = 320
    }
    
    /**
     * PaddleLite 库加载状态
     * 
     * 使用 lazy 延迟加载，避免启动时加载失败导致崩溃
     * 
     * ## 加载流程
     * 1. 尝试加载 native 库 "paddle_lite_jni"
     * 2. 加载成功返回 true
     * 3. 加载失败（UnsatisfiedLinkError）返回 false
     * 
     * ## 失败原因
     * - 设备架构不支持（如 x86）
     * - 库文件缺失
     * - 库文件损坏
     */
    private val paddleLiteLoaded: Boolean by lazy {
        runCatching {
            try {
                System.loadLibrary("paddle_lite_jni")
                true
            } catch (e: UnsatisfiedLinkError) {
                // Log failure but don't crash here; isPaddleLiteAvailable will return false
                false
            }
        }.getOrDefault(false)
    }
    
    /**
     * 检查 PaddleLite 是否可用
     * 
     * ## 检查项
     * 1. Native 库是否加载成功
     * 2. MobileConfig 类是否存在
     * 3. PaddlePredictor 类是否存在
     * 
     * ## 返回值
     * - true: PaddleLite 可用，可以进行 OCR 识别
     * - false: PaddleLite 不可用，应使用在线识别或提示用户
     * 
     * ## 使用场景
     * - 应用启动时检查
     * - OCR 识别前检查
     * - 功能开关判断
     * 
     * @return true 如果 PaddleLite 可用
     */
    fun isPaddleLiteAvailable(): Boolean {
        if (!paddleLiteLoaded) return false
        return runCatching {
            Class.forName("com.baidu.paddle.lite.MobileConfig")
            Class.forName("com.baidu.paddle.lite.PaddlePredictor")
            true
        }.getOrDefault(false)
    }
    
    /**
     * 准备模型文件
     * 
     * ## 功能说明
     * 从 assets 目录复制模型文件到应用缓存目录
     * 
     * ## 执行流程
     * 1. 复制检测模型（det）
     * 2. 复制识别模型（rec）
     * 3. 复制分类模型（cls）
     * 4. 复制字典文件（dict）
     * 5. 返回文件集合
     * 
     * ## 缓存策略
     * - 如果文件已存在且大小 > 0，直接返回
     * - 否则从 assets 复制到缓存目录
     * - 缓存目录：{filesDir}/paddle/
     * 
     * ## 错误处理
     * - 任何文件复制失败都返回 null
     * - 调用方应检查返回值
     * 
     * @param spec 模型规格，包含模型文件路径
     * @return 模型文件集合，失败返回 null
     * 
     * @see PaddleLiteModelSpec 模型规格定义
     * @see PaddleLiteModelFiles 模型文件集合
     */
    fun prepareModelFiles(spec: PaddleLiteModelSpec): PaddleLiteModelFiles? {
        val det = copyAssetToCache(spec.detModel) ?: return null
        val rec = copyAssetToCache(spec.recModel) ?: return null
        val cls = copyAssetToCache(spec.clsModel) ?: return null
        val dict = copyAssetToCache(spec.dict) ?: return null
        return PaddleLiteModelFiles(det, rec, cls, dict)
    }
    
    /**
     * 选择模型规格
     * 
     * ## 选择策略
     * - **文档场景 + VL 模型可用**: 使用 VL 模型（更高精度）
     * - **其他情况**: 使用标准模型
     * 
     * ## VL 模型优势
     * - 针对文档场景优化
     * - 更好的版面分析能力
     * - 更高的识别精度
     * 
     * ## 检查逻辑
     * 1. 判断场景类型
     * 2. 检查 VL 模型文件是否存在
     * 3. 返回合适的模型规格
     * 
     * @param scene OCR 场景类型（文档/商品照片）
     * @param modelSpec 标准模型规格
     * @param vlModelSpec VL 模型规格
     * @return 选择的模型规格
     * 
     * @see OcrScene 场景类型枚举
     */
    fun selectModelSpec(scene: OcrScene, modelSpec: PaddleLiteModelSpec, vlModelSpec: PaddleLiteModelSpec): PaddleLiteModelSpec {
        return if (scene == OcrScene.Document && hasAllAssets(vlModelSpec)) {
            vlModelSpec
        } else {
            modelSpec
        }
    }
    
    private val cachedDictMap = LruCache<String, List<String>>(Constants.Cache.QUERY_DEFAULT_MAX_SIZE)

    /**
     * 加载字典文件
     * 
     * ## 功能说明
     * 加载 OCR 字符字典，用于解码模型输出
     * 
     * ## 字典格式
     * - 每行一个字符
     * - UTF-8 编码
     * - 空行会被过滤
     * 
     * ## 缓存机制
     * - 使用 ConcurrentHashMap 缓存已加载的字典
     * - 使用文件绝对路径作为缓存键
     * - 使用 computeIfAbsent 保证线程安全和原子性
     * - 避免重复加载相同字典
     * 
     * ## 性能优化
     * - 第一次加载：从文件读取并缓存
     * - 后续加载：直接从缓存返回
     * - 多线程安全，无需额外同步
     * 
     * ## 错误处理
     * - 文件不存在：返回空列表
     * - 读取失败：返回空列表
     * - 不会抛出异常
     * 
     * @param file 字典文件
     * @return 字符列表，每个元素是一个字符
     */
    fun loadDict(file: File): List<String> {
        val path = file.absolutePath
        synchronized(cachedDictMap) {
            val cached = cachedDictMap.get(path)
            if (cached != null) return cached
            val dict = if (!file.exists()) {
                emptyList()
            } else {
                file.inputStream().use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8)
                        .readLines()
                        .filter { it.isNotBlank() }
                }
            }
            cachedDictMap.put(path, dict)
            return dict
        }
    }
    
    /**
     * 运行识别器（核心推理方法）
     * 
     * ## 功能说明
     * 使用 PaddleLite 进行 OCR 文字识别推理
     * 
     * ## 执行流程
     * 1. 创建预测器（Predictor）
     * 2. 设置输入张量（Tensor）
     *    - 形状：[1, 3, height, width]
     *    - 数据：Float32 数组
     * 3. 运行推理（run）
     * 4. 获取输出张量
     *    - 形状：[batch_size, seq_len, dict_size]
     *    - 数据：Float32 数组
     * 5. 解码输出为文本
     * 6. 释放预测器资源
     * 
     * ## 反射调用
     * 由于 PaddleLite 可能不存在，使用反射动态调用：
     * - getInput(0): 获取输入张量
     * - resize(shape): 设置张量形状
     * - setData(data): 设置张量数据
     * - run(): 运行推理
     * - getOutput(0): 获取输出张量
     * - getShape(): 获取输出形状
     * - getFloatData(): 获取输出数据
     * 
     * ## 资源管理
     * - 使用 try-finally 确保资源释放
     * - 调用 release() 或 destroy() 释放预测器
     * - 使用 runCatching 捕获释放异常
     * 
     * ## 错误处理
     * - 预测器创建失败：返回 null
     * - 推理失败：返回 null
     * - 解码失败：返回 null
     * - 不会抛出异常
     * 
     * @param modelFile 模型文件
     * @param inputData 输入数据（Float32 数组）
     * @param inputHeight 输入高度
     * @param inputWidth 输入宽度
     * @param dict 字符字典
     * @return OCR 识别结果，失败返回 null
     * 
     * @see OcrTextResult OCR 文本结果
     */
    fun runRecognizer(
        modelFile: File,
        inputData: FloatArray,
        inputHeight: Int,
        inputWidth: Int,
        dict: List<String>
    ): OcrTextResult? {
        val predictor = createPredictor(modelFile) ?: return null
        val predictorClass = predictor.javaClass
        
        return try {
            val input = predictorClass.getMethod("getInput", Int::class.javaPrimitiveType).invoke(predictor, 0)
            val inputClass = input.javaClass
            inputClass.getMethod("resize", LongArray::class.java)
                .invoke(input, longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong()))
            inputClass.getMethod("setData", FloatArray::class.java).invoke(input, inputData)
            predictorClass.getMethod("run").invoke(predictor)
            
            val output = predictorClass.getMethod("getOutput", Int::class.javaPrimitiveType).invoke(predictor, 0)
            val outputClass = output.javaClass
            val shape = outputClass.methods.firstOrNull { it.name == "getShape" && it.parameterCount == 0 }
                ?.invoke(output) as? LongArray
            val dataMethod = outputClass.methods.firstOrNull { it.name == "getFloatData" && it.parameterCount == 0 }
                ?: outputClass.methods.firstOrNull { it.name == "getData" && it.parameterCount == 0 }
            val data = (dataMethod?.invoke(output) as? FloatArray) ?: return null
            
            decodeOutput(data, shape, dict)
        } finally {
            val release = predictorClass.methods.firstOrNull { it.name == "release" && it.parameterCount == 0 }
                ?: predictorClass.methods.firstOrNull { it.name == "destroy" && it.parameterCount == 0 }
            runCatching { release?.invoke(predictor) }
        }
    }
    
    /**
     * 创建预测器
     * 
     * ## 功能说明
     * 创建并配置 PaddleLite 预测器实例
     * 
     * ## 配置参数
     * - **模型文件**: 从参数传入
     * - **功耗模式**: LITE_POWER_HIGH（高性能模式）
     * - **线程数**: 2（平衡性能和资源占用）
     * 
     * ## 安全检查
     * 1. **路径安全**: 验证模型文件路径在合法目录内
     * 2. **类源验证**: 验证 PaddleLite 类来自可信源
     * 
     * ### 路径安全检查
     * - 使用 canonicalPath 规范化路径
     * - 检查是否在 filesDir 目录内
     * - 防止路径遍历攻击
     * 
     * ### 类源验证
     * - 检查 codeSource 是否包含 "paddle"
     * - 防止恶意类注入
     * 
     * ## 反射调用
     * 使用反射动态创建预测器：
     * 1. 加载 MobileConfig 类
     * 2. 创建配置对象
     * 3. 设置模型文件路径
     * 4. 设置功耗模式
     * 5. 设置线程数
     * 6. 创建预测器
     * 
     * ## 错误处理
     * - 路径不安全：抛出 SecurityException
     * - 类不可信：抛出 SecurityException
     * - 创建失败：返回 null
     * 
     * @param modelFile 模型文件
     * @return 预测器实例，失败返回 null
     * 
     * @throws SecurityException 当路径不安全或类不可信时
     */
    private fun createPredictor(modelFile: File): Any? {
        return runCatching {
            // 验证模型文件路径安全性
            if (!modelFile.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                throw SecurityException("非法的模型文件路径")
            }
            
            val configClass = Class.forName("com.baidu.paddle.lite.MobileConfig")
            val predictorClass = Class.forName("com.baidu.paddle.lite.PaddlePredictor")
            val powerModeClass = Class.forName("com.baidu.paddle.lite.PowerMode")
            
            // 验证类是否来自可信源
            if (!isClassTrusted(configClass)) {
                throw SecurityException("不可信的类源")
            }
            
            val config = configClass.getDeclaredConstructor().newInstance()
            val setModel = configClass.getMethod("setModelFromFile", String::class.java)
            setModel.invoke(config, modelFile.absolutePath)
            val setPowerMode = configClass.getMethod("setPowerMode", powerModeClass)
            val high = powerModeClass.getField("LITE_POWER_HIGH").get(null)
            setPowerMode.invoke(config, high)
            val setThreads = configClass.getMethod("setThreads", Int::class.javaPrimitiveType)
            setThreads.invoke(config, 2)
            val create = predictorClass.getMethod("createPaddlePredictor", Class.forName("com.baidu.paddle.lite.ConfigBase"))
            create.invoke(null, config) as Any
        }.getOrNull()
    }
    
    /**
     * 验证类是否可信
     * 
     * ## 功能说明
     * 检查类的代码源（codeSource）是否来自可信位置
     * 
     * ## 验证逻辑
     * - 获取类的 protectionDomain
     * - 获取 codeSource 的 location
     * - 检查 location 是否包含 "paddle"（忽略大小写）
     * 
     * ## 安全目的
     * - 防止恶意类注入
     * - 确保使用官方 PaddleLite 库
     * - 避免代码注入攻击
     * 
     * ## 使用场景
     * - 创建预测器前验证
     * - 加载 PaddleLite 类时验证
     * 
     * @param clazz 要验证的类
     * @return true 如果类可信
     */
    private fun isClassTrusted(clazz: Class<*>): Boolean {
        val codeSource = clazz.protectionDomain?.codeSource?.location?.toString()
        return codeSource?.contains("paddle", ignoreCase = true) == true
    }
    
    /**
     * 解码输出（CTC 解码算法）
     * 
     * ## 功能说明
     * 将模型输出的概率分布转换为文本和置信度
     * 
     * ## CTC 解码算法
     * CTC (Connectionist Temporal Classification) 是序列标注算法
     * 
     * ### 解码规则
     * 1. 在每个时间步选择概率最大的字符
     * 2. 跳过空白字符（索引 0）
     * 3. 合并连续重复的字符
     * 4. 计算平均置信度
     * 
     * ### 示例
     * ```
     * 输入序列: [0, 1, 1, 0, 2, 2, 0, 3]
     * 字典: ["", "你", "好", "啊"]
     * 
     * 解码过程:
     * - 0: 空白，跳过
     * - 1: "你"，输出
     * - 1: "你"，重复，跳过
     * - 0: 空白，跳过
     * - 2: "好"，输出
     * - 2: "好"，重复，跳过
     * - 0: 空白，跳过
     * - 3: "啊"，输出
     * 
     * 输出: "你好啊"
     * ```
     * 
     * ## 输入格式
     * - data: Float32 数组，形状 [seq_len * dict_size]
     * - shape: 输出形状 [batch_size, seq_len, dict_size]
     * - dict: 字符字典（索引 0 是空白字符）
     * 
     * ## 输出格式
     * - text: 识别的文本
     * - confidence: 平均置信度 [0, 1]
     * 
     * ## 置信度计算
     * - 对每个非空白字符，记录其概率
     * - 计算所有概率的平均值
     * - 作为整体置信度
     * 
     * @param data 模型输出数据
     * @param shape 输出形状
     * @param dict 字符字典
     * @return OCR 文本结果，失败返回 null
     * 
     * @see OcrTextResult OCR 文本结果
     */
    private fun decodeOutput(data: FloatArray, shape: LongArray?, dict: List<String>): OcrTextResult? {
        val dictWithBlank = listOf("") + dict
        val dictSize = dictWithBlank.size
        val seqLen = when {
            shape != null && shape.size >= 3 -> shape[1].toInt()
            data.size >= dictSize && data.size % dictSize == 0 -> data.size / dictSize
            else -> 0
        }
        if (seqLen <= 0) return null
        
        val builder = StringBuilder()
        var prevIndex = -1
        var confidenceSum = 0f
        var confidenceCount = 0
        
        for (t in 0 until seqLen) {
            val offset = t * dictSize
            var maxIndex = 0
            var maxValue = -Float.MAX_VALUE
            
            for (i in 0 until dictSize) {
                val value = data[offset + i]
                if (value > maxValue) {
                    maxValue = value
                    maxIndex = i
                }
            }
            
            if (maxIndex != 0 && maxIndex != prevIndex) {
                builder.append(dictWithBlank[maxIndex])
                confidenceSum += maxValue
                confidenceCount++
            }
            prevIndex = maxIndex
        }
        
        val text = builder.toString()
        val confidence = if (confidenceCount == 0) 0f else confidenceSum / confidenceCount
        return OcrTextResult(text, confidence)
    }
    
    /**
     * 复制 asset 到缓存目录
     * 
     * ## 功能说明
     * 从 assets 目录复制文件到应用缓存目录
     * 
     * ## 安全检查
     * 1. **路径遍历防护**: 检查路径中是否包含 ".." 或以 "/" 开头
     * 2. **目标路径验证**: 验证目标文件在合法目录内
     * 
     * ## 缓存策略
     * - 如果目标文件已存在且大小 > 0，直接返回
     * - 否则从 assets 复制到缓存目录
     * - 缓存目录：{filesDir}/paddle/
     * 
     * ## 执行流程
     * 1. 验证 asset 路径安全性
     * 2. 创建缓存目录（如果不存在）
     * 3. 提取文件名
     * 4. 构建目标文件路径
     * 5. 验证目标路径在合法目录内
     * 6. 检查文件是否已存在
     * 7. 复制文件
     * 
     * ## 错误处理
     * - 路径不安全：返回 null
     * - 文件不存在：返回 null
     * - 复制失败：返回 null
     * 
     * @param assetPath assets 中的文件路径
     * @return 缓存文件，失败返回 null
     */
    private fun copyAssetToCache(assetPath: String): File? {
        return runCatching {
            // 防止路径遍历攻击
            if (assetPath.contains("..") || assetPath.startsWith("/")) {
                return@runCatching null
            }
            
            val targetDir = File(context.filesDir, "paddle")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val name = assetPath.substringAfterLast("/")
            val target = File(targetDir, name)
            
            // 验证目标路径在合法目录内
            if (!target.canonicalPath.startsWith(targetDir.canonicalPath)) {
                return@runCatching null
            }
            
            if (target.exists() && target.length() > 0L) {
                return@runCatching target
            }
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target
        }.getOrNull()
    }
    
    /**
     * 检查所有asset是否存在
     */
    private fun hasAllAssets(spec: PaddleLiteModelSpec): Boolean {
        return assetExists(spec.detModel) &&
            assetExists(spec.recModel) &&
            assetExists(spec.clsModel) &&
            assetExists(spec.dict)
    }
    
    /**
     * 检查asset是否存在
     */
    private fun assetExists(assetPath: String): Boolean {
        return runCatching {
            context.assets.open(assetPath).use { }
            true
        }.getOrDefault(false)
    }
}

/**
 * PaddleLite 模型规格
 * 
 * 定义一组 OCR 模型文件的路径
 * 
 * ## 模型组成
 * - **detModel**: 文本检测模型（检测文本区域）
 * - **recModel**: 文本识别模型（识别文本内容）
 * - **clsModel**: 文本方向分类模型（判断文本方向）
 * - **dict**: 字符字典（字符集）
 * 
 * ## 使用场景
 * - 定义标准模型规格
 * - 定义 VL 模型规格
 * - 支持多种模型配置
 * 
 * @param detModel 检测模型路径（相对于 assets）
 * @param recModel 识别模型路径（相对于 assets）
 * @param clsModel 分类模型路径（相对于 assets）
 * @param dict 字典文件路径（相对于 assets）
 */
data class PaddleLiteModelSpec(
    val detModel: String,
    val recModel: String,
    val clsModel: String,
    val dict: String
)

/**
 * PaddleLite 模型文件集合
 * 
 * 包含已复制到缓存目录的模型文件
 * 
 * ## 文件位置
 * 所有文件都在 {filesDir}/paddle/ 目录下
 * 
 * @param detModel 检测模型文件
 * @param recModel 识别模型文件
 * @param clsModel 分类模型文件
 * @param dict 字典文件
 */
data class PaddleLiteModelFiles(
    val detModel: File,
    val recModel: File,
    val clsModel: File,
    val dict: File
)

/**
 * OCR 文本识别结果
 * 
 * 包含识别的文本和置信度
 * 
 * ## 置信度说明
 * - 范围：[0, 1]
 * - 0: 完全不确定
 * - 1: 完全确定
 * - 通常 > 0.8 表示高置信度
 * 
 * @param text 识别的文本
 * @param confidence 置信度（0-1）
 */
data class OcrTextResult(
    val text: String,
    val confidence: Float
)

data class OcrBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float
)

interface OcrInferenceBackend {
    fun isAvailable(): Boolean
    fun infer(scene: OcrScene, bitmap: Bitmap): OcrTextResult?
    fun detect(scene: OcrScene, bitmap: Bitmap): List<OcrBox>? = null
}

interface OcrLayoutBackend {
    fun classify(bitmap: Bitmap): OcrLayoutType?
}

interface OcrOrientationBackend {
    fun classifyAngle(bitmap: Bitmap): Int?
}

interface OcrRectifyBackend {
    fun detectQuad(bitmap: Bitmap): List<PointF>?
}

interface OcrDetectionBackend {
    fun detect(bitmap: Bitmap): List<OcrBox>?
}

interface OcrTableStructureBackend {
    fun detectCells(bitmap: Bitmap): List<OcrBox>?
}

class PaddleLiteBackend(
    private val engine: PaddleLiteEngine,
    private val bitmapUtils: OcrBitmapUtils,
    private val modelSpec: PaddleLiteModelSpec,
    private val vlModelSpec: PaddleLiteModelSpec
) : OcrInferenceBackend {
    override fun isAvailable(): Boolean {
        return engine.isPaddleLiteAvailable()
    }

    override fun infer(scene: OcrScene, bitmap: Bitmap): OcrTextResult? {
        val spec = engine.selectModelSpec(scene, modelSpec, vlModelSpec)
        val modelFiles = engine.prepareModelFiles(spec) ?: return null
        val dict = engine.loadDict(modelFiles.dict)
        if (dict.isEmpty()) {
            return null
        }

        val inputData = bitmapUtils.preprocessBitmap(
            bitmap,
            PaddleLiteEngine.OCR_INPUT_HEIGHT,
            PaddleLiteEngine.OCR_INPUT_WIDTH
        )
        return engine.runRecognizer(
            modelFiles.recModel,
            inputData,
            PaddleLiteEngine.OCR_INPUT_HEIGHT,
            PaddleLiteEngine.OCR_INPUT_WIDTH,
            dict
        )
    }
}

data class OnnxRuntimeModelSpec(
    val recModel: String,
    val dict: String,
    val inputHeight: Int = Constants.Ocr.INPUT_HEIGHT,
    val inputWidth: Int = Constants.Ocr.INPUT_WIDTH,
    val detModel: String? = null,
    val detInputSize: Int = Constants.Ocr.DET_INPUT_SIZE
)

private object OnnxSessionCache {
    private val sessions = ConcurrentHashMap<String, Any>()

    fun get(key: String): Any? {
        return sessions[key]
    }

    fun putIfAbsent(key: String, value: Any): Any {
        val existing = sessions.putIfAbsent(key, value)
        return existing ?: value
    }

    fun clear() {
        val snapshot = sessions.values.toList()
        sessions.clear()
        snapshot.forEach { closeOrtIfPossible(it) }
    }
}

internal fun clearOnnxSessionCache() {
    OnnxSessionCache.clear()
}

class OnnxRuntimeBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val modelSpec: OnnxRuntimeModelSpec
) : OcrInferenceBackend {
    private val dictCache = LruCache<String, List<String>>(2)
    private val envClass = runCatching { Class.forName("ai.onnxruntime.OrtEnvironment") }.getOrNull()
    private val tensorClass = runCatching { Class.forName("ai.onnxruntime.OnnxTensor") }.getOrNull()
    private val sessionOptionsClass = runCatching { Class.forName("ai.onnxruntime.SessionOptions") }.getOrNull()
    private val env by lazy {
        runCatching { envClass?.getMethod("getEnvironment")?.invoke(null) }.getOrNull()
    }
    private val sessionOptions by lazy {
        runCatching { sessionOptionsClass?.getConstructor()?.newInstance() }.getOrNull()
    }
    private val createSession by lazy {
        runCatching {
            if (envClass == null || sessionOptionsClass == null) null
            else envClass.getMethod("createSession", String::class.java, sessionOptionsClass)
        }.getOrNull()
    }
    override fun isAvailable(): Boolean {
        return envClass != null && tensorClass != null
    }

    override fun infer(scene: OcrScene, bitmap: Bitmap): OcrTextResult? {
        if (!isAvailable()) return null
        val modelFile = prepareModelFile(modelSpec.recModel) ?: return null
        val dict = loadDict(modelSpec.dict)
        if (dict.isEmpty()) return null

        val inputData = bitmapUtils.preprocessBitmap(
            bitmap,
            modelSpec.inputHeight,
            modelSpec.inputWidth
        )

        return runCatching {
            val env = env ?: return@runCatching null
            val session = getSession(modelFile) ?: return@runCatching null
            val inputNames = session.javaClass.getMethod("getInputNames").invoke(session) as? Set<*>
            val inputName = inputNames?.firstOrNull() as? String ?: return@runCatching null
            val tensorClass = tensorClass ?: return@runCatching null
            val shape = longArrayOf(
                1,
                3,
                modelSpec.inputHeight.toLong(),
                modelSpec.inputWidth.toLong()
            )
            val createTensor = tensorClass.getMethod(
                "createTensor",
                envClass,
                FloatArray::class.java,
                LongArray::class.java
            )
            val inputTensor = createTensor.invoke(null, env, inputData, shape)
            val inputs = Collections.singletonMap(inputName, inputTensor)
            val results = session.javaClass.getMethod("run", Map::class.java).invoke(session, inputs)
            val output = extractOutput(results)
            if (output == null) {
                closeIfPossible(results)
                closeIfPossible(inputTensor)
                return@runCatching null
            }

            val textResult = decodeCtc(
                output.data,
                output.shape,
                dict
            )
            closeIfPossible(results)
            closeIfPossible(inputTensor)
            textResult
        }.getOrNull()
    }

    override fun detect(scene: OcrScene, bitmap: Bitmap): List<OcrBox>? {
        val detModel = modelSpec.detModel ?: return null
        if (!isAvailable()) return null
        val modelFile = prepareModelFile(detModel) ?: return null
        val detInput = bitmapUtils.preprocessDetBitmap(bitmap, modelSpec.detInputSize)
        return runCatching {
            val env = env ?: return@runCatching null
            val session = getSession(modelFile) ?: return@runCatching null
            val inputNames = session.javaClass.getMethod("getInputNames").invoke(session) as? Set<*>
            val inputName = inputNames?.firstOrNull() as? String ?: return@runCatching null
            val tensorClass = tensorClass ?: return@runCatching null
            val shape = longArrayOf(
                1,
                3,
                detInput.resizeHeight.toLong(),
                detInput.resizeWidth.toLong()
            )
            val createTensor = tensorClass.getMethod(
                "createTensor",
                envClass,
                FloatArray::class.java,
                LongArray::class.java
            )
            val inputTensor = createTensor.invoke(null, env, detInput.data, shape)
            val inputs = Collections.singletonMap(inputName, inputTensor)
            val results = session.javaClass.getMethod("run", Map::class.java).invoke(session, inputs)
            val output = extractOutput(results)
            val boxes = if (output == null) {
                null
            } else {
                decodeDetOutput(
                    output,
                    detInput.resizeWidth,
                    detInput.resizeHeight,
                    detInput.scaleX,
                    detInput.scaleY,
                    bitmap.width,
                    bitmap.height
                )
            }
            closeIfPossible(results)
            closeIfPossible(inputTensor)
            boxes
        }.getOrNull()
    }

    private fun getSession(modelFile: File): Any? {
        val env = env ?: return null
        val options = sessionOptions ?: return null
        val create = createSession ?: return null
        val cached = OnnxSessionCache.get(modelFile.absolutePath)
        if (cached != null) return cached
        val created = runCatching { create.invoke(env, modelFile.absolutePath, options) }.getOrNull() ?: return null
        return OnnxSessionCache.putIfAbsent(modelFile.absolutePath, created)
    }

    private fun prepareModelFile(assetPath: String): File? {
        return runCatching {
            if (assetPath.contains("..") || assetPath.startsWith("/")) {
                return@runCatching null
            }
            val targetDir = File(context.filesDir, "onnx")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val name = assetPath.substringAfterLast("/")
            val target = File(targetDir, name)
            if (!target.canonicalPath.startsWith(targetDir.canonicalPath)) {
                return@runCatching null
            }
            if (target.exists() && target.length() > 0L) {
                return@runCatching target
            }
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target
        }.getOrNull()
    }

    private fun loadDict(dictPath: String): List<String> {
        val cached = dictCache.get(dictPath)
        if (cached != null) return cached
        val dict = runCatching {
            context.assets.open(dictPath).use { input ->
                InputStreamReader(input).readLines()
            }
        }.getOrDefault(emptyList())
        dictCache.put(dictPath, dict)
        return dict
    }

    private data class OutputData(
        val data: FloatArray,
        val shape: IntArray
    )

    private fun extractOutput(results: Any?): OutputData? {
        if (results == null) return null
        val getMethod = results.javaClass.methods.firstOrNull { it.name == "get" && it.parameterTypes.size == 1 }
            ?: return null
        val first = getMethod.invoke(results, 0) ?: return null
        val valueMethod = first.javaClass.methods.firstOrNull { it.name == "getValue" } ?: return null
        val value = valueMethod.invoke(first) ?: return null
        val shape = extractShape(first, value) ?: return null
        val flat = when (value) {
            is FloatArray -> value
            is Array<*> -> flattenArray(value, shape)
            else -> null
        } ?: return null
        return OutputData(flat, shape)
    }

    private fun extractShape(resultValue: Any, value: Any): IntArray? {
        val infoMethod = resultValue.javaClass.methods.firstOrNull { it.name == "getInfo" }
        val shape = if (infoMethod != null) {
            val info = infoMethod.invoke(resultValue)
            val shapeMethod = info?.javaClass?.methods?.firstOrNull { it.name == "getShape" }
            (shapeMethod?.invoke(info) as? LongArray)?.map { it.toInt() }?.toIntArray()
        } else {
            null
        }
        if (shape != null) return shape
        return shapeFromValue(value)
    }

    private fun shapeFromValue(value: Any): IntArray? {
        if (value !is Array<*>) return null
        val dim0 = value.size
        val first = value.firstOrNull() ?: return intArrayOf(dim0, 0, 0)
        if (first !is Array<*>) return null
        val dim1 = first.size
        val first2 = first.firstOrNull() ?: return intArrayOf(dim0, dim1, 0)
        return when (first2) {
            is FloatArray -> intArrayOf(dim0, dim1, first2.size)
            is Array<*> -> intArrayOf(dim0, dim1, first2.size)
            else -> null
        }
    }

    private fun flattenArray(value: Array<*>, shape: IntArray): FloatArray? {
        if (shape.size < 3) return null
        val dim0 = shape[0]
        val dim1 = shape[1]
        val dim2 = shape[2]
        val out = FloatArray(dim0 * dim1 * dim2)
        var index = 0
        for (i in 0 until dim0) {
            val arr1 = value[i] as? Array<*> ?: return null
            for (j in 0 until dim1) {
                val arr2 = arr1[j]
                val slice = when (arr2) {
                    is FloatArray -> arr2
                    is Array<*> -> arr2.map { (it as? Number)?.toFloat() ?: 0f }.toFloatArray()
                    else -> return null
                }
                if (slice.size != dim2) return null
                slice.copyInto(out, index)
                index += dim2
            }
        }
        return out
    }

    private fun decodeCtc(data: FloatArray, shape: IntArray, dict: List<String>): OcrTextResult? {
        if (shape.size < 3) return null
        val timeSteps = shape[1]
        val numClasses = shape[2]
        val blankIndex = numClasses - 1
        val builder = StringBuilder()
        var prevIndex = -1
        var sum = 0f
        var count = 0
        for (t in 0 until timeSteps) {
            val base = t * numClasses
            var maxIndex = 0
            var maxValue = Float.NEGATIVE_INFINITY
            for (c in 0 until numClasses) {
                val v = data[base + c]
                if (v > maxValue) {
                    maxValue = v
                    maxIndex = c
                }
            }
            if (maxIndex != blankIndex && maxIndex != prevIndex) {
                if (maxIndex < dict.size) {
                    builder.append(dict[maxIndex])
                }
                sum += maxValue
                count++
            }
            prevIndex = maxIndex
        }
        val confidence = if (count > 0) sum / count else 0f
        return OcrTextResult(builder.toString(), confidence)
    }

    private fun decodeDetOutput(
        output: OutputData,
        resizeWidth: Int,
        resizeHeight: Int,
        scaleX: Float,
        scaleY: Float,
        originalWidth: Int,
        originalHeight: Int
    ): List<OcrBox> {
        val map = extractProbabilityMap(output) ?: return emptyList()
        val prob = map.data
        val mapWidth = map.width
        val mapHeight = map.height
        if (mapWidth == 0 || mapHeight == 0) return emptyList()

        val threshold = 0.3f
        val minArea = 24
        val visited = BooleanArray(mapWidth * mapHeight)
        val boxes = mutableListOf<OcrBox>()
        val scaleToInputX = resizeWidth.toFloat() / mapWidth.toFloat()
        val scaleToInputY = resizeHeight.toFloat() / mapHeight.toFloat()

        val stackX = IntArray(mapWidth * mapHeight)
        val stackY = IntArray(mapWidth * mapHeight)
        var stackSize: Int

        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val idx = y * mapWidth + x
                if (visited[idx] || prob[idx] < threshold) continue
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0
                stackSize = 0
                stackX[stackSize] = x
                stackY[stackSize] = y
                stackSize++
                visited[idx] = true
                while (stackSize > 0) {
                    stackSize--
                    val cx = stackX[stackSize]
                    val cy = stackY[stackSize]
                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx < 0 || ny < 0 || nx >= mapWidth || ny >= mapHeight) continue
                            val nIdx = ny * mapWidth + nx
                            if (visited[nIdx] || prob[nIdx] < threshold) continue
                            visited[nIdx] = true
                            stackX[stackSize] = nx
                            stackY[stackSize] = ny
                            stackSize++
                        }
                    }
                }
                if (area < minArea) continue
                val left = (minX * scaleToInputX * scaleX).coerceIn(0f, originalWidth.toFloat())
                val top = (minY * scaleToInputY * scaleY).coerceIn(0f, originalHeight.toFloat())
                val right = ((maxX + 1) * scaleToInputX * scaleX).coerceIn(0f, originalWidth.toFloat())
                val bottom = ((maxY + 1) * scaleToInputY * scaleY).coerceIn(0f, originalHeight.toFloat())
                val score = prob[idx]
                if (right > left && bottom > top) {
                    boxes.add(OcrBox(left, top, right, bottom, score))
                }
            }
        }
        return boxes.sortedByDescending { it.score }
    }

    private data class ProbabilityMap(
        val data: FloatArray,
        val width: Int,
        val height: Int
    )

    private fun extractProbabilityMap(output: OutputData): ProbabilityMap? {
        val shape = output.shape
        val data = output.data
        if (shape.isEmpty()) return null
        return when (shape.size) {
            4 -> {
                if (shape[1] == 1) {
                    val height = shape[2]
                    val width = shape[3]
                    val map = FloatArray(width * height)
                    var i = 0
                    for (y in 0 until height) {
                        val row = y * width
                        for (x in 0 until width) {
                            map[row + x] = data[i + row + x]
                        }
                    }
                    ProbabilityMap(map, width, height)
                } else if (shape[3] == 1) {
                    val height = shape[1]
                    val width = shape[2]
                    val map = FloatArray(width * height)
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val index = ((y * width) + x) * shape[3]
                            map[y * width + x] = data[index]
                        }
                    }
                    ProbabilityMap(map, width, height)
                } else {
                    null
                }
            }
            3 -> {
                val height = shape[1]
                val width = shape[2]
                val map = FloatArray(width * height)
                for (y in 0 until height) {
                    val row = y * width
                    for (x in 0 until width) {
                        map[row + x] = data[row + x]
                    }
                }
                ProbabilityMap(map, width, height)
            }
            else -> null
        }
    }

    private fun closeIfPossible(target: Any?) {
        closeOrtIfPossible(target)
    }
}

data class OnnxLayoutModelSpec(
    val model: String,
    val inputSize: Int = Constants.Ocr.LAYOUT_INPUT_SIZE,
    val tableClassIds: IntArray = intArrayOf(3)
)

data class OnnxOrientationModelSpec(
    val model: String,
    val inputSize: Int = Constants.Ocr.ORI_INPUT_SIZE,
    val angles: IntArray = intArrayOf(0, 90, 180, 270)
)

data class OnnxRectifyModelSpec(
    val model: String,
    val inputSize: Int = 512
)

data class OnnxDetModelSpec(
    val model: String,
    val inputSize: Int = Constants.Ocr.DET_INPUT_SIZE
)

data class OnnxTableStructureModelSpec(
    val model: String,
    val inputSize: Int = Constants.Ocr.DET_INPUT_SIZE
)

class OnnxRuntimeLayoutBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val spec: OnnxLayoutModelSpec
) : OcrLayoutBackend {
    private val helper = OnnxRuntimeHelper(context)

    override fun classify(bitmap: Bitmap): OcrLayoutType? {
        if (!helper.isAvailable()) return null
        val modelFile = helper.prepareModelFile(spec.model) ?: return null
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = bitmapUtils.preprocessBitmapWithMeanStd(bitmap, spec.inputSize, spec.inputSize, mean, std)
        val output = helper.runSession(modelFile, input, longArrayOf(1, 3, spec.inputSize.toLong(), spec.inputSize.toLong()))
            ?: return null
        val scores = output.data
        if (scores.isEmpty()) return null
        var maxIndex = 0
        var maxValue = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > maxValue) {
                maxValue = scores[i]
                maxIndex = i
            }
        }
        return if (spec.tableClassIds.contains(maxIndex)) OcrLayoutType.Table else OcrLayoutType.TextLabel
    }
}

class OnnxRuntimeTableLayoutBackend(
    private val detBackend: OcrDetectionBackend
) : OcrLayoutBackend {
    override fun classify(bitmap: Bitmap): OcrLayoutType? {
        val boxes = detBackend.detect(bitmap)
        return if (boxes.isNullOrEmpty()) OcrLayoutType.TextLabel else OcrLayoutType.Table
    }
}

class OnnxRuntimeOrientationBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val spec: OnnxOrientationModelSpec
) : OcrOrientationBackend {
    private val helper = OnnxRuntimeHelper(context)

    override fun classifyAngle(bitmap: Bitmap): Int? {
        if (!helper.isAvailable()) return null
        val modelFile = helper.prepareModelFile(spec.model) ?: return null
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = bitmapUtils.preprocessBitmapWithMeanStd(bitmap, spec.inputSize, spec.inputSize, mean, std)
        val output = helper.runSession(modelFile, input, longArrayOf(1, 3, spec.inputSize.toLong(), spec.inputSize.toLong()))
            ?: return null
        val scores = output.data
        if (scores.isEmpty()) return null
        var maxIndex = 0
        var maxValue = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > maxValue) {
                maxValue = scores[i]
                maxIndex = i
            }
        }
        return spec.angles.getOrNull(maxIndex)
    }
}

class OnnxRuntimeRectifyBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val spec: OnnxRectifyModelSpec
) : OcrRectifyBackend {
    private val helper = OnnxRuntimeHelper(context)

    override fun detectQuad(bitmap: Bitmap): List<PointF>? {
        if (!helper.isAvailable()) return null
        val modelFile = helper.prepareModelFile(spec.model) ?: return null
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = bitmapUtils.preprocessBitmapWithMeanStd(bitmap, spec.inputSize, spec.inputSize, mean, std)
        val output = helper.runSession(modelFile, input, longArrayOf(1, 3, spec.inputSize.toLong(), spec.inputSize.toLong()))
            ?: return null
        val data = output.data
        if (data.size < 8) return null
        val maxValue = data.take(8).maxOrNull() ?: return null
        val normalized = maxValue <= 1.5f
        val scaleX = if (normalized) bitmap.width.toFloat() else bitmap.width.toFloat() / spec.inputSize.toFloat()
        val scaleY = if (normalized) bitmap.height.toFloat() else bitmap.height.toFloat() / spec.inputSize.toFloat()
        return listOf(
            PointF(data[0] * scaleX, data[1] * scaleY),
            PointF(data[2] * scaleX, data[3] * scaleY),
            PointF(data[4] * scaleX, data[5] * scaleY),
            PointF(data[6] * scaleX, data[7] * scaleY)
        )
    }
}

class OnnxRuntimeDetBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val spec: OnnxDetModelSpec
) : OcrDetectionBackend {
    private val helper = OnnxRuntimeHelper(context)

    override fun detect(bitmap: Bitmap): List<OcrBox>? {
        if (!helper.isAvailable()) return null
        val modelFile = helper.prepareModelFile(spec.model) ?: return null
        val detInput = bitmapUtils.preprocessDetBitmap(bitmap, spec.inputSize)
        val output = helper.runSession(
            modelFile,
            detInput.data,
            longArrayOf(1, 3, detInput.resizeHeight.toLong(), detInput.resizeWidth.toLong())
        ) ?: return null
        return decodeDetOutput(
            output,
            detInput.resizeWidth,
            detInput.resizeHeight,
            detInput.scaleX,
            detInput.scaleY,
            bitmap.width,
            bitmap.height
        )
    }

    private fun decodeDetOutput(
        output: OnnxRuntimeHelper.OutputData,
        resizeWidth: Int,
        resizeHeight: Int,
        scaleX: Float,
        scaleY: Float,
        originalWidth: Int,
        originalHeight: Int
    ): List<OcrBox> {
        val map = extractProbabilityMap(output) ?: return emptyList()
        val prob = map.data
        val mapWidth = map.width
        val mapHeight = map.height
        if (mapWidth == 0 || mapHeight == 0) return emptyList()

        val threshold = 0.3f
        val minArea = 24
        val visited = BooleanArray(mapWidth * mapHeight)
        val boxes = mutableListOf<OcrBox>()
        val scaleToInputX = resizeWidth.toFloat() / mapWidth.toFloat()
        val scaleToInputY = resizeHeight.toFloat() / mapHeight.toFloat()

        val stackX = IntArray(mapWidth * mapHeight)
        val stackY = IntArray(mapWidth * mapHeight)
        var stackSize: Int

        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val idx = y * mapWidth + x
                if (visited[idx] || prob[idx] < threshold) continue
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0
                stackSize = 0
                stackX[stackSize] = x
                stackY[stackSize] = y
                stackSize++
                visited[idx] = true
                while (stackSize > 0) {
                    stackSize--
                    val cx = stackX[stackSize]
                    val cy = stackY[stackSize]
                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = cx + dx
                            val ny = cy + dy
                            if (nx < 0 || ny < 0 || nx >= mapWidth || ny >= mapHeight) continue
                            val nIdx = ny * mapWidth + nx
                            if (visited[nIdx] || prob[nIdx] < threshold) continue
                            visited[nIdx] = true
                            stackX[stackSize] = nx
                            stackY[stackSize] = ny
                            stackSize++
                        }
                    }
                }
                if (area < minArea) continue
                val left = (minX * scaleToInputX * scaleX).coerceIn(0f, originalWidth.toFloat())
                val top = (minY * scaleToInputY * scaleY).coerceIn(0f, originalHeight.toFloat())
                val right = ((maxX + 1) * scaleToInputX * scaleX).coerceIn(0f, originalWidth.toFloat())
                val bottom = ((maxY + 1) * scaleToInputY * scaleY).coerceIn(0f, originalHeight.toFloat())
                val score = prob[idx]
                if (right > left && bottom > top) {
                    boxes.add(OcrBox(left, top, right, bottom, score))
                }
            }
        }
        return boxes.sortedByDescending { it.score }
    }

    private data class ProbabilityMap(
        val data: FloatArray,
        val width: Int,
        val height: Int
    )

    private fun extractProbabilityMap(output: OnnxRuntimeHelper.OutputData): ProbabilityMap? {
        val shape = output.shape
        val data = output.data
        if (shape.isEmpty()) return null
        return when (shape.size) {
            4 -> {
                if (shape[1] == 1) {
                    val height = shape[2]
                    val width = shape[3]
                    val map = FloatArray(width * height)
                    for (y in 0 until height) {
                        val row = y * width
                        for (x in 0 until width) {
                            map[row + x] = data[row + x]
                        }
                    }
                    ProbabilityMap(map, width, height)
                } else if (shape[3] == 1) {
                    val height = shape[1]
                    val width = shape[2]
                    val map = FloatArray(width * height)
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val index = ((y * width) + x) * shape[3]
                            map[y * width + x] = data[index]
                        }
                    }
                    ProbabilityMap(map, width, height)
                } else {
                    null
                }
            }
            3 -> {
                val height = shape[1]
                val width = shape[2]
                val map = FloatArray(width * height)
                for (y in 0 until height) {
                    val row = y * width
                    for (x in 0 until width) {
                        map[row + x] = data[row + x]
                    }
                }
                ProbabilityMap(map, width, height)
            }
            else -> null
        }
    }
}

class OnnxRuntimeTableStructureBackend(
    private val context: Context,
    private val bitmapUtils: OcrBitmapUtils,
    private val spec: OnnxTableStructureModelSpec
) : OcrTableStructureBackend {
    private val helper = OnnxRuntimeHelper(context)

    override fun detectCells(bitmap: Bitmap): List<OcrBox>? {
        if (!helper.isAvailable()) return null
        val modelFile = helper.prepareModelFile(spec.model) ?: return null
        val input = bitmapUtils.preprocessDetBitmap(bitmap, spec.inputSize)
        val output = helper.runSession(
            modelFile,
            input.data,
            longArrayOf(1, 3, input.resizeHeight.toLong(), input.resizeWidth.toLong())
        ) ?: return null
        return decodeBoxes(output, input, bitmap.width, bitmap.height)
    }

    private fun decodeBoxes(
        output: OnnxRuntimeHelper.OutputData,
        input: DetPreprocessResult,
        originalWidth: Int,
        originalHeight: Int
    ): List<OcrBox> {
        val data = output.data
        val shape = output.shape
        if (shape.size < 2) return emptyList()
        val stride = shape.last()
        if (stride < 4) return emptyList()
        val count = data.size / stride
        if (count <= 0) return emptyList()
        val maxValue = data.take(minOf(data.size, 2048)).maxOrNull() ?: 0f
        val normalized = maxValue <= 1.5f
        val boxes = ArrayList<OcrBox>(count)
        for (i in 0 until count) {
            val base = i * stride
            val leftRaw = data[base]
            val topRaw = data[base + 1]
            val rightRaw = data[base + 2]
            val bottomRaw = data[base + 3]
            val score = if (stride > 4) data[base + 4] else 1f
            val left: Float
            val top: Float
            val right: Float
            val bottom: Float
            if (normalized) {
                left = (leftRaw * originalWidth).coerceIn(0f, originalWidth.toFloat())
                top = (topRaw * originalHeight).coerceIn(0f, originalHeight.toFloat())
                right = (rightRaw * originalWidth).coerceIn(0f, originalWidth.toFloat())
                bottom = (bottomRaw * originalHeight).coerceIn(0f, originalHeight.toFloat())
            } else {
                left = (leftRaw * input.scaleX).coerceIn(0f, originalWidth.toFloat())
                top = (topRaw * input.scaleY).coerceIn(0f, originalHeight.toFloat())
                right = (rightRaw * input.scaleX).coerceIn(0f, originalWidth.toFloat())
                bottom = (bottomRaw * input.scaleY).coerceIn(0f, originalHeight.toFloat())
            }
            if (right > left && bottom > top) {
                boxes.add(OcrBox(left, top, right, bottom, score))
            }
        }
        return boxes.sortedWith(compareByDescending<OcrBox> { it.score }.thenBy { it.top }.thenBy { it.left })
    }
}

private class OnnxRuntimeHelper(private val context: Context) {
    data class OutputData(
        val data: FloatArray,
        val shape: IntArray
    )

    private val envClass = runCatching { Class.forName("ai.onnxruntime.OrtEnvironment") }.getOrNull()
    private val tensorClass = runCatching { Class.forName("ai.onnxruntime.OnnxTensor") }.getOrNull()
    private val sessionOptionsClass = runCatching { Class.forName("ai.onnxruntime.SessionOptions") }.getOrNull()
    private val env by lazy {
        runCatching { envClass?.getMethod("getEnvironment")?.invoke(null) }.getOrNull()
    }
    private val sessionOptions by lazy {
        runCatching { sessionOptionsClass?.getConstructor()?.newInstance() }.getOrNull()
    }
    private val createSession by lazy {
        runCatching {
            if (envClass == null || sessionOptionsClass == null) null
            else envClass.getMethod("createSession", String::class.java, sessionOptionsClass)
        }.getOrNull()
    }
    fun isAvailable(): Boolean {
        return envClass != null && tensorClass != null
    }

    fun prepareModelFile(assetPath: String): File? {
        return runCatching {
            if (assetPath.contains("..") || assetPath.startsWith("/")) {
                return@runCatching null
            }
            val targetDir = File(context.filesDir, "onnx")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val name = assetPath.substringAfterLast("/")
            val target = File(targetDir, name)
            if (!target.canonicalPath.startsWith(targetDir.canonicalPath)) {
                return@runCatching null
            }
            if (target.exists() && target.length() > 0L) {
                return@runCatching target
            }
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target
        }.getOrNull()
    }

    fun runSession(modelFile: File, inputData: FloatArray, inputShape: LongArray): OutputData? {
        return runCatching {
            val env = env ?: return@runCatching null
            val session = getSession(modelFile) ?: return@runCatching null
            val inputNames = session.javaClass.getMethod("getInputNames").invoke(session) as? Set<*>
            val inputName = inputNames?.firstOrNull() as? String ?: return@runCatching null
            val tensorClass = tensorClass ?: return@runCatching null
            val createTensor = tensorClass.getMethod(
                "createTensor",
                envClass,
                FloatArray::class.java,
                LongArray::class.java
            )
            val inputTensor = createTensor.invoke(null, env, inputData, inputShape)
            val inputs = Collections.singletonMap(inputName, inputTensor)
            val results = session.javaClass.getMethod("run", Map::class.java).invoke(session, inputs)
            val output = extractOutput(results)
            closeIfPossible(results)
            closeIfPossible(inputTensor)
            output
        }.getOrNull()
    }

    private fun getSession(modelFile: File): Any? {
        val env = env ?: return null
        val options = sessionOptions ?: return null
        val create = createSession ?: return null
        val cached = OnnxSessionCache.get(modelFile.absolutePath)
        if (cached != null) return cached
        val created = runCatching { create.invoke(env, modelFile.absolutePath, options) }.getOrNull() ?: return null
        return OnnxSessionCache.putIfAbsent(modelFile.absolutePath, created)
    }

    private fun extractOutput(results: Any?): OutputData? {
        if (results == null) return null
        val getMethod = results.javaClass.methods.firstOrNull { it.name == "get" && it.parameterTypes.size == 1 }
            ?: return null
        val first = getMethod.invoke(results, 0) ?: return null
        val valueMethod = first.javaClass.methods.firstOrNull { it.name == "getValue" } ?: return null
        val value = valueMethod.invoke(first) ?: return null
        val shape = extractShape(first, value) ?: return null
        val flat = when (value) {
            is FloatArray -> value
            is Array<*> -> flattenArray(value, shape)
            else -> null
        } ?: return null
        return OutputData(flat, shape)
    }

    private fun extractShape(resultValue: Any, value: Any): IntArray? {
        val infoMethod = resultValue.javaClass.methods.firstOrNull { it.name == "getInfo" }
        val shape = if (infoMethod != null) {
            val info = infoMethod.invoke(resultValue)
            val shapeMethod = info?.javaClass?.methods?.firstOrNull { it.name == "getShape" }
            (shapeMethod?.invoke(info) as? LongArray)?.map { it.toInt() }?.toIntArray()
        } else {
            null
        }
        if (shape != null) return shape
        return shapeFromValue(value)
    }

    private fun shapeFromValue(value: Any): IntArray? {
        if (value !is Array<*>) return null
        val dim0 = value.size
        val first = value.firstOrNull() ?: return intArrayOf(dim0, 0, 0)
        if (first !is Array<*>) return null
        val dim1 = first.size
        val first2 = first.firstOrNull() ?: return intArrayOf(dim0, dim1, 0)
        return when (first2) {
            is FloatArray -> intArrayOf(dim0, dim1, first2.size)
            is Array<*> -> intArrayOf(dim0, dim1, first2.size)
            else -> null
        }
    }

    private fun flattenArray(value: Array<*>, shape: IntArray): FloatArray? {
        if (shape.size < 3) return null
        val dim0 = shape[0]
        val dim1 = shape[1]
        val dim2 = shape[2]
        val out = FloatArray(dim0 * dim1 * dim2)
        var index = 0
        for (i in 0 until dim0) {
            val arr1 = value[i] as? Array<*> ?: return null
            for (j in 0 until dim1) {
                val arr2 = arr1[j]
                val slice = when (arr2) {
                    is FloatArray -> arr2
                    is Array<*> -> arr2.map { (it as? Number)?.toFloat() ?: 0f }.toFloatArray()
                    else -> return null
                }
                if (slice.size != dim2) return null
                slice.copyInto(out, index)
                index += dim2
            }
        }
        return out
    }

    private fun closeIfPossible(target: Any?) {
        closeOrtIfPossible(target)
    }
}

private fun closeOrtIfPossible(target: Any?) {
    if (target == null) return
    runCatching {
        target.javaClass.methods.firstOrNull { it.name == "close" && it.parameterTypes.isEmpty() }
            ?.invoke(target)
    }
}
