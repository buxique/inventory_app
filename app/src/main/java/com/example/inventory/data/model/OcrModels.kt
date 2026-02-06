package com.example.inventory.data.model

data class OcrToken(
    val text: String,
    val confidence: Float,
    val box: List<Float> = emptyList() // [left, top, right, bottom] normalized 0..1 or absolute
)

data class OcrGroup(
    val id: String,
    val tokens: List<OcrToken>,
    val confidence: Float,
    val box: List<Float> = emptyList() // Bounding box of the group
)

data class OcrResult(
    val groups: List<OcrGroup>
)

data class OcrTableCell(
    val id: String,
    val text: String,
    val confidence: Float,
    val box: List<Float>,
    val rowIndex: Int? = null,
    val colIndex: Int? = null,
    val rowSpan: Int = 1,
    val colSpan: Int = 1
)

data class OcrTableResult(
    val cells: List<OcrTableCell>
)

/**
 * OCR 场景类型（输出层使用）
 *
 * 用于将内部分类结果暴露给 UI 与 JSON 输出层。
 */
enum class OcrSceneType {
    Document,
    ItemPhoto
}

/**
 * OCR 版面类型（输出层使用）
 *
 * - Table: 规则表格结构
 * - TextLabel: 普通文本标签或非表格文本
 */
enum class OcrLayoutType {
    Table,
    TextLabel
}

/**
 * OCR 输入图片的尺寸信息
 */
data class OcrImageMeta(
    val width: Int,
    val height: Int
)

/**
 * OCR 识别结果的矩形框
 *
 * normalized=true 时坐标范围为 0..1，false 时为像素坐标。
 */
data class OcrBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val normalized: Boolean = false
)

/**
 * UI 叠加层单条元素
 *
 * 将 OCR 文本与 bbox 打包给 UI 图层，便于选中、拖拽、合并。
 */
data class OcrOverlayItem(
    val id: String,
    val text: String,
    val confidence: Float,
    val box: OcrBoundingBox
)

/**
 * UI 叠加层载荷
 *
 * 对应 JSON 输出结构的 overlay 部分。
 */
data class OcrOverlayPayload(
    val image: OcrImageMeta,
    val items: List<OcrOverlayItem>
)

/**
 * OCR pipeline 输出结构
 *
 * JSON 示例：
 * {
 *   "scene": "Document",
 *   "layout": "Table",
 *   "image": { "width": 1920, "height": 1080 },
 *   "result": {
 *     "groups": [
 *       {
 *         "id": "group-1",
 *         "confidence": 0.98,
 *         "box": [12, 34, 560, 78],
 *         "tokens": [
 *           { "text": "螺丝", "confidence": 0.98, "box": [12, 34, 90, 78] }
 *         ]
 *       }
 *     ]
 *   }
 * }
 */
data class OcrPipelineOutput(
    val scene: OcrSceneType,
    val layout: OcrLayoutType,
    val image: OcrImageMeta,
    val result: OcrResult,
    val table: OcrTableResult? = null
)

/**
 * 将 OCR 结果转换为 UI 叠加层结构
 */
fun OcrResult.toOverlayPayload(image: OcrImageMeta, normalized: Boolean = false): OcrOverlayPayload {
    val items = groups.map { group ->
        val text = group.tokens.joinToString("") { it.text }
        val box = if (group.box.size == 4) {
            OcrBoundingBox(
                left = group.box[0],
                top = group.box[1],
                right = group.box[2],
                bottom = group.box[3],
                normalized = normalized
            )
        } else {
            OcrBoundingBox(0f, 0f, 0f, 0f, normalized)
        }
        OcrOverlayItem(
            id = group.id,
            text = text,
            confidence = group.confidence,
            box = box
        )
    }
    return OcrOverlayPayload(image = image, items = items)
}
