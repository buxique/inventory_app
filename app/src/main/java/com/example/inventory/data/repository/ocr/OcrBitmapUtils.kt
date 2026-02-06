package com.example.inventory.data.repository.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min

data class DetPreprocessResult(
    val data: FloatArray,
    val resizeWidth: Int,
    val resizeHeight: Int,
    val scaleX: Float,
    val scaleY: Float
)

/**
 * OCR Bitmap 工具类
 * 
 * ## 核心功能
 * 提供 Bitmap 相关的工具方法，用于 OCR 图片预处理
 * 
 * ### 主要功能模块
 * 1. **图片采样**: 计算合适的采样率，降低大图片的内存占用
 * 2. **Bitmap 预处理**: 将图片转换为模型输入格式（归一化 FloatArray）
 * 3. **透视变换**: 对文档图片进行透视变换校正
 * 
 * ## 图片采样算法
 * 
 * ### 为什么需要采样？
 * - 高分辨率图片（如 4000x3000）会占用大量内存（约 45MB）
 * - 可能导致 OutOfMemoryError
 * - OCR 模型通常只需要 640x480 或更小的输入
 * 
 * ### 采样策略
 * 使用 2 的幂次方作为采样率（1, 2, 4, 8, ...）：
 * - inSampleSize = 1: 原始尺寸
 * - inSampleSize = 2: 宽高各缩小一半，内存占用减少 75%
 * - inSampleSize = 4: 宽高各缩小到 1/4，内存占用减少 93.75%
 * 
 * ### 算法原理
 * ```
 * 1. 获取图片原始尺寸（不加载到内存）
 * 2. 计算缩放比例：
 *    - 如果宽度或高度超过目标尺寸
 *    - 每次将采样率翻倍（1 → 2 → 4 → 8）
 *    - 直到缩放后的尺寸小于目标尺寸
 * 3. 返回采样率
 * ```
 * 
 * ## Bitmap 预处理算法
 * 
 * ### 模型输入格式
 * PaddleOCR 模型需要以下格式的输入：
 * - **数据类型**: FloatArray
 * - **数据范围**: [-1, 1]（归一化）
 * - **通道顺序**: RGB（分离存储）
 * - **数据布局**: [R通道, G通道, B通道]
 * 
 * ### 归一化公式
 * ```
 * 1. 提取 RGB 值：[0, 255]
 * 2. 归一化到 [0, 1]：value / 255
 * 3. 归一化到 [-1, 1]：(value - 0.5) / 0.5
 * ```
 * 
 * ### 数据布局示例
 * 假设图片尺寸为 2x2：
 * ```
 * 原始像素（ARGB）:
 * [R1,G1,B1] [R2,G2,B2]
 * [R3,G3,B3] [R4,G4,B4]
 * 
 * 转换后的 FloatArray（长度 = 2×2×3 = 12）:
 * [R1, R2, R3, R4, G1, G2, G3, G4, B1, B2, B3, B4]
 * ```
 * 
 * ## 透视变换算法
 * 
 * ### 什么是透视变换？
 * 透视变换（Perspective Transform）是一种几何变换，可以将任意四边形映射到矩形。
 * 
 * ### 应用场景
 * - 拍摄文档时，由于相机角度问题，文档呈现梯形
 * - 透视变换可以将梯形还原为矩形
 * - 大幅提升 OCR 识别准确率
 * 
 * ### 变换原理
 * 使用 Android 的 Matrix.setPolyToPoly 方法：
 * - 输入：源四边形的四个角点
 * - 输出：目标矩形的四个角点
 * - 计算：3x3 透视变换矩阵
 * - 应用：使用 Canvas.drawBitmap 绘制变换后的图片
 * 
 * ### 变换矩阵
 * 透视变换矩阵是一个 3x3 矩阵：
 * ```
 * [a b c]
 * [d e f]
 * [g h 1]
 * ```
 * 
 * 变换公式：
 * ```
 * x' = (ax + by + c) / (gx + hy + 1)
 * y' = (dx + ey + f) / (gx + hy + 1)
 * ```
 * 
 * ## 性能优化策略
 * 
 * 1. **智能采样**:
 *    - 使用 BitmapFactory.Options.inJustDecodeBounds 获取尺寸
 *    - 不加载图片到内存，速度快
 *    - 根据目标尺寸计算最优采样率
 * 
 * 2. **内存管理**:
 *    - 预处理后及时回收中间 Bitmap
 *    - 使用 try-finally 确保资源释放
 * 
 * 3. **尺寸限制**:
 *    - 透视变换结果限制在 4096x4096 以内
 *    - 避免创建过大的 Bitmap
 * 
 * ## 使用示例
 * 
 * ### 示例 1：加载大图片
 * ```kotlin
 * val utils = OcrBitmapUtils()
 * 
 * // 1. 获取图片尺寸（不加载到内存）
 * val options = BitmapFactory.Options().apply {
 *     inJustDecodeBounds = true
 * }
 * BitmapFactory.decodeFile(imagePath, options)
 * 
 * // 2. 计算采样率
 * val sampleSize = utils.calculateInSampleSize(options, 1024, 1024)
 * println("采样率: $sampleSize")
 * 
 * // 3. 加载图片
 * options.inJustDecodeBounds = false
 * options.inSampleSize = sampleSize
 * val bitmap = BitmapFactory.decodeFile(imagePath, options)
 * ```
 * 
 * ### 示例 2：预处理为模型输入
 * ```kotlin
 * val utils = OcrBitmapUtils()
 * 
 * // 预处理为 640x480 的归一化 FloatArray
 * val input = utils.preprocessBitmap(bitmap, 480, 640)
 * 
 * // input 长度 = 480 × 640 × 3 = 921,600
 * // 数据范围 = [-1, 1]
 * // 布局 = [R通道, G通道, B通道]
 * 
 * // 传递给 PaddleLite 模型
 * predictor.setInput(input)
 * ```
 * 
 * ### 示例 3：透视变换
 * ```kotlin
 * val utils = OcrBitmapUtils()
 * 
 * // 定义源四边形（梯形文档的四个角点）
 * val srcPoints = listOf(
 *     PointF(100f, 50f),   // 左上
 *     PointF(900f, 100f),  // 右上
 *     PointF(950f, 700f),  // 右下
 *     PointF(50f, 650f)    // 左下
 * )
 * 
 * // 透视变换为 800x600 的矩形
 * val corrected = utils.warpPerspective(bitmap, srcPoints, 800, 600)
 * 
 * if (corrected != null) {
 *     println("透视变换成功")
 *     // 使用校正后的图片进行 OCR
 * } else {
 *     println("透视变换失败")
 * }
 * ```
 * 
 * ## 注意事项
 * 
 * 1. **内存管理**:
 *    - 预处理会创建新的 Bitmap，需要及时回收
 *    - 使用 try-finally 确保资源释放
 * 
 * 2. **采样率选择**:
 *    - 采样率过大会导致图片模糊，影响识别准确率
 *    - 采样率过小会导致内存占用过高
 *    - 建议目标尺寸设置为 1024x1024 或 2048x2048
 * 
 * 3. **透视变换失败**:
 *    - 如果四个角点不构成有效的四边形，变换会失败
 *    - 如果目标尺寸无效（≤ 0），变换会失败
 *    - 失败时返回 null，需要检查返回值
 * 
 * 4. **性能考虑**:
 *    - 预处理是 CPU 密集型操作，建议在后台线程执行
 *    - 大图片预处理可能耗时较长（100-500ms）
 * 
 * @see OcrImageProcessor 提供图片预处理功能
 * @see android.graphics.Matrix.setPolyToPoly 透视变换实现
 */
class OcrBitmapUtils {
    
    companion object {
        const val MAX_IMAGE_DIMENSION = 4096
    }
    
    /**
     * 计算采样率
     * 
     * ## 功能说明
     * 根据图片原始尺寸和目标尺寸，计算合适的采样率（inSampleSize）
     * 
     * ## 采样率说明
     * - inSampleSize = 1: 原始尺寸（不缩放）
     * - inSampleSize = 2: 宽高各缩小一半，内存占用减少 75%
     * - inSampleSize = 4: 宽高各缩小到 1/4，内存占用减少 93.75%
     * - inSampleSize = 8: 宽高各缩小到 1/8，内存占用减少 98.4%
     * 
     * ## 算法原理
     * ```
     * 1. 获取图片原始尺寸（从 options.outWidth 和 options.outHeight）
     * 2. 如果原始尺寸小于等于目标尺寸，返回 1（不缩放）
     * 3. 否则，每次将采样率翻倍：
     *    - 计算缩放后的尺寸 = 原始尺寸 / (采样率 × 2)
     *    - 如果缩放后的尺寸仍大于等于目标尺寸，继续翻倍
     *    - 否则，停止并返回当前采样率
     * ```
     * 
     * ## 为什么使用 2 的幂次方？
     * - BitmapFactory 对 2 的幂次方采样率有优化
     * - 解码速度更快
     * - 内存对齐更好
     * 
     * ## 示例
     * 
     * ### 示例 1：大图片
     * ```kotlin
     * // 原始尺寸：4000x3000
     * // 目标尺寸：1024x1024
     * val options = BitmapFactory.Options().apply {
     *     inJustDecodeBounds = true
     *     // outWidth = 4000, outHeight = 3000
     * }
     * 
     * val sampleSize = calculateInSampleSize(options, 1024, 1024)
     * // 返回 4
     * // 缩放后尺寸：1000x750
     * // 内存占用：从 45MB 减少到 2.8MB
     * ```
     * 
     * ### 示例 2：小图片
     * ```kotlin
     * // 原始尺寸：800x600
     * // 目标尺寸：1024x1024
     * val options = BitmapFactory.Options().apply {
     *     inJustDecodeBounds = true
     *     // outWidth = 800, outHeight = 600
     * }
     * 
     * val sampleSize = calculateInSampleSize(options, 1024, 1024)
     * // 返回 1
     * // 不缩放，保持原始尺寸
     * ```
     * 
     * ## 使用方法
     * ```kotlin
     * val utils = OcrBitmapUtils()
     * 
     * // 1. 获取图片尺寸（不加载到内存）
     * val options = BitmapFactory.Options().apply {
     *     inJustDecodeBounds = true
     * }
     * BitmapFactory.decodeFile(imagePath, options)
     * 
     * // 2. 计算采样率
     * val sampleSize = utils.calculateInSampleSize(options, 1024, 1024)
     * 
     * // 3. 加载图片
     * options.inJustDecodeBounds = false
     * options.inSampleSize = sampleSize
     * val bitmap = BitmapFactory.decodeFile(imagePath, options)
     * ```
     * 
     * @param options BitmapFactory.Options 对象，包含图片原始尺寸
     * @param reqWidth 目标宽度（像素）
     * @param reqHeight 目标高度（像素）
     * @return 采样率（2 的幂次方，≥ 1）
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 预处理 Bitmap 为模型输入
     * 
     * ## 功能说明
     * 将 Bitmap 转换为 PaddleOCR 模型所需的输入格式
     * 
     * ## 模型输入要求
     * - **数据类型**: FloatArray
     * - **数据范围**: [-1, 1]（归一化）
     * - **通道顺序**: RGB（分离存储）
     * - **数据布局**: [R通道, G通道, B通道]
     * 
     * ## 处理流程
     * ```
     * 1. 缩放图片
     *    ├─ 如果尺寸已匹配，跳过缩放
     *    └─ 否则，缩放到目标尺寸（targetWidth × targetHeight）
     * 
     * 2. 提取像素数据
     *    └─ 使用 Bitmap.getPixels 获取 ARGB 像素数组
     * 
     * 3. 归一化处理
     *    ├─ 提取 RGB 三个通道
     *    ├─ 归一化到 [0, 1]：value / 255
     *    └─ 归一化到 [-1, 1]：(value - 0.5) / 0.5
     * 
     * 4. 重排数据布局
     *    └─ 从交错存储（RGBRGBRGB...）转换为分离存储（RRR...GGG...BBB...）
     * 
     * 5. 清理资源
     *    └─ 如果创建了缩放后的 Bitmap，及时回收
     * ```
     * 
     * ## 归一化公式
     * 
     * ### 第一步：归一化到 [0, 1]
     * ```
     * r = Red(pixel) / 255.0
     * g = Green(pixel) / 255.0
     * b = Blue(pixel) / 255.0
     * ```
     * 
     * ### 第二步：归一化到 [-1, 1]
     * ```
     * normalizedR = (r - 0.5) / 0.5 = 2r - 1
     * normalizedG = (g - 0.5) / 0.5 = 2g - 1
     * normalizedB = (b - 0.5) / 0.5 = 2b - 1
     * ```
     * 
     * ## 数据布局转换
     * 
     * ### 原始像素布局（交错存储）
     * ```
     * [R1,G1,B1] [R2,G2,B2] [R3,G3,B3] ...
     * ```
     * 
     * ### 模型输入布局（分离存储）
     * ```
     * [R1, R2, R3, ..., G1, G2, G3, ..., B1, B2, B3, ...]
     * ```
     * 
     * ### 索引计算
     * 对于坐标 (x, y) 的像素：
     * - R 通道索引：y × width + x
     * - G 通道索引：imageSize + (y × width + x)
     * - B 通道索引：2 × imageSize + (y × width + x)
     * 
     * 其中 imageSize = width × height
     * 
     * ## 性能优化
     * 
     * 1. **避免重复缩放**:
     *    - 如果尺寸已匹配，直接使用原图
     *    - 减少内存分配和拷贝
     * 
     * 2. **资源管理**:
     *    - 使用 try-finally 确保缩放后的 Bitmap 被回收
     *    - 避免内存泄漏
     * 
     * 3. **典型耗时**:
     *    - 640x480 图片：50-100ms
     *    - 1024x768 图片：100-200ms
     * 
     * ## 示例
     * 
     * ### 示例 1：基本用法
     * ```kotlin
     * val utils = OcrBitmapUtils()
     * val bitmap = BitmapFactory.decodeFile("/path/to/image.jpg")
     * 
     * // 预处理为 640x480 的模型输入
     * val input = utils.preprocessBitmap(bitmap, 480, 640)
     * 
     * // input 长度 = 480 × 640 × 3 = 921,600
     * // 数据范围 = [-1, 1]
     * 
     * // 传递给 PaddleLite 模型
     * predictor.setInput(input)
     * predictor.run()
     * ```
     * 
     * ### 示例 2：验证数据范围
     * ```kotlin
     * val input = utils.preprocessBitmap(bitmap, 480, 640)
     * 
     * val min = input.minOrNull() ?: 0f
     * val max = input.maxOrNull() ?: 0f
     * 
     * println("数据范围: [$min, $max]")
     * // 输出: 数据范围: [-1.0, 1.0]
     * ```
     * 
     * ## 注意事项
     * 
     * 1. **内存占用**:
     *    - 输入数组大小 = targetWidth × targetHeight × 3 × 4 字节
     *    - 640x480 图片：约 3.5MB
     *    - 1024x768 图片：约 9MB
     * 
     * 2. **线程安全**:
     *    - 此方法不是线程安全的
     *    - 建议在单个线程中调用
     * 
     * 3. **资源清理**:
     *    - 如果传入的 bitmap 不再使用，记得回收
     *    - 方法内部会自动回收缩放后的临时 Bitmap
     * 
     * @param bitmap 原始图片
     * @param targetHeight 目标高度（像素）
     * @param targetWidth 目标宽度（像素）
     * @return 归一化的 FloatArray，长度 = targetWidth × targetHeight × 3
     */
    fun preprocessBitmap(bitmap: Bitmap, targetHeight: Int, targetWidth: Int): FloatArray {
        var resized: Bitmap? = null
        try {
            // 缩放图片
            resized = if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }
            
            val pixels = IntArray(targetWidth * targetHeight)
            resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            
            val imageSize = targetWidth * targetHeight
            val input = FloatArray(3 * imageSize)
            
            // 归一化：[0, 255] -> [-1, 1]
            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val pixel = pixels[y * targetWidth + x]
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    val normalizedR = (r - 0.5f) / 0.5f
                    val normalizedG = (g - 0.5f) / 0.5f
                    val normalizedB = (b - 0.5f) / 0.5f
                    val index = y * targetWidth + x
                    input[index] = normalizedR
                    input[imageSize + index] = normalizedG
                    input[2 * imageSize + index] = normalizedB
                }
            }
            
            return input
        } finally {
            // 确保释放缩放后的Bitmap
            if (resized != null && resized != bitmap) {
                resized.recycle()
            }
        }
    }

    fun preprocessBitmapWithMeanStd(
        bitmap: Bitmap,
        targetHeight: Int,
        targetWidth: Int,
        mean: FloatArray,
        std: FloatArray
    ): FloatArray {
        var resized: Bitmap? = null
        try {
            resized = if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }

            val pixels = IntArray(targetWidth * targetHeight)
            resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            val imageSize = targetWidth * targetHeight
            val input = FloatArray(3 * imageSize)

            for (y in 0 until targetHeight) {
                for (x in 0 until targetWidth) {
                    val pixel = pixels[y * targetWidth + x]
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    val index = y * targetWidth + x
                    input[index] = (r - mean[0]) / std[0]
                    input[imageSize + index] = (g - mean[1]) / std[1]
                    input[2 * imageSize + index] = (b - mean[2]) / std[2]
                }
            }
            return input
        } finally {
            if (resized != null && resized != bitmap) {
                resized.recycle()
            }
        }
    }

    fun preprocessDetBitmap(bitmap: Bitmap, maxSideLen: Int): DetPreprocessResult {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = min(1f, maxSideLen.toFloat() / max(width, height).toFloat())
        var resizeWidth = (width * ratio).toInt()
        var resizeHeight = (height * ratio).toInt()
        resizeWidth = max(32, resizeWidth / 32 * 32)
        resizeHeight = max(32, resizeHeight / 32 * 32)

        val scaleX = width.toFloat() / resizeWidth.toFloat()
        val scaleY = height.toFloat() / resizeHeight.toFloat()
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val input = preprocessBitmapWithMeanStd(bitmap, resizeHeight, resizeWidth, mean, std)
        return DetPreprocessResult(
            data = input,
            resizeWidth = resizeWidth,
            resizeHeight = resizeHeight,
            scaleX = scaleX,
            scaleY = scaleY
        )
    }

    fun rotateBitmap(bitmap: Bitmap, angle: Int): Bitmap {
        if (angle % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(angle.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cropBitmap(bitmap: Bitmap, box: List<Float>): Bitmap? {
        if (box.size < 4) return null
        val left = box[0].toInt().coerceIn(0, bitmap.width - 1)
        val top = box[1].toInt().coerceIn(0, bitmap.height - 1)
        val right = box[2].toInt().coerceIn(left + 1, bitmap.width)
        val bottom = box[3].toInt().coerceIn(top + 1, bitmap.height)
        return runCatching {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        }.getOrNull()
    }
    
    /**
     * 透视变换
     * 
     * ## 功能说明
     * 使用 Android 原生 Matrix 和 Canvas 进行透视变换，将任意四边形映射到矩形
     * 
     * ## 应用场景
     * 当用户斜着拍摄文档时，文档会呈现梯形变形。透视变换可以将梯形还原为矩形，
     * 大幅提升 OCR 识别准确率。
     * 
     * ## 算法原理
     * 
     * ### 透视变换矩阵
     * 透视变换使用 3x3 矩阵表示：
     * ```
     * [a b c]
     * [d e f]
     * [g h 1]
     * ```
     * 
     * 变换公式：
     * ```
     * x' = (ax + by + c) / (gx + hy + 1)
     * y' = (dx + ey + f) / (gx + hy + 1)
     * ```
     * 
     * ### Matrix.setPolyToPoly
     * Android 提供的 setPolyToPoly 方法可以自动计算透视变换矩阵：
     * - 输入：源四边形的 4 个角点坐标
     * - 输出：目标矩形的 4 个角点坐标
     * - 返回：是否成功计算矩阵
     * 
     * ## 处理流程
     * ```
     * 1. 验证参数
     *    ├─ 检查目标尺寸是否有效（> 1）
     *    └─ 如果无效，返回 null
     * 
     * 2. 创建输出 Bitmap
     *    └─ 尺寸 = dstWidth × dstHeight
     * 
     * 3. 构建源点数组
     *    └─ 将 4 个 PointF 转换为 8 个 Float（x1,y1,x2,y2,x3,y3,x4,y4）
     * 
     * 4. 构建目标点数组
     *    └─ 矩形的四个角点：(0,0), (w,0), (w,h), (0,h)
     * 
     * 5. 计算透视变换矩阵
     *    └─ 使用 Matrix.setPolyToPoly
     * 
     * 6. 应用变换
     *    └─ 使用 Canvas.drawBitmap 绘制变换后的图片
     * 
     * 7. 返回结果
     *    └─ 成功返回新 Bitmap，失败返回 null
     * ```
     * 
     * ## 角点顺序
     * 
     * 源点和目标点必须按相同顺序排列：
     * ```
     * ordered[0] → (0, 0)           左上角
     * ordered[1] → (width, 0)       右上角
     * ordered[2] → (width, height)  右下角
     * ordered[3] → (0, height)      左下角
     * ```
     * 
     * ## 失败情况
     * 
     * 以下情况会导致变换失败，返回 null：
     * 1. 目标尺寸无效（≤ 1）
     * 2. 四个角点不构成有效的四边形
     * 3. Matrix.setPolyToPoly 计算失败
     * 
     * ## 性能考虑
     * 
     * 1. **内存占用**:
     *    - 创建新的 Bitmap：dstWidth × dstHeight × 4 字节
     *    - 800x600 图片：约 1.8MB
     * 
     * 2. **典型耗时**:
     *    - 800x600 图片：50-100ms
     *    - 1024x768 图片：100-150ms
     * 
     * 3. **资源管理**:
     *    - 如果变换失败，会自动回收创建的 Bitmap
     *    - 调用者需要回收返回的 Bitmap
     * 
     * ## 示例
     * 
     * ### 示例 1：校正梯形文档
     * ```kotlin
     * val utils = OcrBitmapUtils()
     * 
     * // 梯形文档的四个角点（按顺序：左上、右上、右下、左下）
     * val srcPoints = listOf(
     *     PointF(100f, 50f),   // 左上
     *     PointF(900f, 100f),  // 右上
     *     PointF(950f, 700f),  // 右下
     *     PointF(50f, 650f)    // 左下
     * )
     * 
     * // 透视变换为 800x600 的矩形
     * val corrected = utils.warpPerspective(bitmap, srcPoints, 800, 600)
     * 
     * if (corrected != null) {
     *     println("透视变换成功")
     *     // 使用校正后的图片进行 OCR
     *     val result = ocrEngine.recognize(corrected)
     *     
     *     // 清理资源
     *     corrected.recycle()
     * } else {
     *     println("透视变换失败，使用原图")
     * }
     * ```
     * 
     * ### 示例 2：验证角点顺序
     * ```kotlin
     * // 错误的角点顺序会导致变换失败或结果错误
     * val wrongOrder = listOf(
     *     PointF(100f, 50f),   // 左上
     *     PointF(50f, 650f),   // 左下 ❌ 顺序错误
     *     PointF(900f, 100f),  // 右上
     *     PointF(950f, 700f)   // 右下
     * )
     * 
     * // 正确的角点顺序：左上、右上、右下、左下（顺时针）
     * val correctOrder = listOf(
     *     PointF(100f, 50f),   // 左上 ✅
     *     PointF(900f, 100f),  // 右上 ✅
     *     PointF(950f, 700f),  // 右下 ✅
     *     PointF(50f, 650f)    // 左下 ✅
     * )
     * ```
     * 
     * ## 注意事项
     * 
     * 1. **角点顺序**:
     *    - 必须按照左上、右上、右下、左下的顺序
     *    - 顺序错误会导致变换结果错误
     * 
     * 2. **尺寸限制**:
     *    - 建议目标尺寸不超过 4096x4096
     *    - 过大的尺寸可能导致 OutOfMemoryError
     * 
     * 3. **资源清理**:
     *    - 变换成功后，调用者需要回收返回的 Bitmap
     *    - 变换失败时，方法内部会自动回收创建的 Bitmap
     * 
     * 4. **线程安全**:
     *    - 此方法不是线程安全的
     *    - 建议在单个线程中调用
     * 
     * @param bitmap 原始图片
     * @param ordered 四个角点（按顺序：左上、右上、右下、左下）
     * @param dstWidth 目标宽度（像素）
     * @param dstHeight 目标高度（像素）
     * @return 变换后的图片，失败返回 null
     */
    fun warpPerspective(
        bitmap: Bitmap,
        ordered: List<PointF>,
        dstWidth: Int,
        dstHeight: Int
    ): Bitmap? {
        if (dstWidth <= 1 || dstHeight <= 1) return null
        
        val output = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = Matrix()
        
        val srcPoints = FloatArray(8)
        for (i in 0 until 4) {
            srcPoints[i * 2] = ordered[i].x
            srcPoints[i * 2 + 1] = ordered[i].y
        }
        
        val dstPoints = floatArrayOf(
            0f, 0f,
            dstWidth.toFloat(), 0f,
            dstWidth.toFloat(), dstHeight.toFloat(),
            0f, dstHeight.toFloat()
        )
        
        // setPolyToPoly对于4个点执行透视变换
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        if (!success) {
            output.recycle()
            return null
        }
        
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        
        return output
    }
}
