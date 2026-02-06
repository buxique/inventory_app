package com.example.inventory.util

/**
 * 应用常量定义
 */
object Constants {
    
    // ==================== 数据库相关 ====================
    object Database {
        const val NAME = "inventory.db"
        const val VERSION = 10  // 与 Room 数据库版本保持一致
    }
    
    // ==================== 文件相关 ====================
    object File {
        const val CSV_EXTENSION = ".csv"
        const val XLSX_EXTENSION = ".xlsx"
        const val DB_EXTENSION = ".db"
        const val PREFIX_INVENTORY = "inventory"
        const val PREFIX_BACKUP = "inventory-backup"
        const val PREFIX_TEMP_BACKUP = "inventory_temp_backup"
    }
    
    // ==================== OCR相关 ====================
    object Ocr {
        const val INPUT_HEIGHT = 48
        const val INPUT_WIDTH = 320
        const val MAX_IMAGE_DIMENSION = 4096
        const val USE_ONNX = false
        const val BACKEND_AUTO = "auto"
        const val BACKEND_PADDLE = "paddle"
        const val BACKEND_ONNX = "onnx"
        const val BACKEND_OPENOCR = "openocr"
        const val DET_INPUT_SIZE = 640
        const val LAYOUT_INPUT_SIZE = 640
        const val ORI_INPUT_SIZE = 224
        
        // 模型路径（PaddleOCR v4）
        const val MODEL_DET = "ppocr/ppocrv4_det.nb"
        const val MODEL_REC = "ppocr/ppocrv4_rec.nb"
        const val MODEL_CLS = "ppocr/ch_ppocr_mobile_v2.0_cls_opt.nb"
        const val DICT_PATH = "ppocr/ppocr_keys_v1.txt"
        const val MODEL_DIR = "paddle"

        const val ONNX_REC_MODEL = "onnx/ch_PP-OCRv5_rec_mobile_infer.onnx"
        const val ONNX_DICT_PATH = "onnx/ppocrv5_dict.txt"
        const val ONNX_DET_MODEL = "onnx/ch_PP-OCRv5_mobile_det_infer.onnx"
        const val ONNX_LAYOUT_MODEL = "onnx/PP-DocLayoutV3.onnx"
        const val ONNX_TABLE_LAYOUT_MODEL = "onnx/picodet_lcnet_x1_0_fgd_layout_table_infer_model.onnx"
        const val ONNX_DOC_ORI_MODEL = "onnx/PP-LCNet_x1_0_doc_ori_infer.onnx"
        const val ONNX_TEXTLINE_ORI_MODEL = "onnx/PP-LCNet_x1_0_textline_ori_infer.onnx"
        const val ONNX_TABLE_STRUCTURE_MODEL = "onnx/SLANeXt_wired_infer.onnx"
        const val ONNX_RECTIFY_MODEL = "onnx/UVDoc_infer.onnx"
        const val OPENOCR_REC_MODEL = "onnx/openocr_rec_model.onnx"
        const val OPENOCR_DICT_PATH = "onnx/ppocrv5_dict.txt"
        const val OPENOCR_DET_MODEL = "onnx/openocr_det_model.onnx"
    }
    
    // ==================== 文件导入限制 ====================
    object Import {
        const val MAX_FILE_SIZE = 10 * 1024 * 1024  // 10MB
        const val MAX_ROWS = 10_000
        const val MAX_FIELD_NAME = 200
        const val MAX_FIELD_BRAND = 100
        const val MAX_FIELD_MODEL = 100
        const val MAX_FIELD_PARAMETERS = 500
        const val MAX_FIELD_BARCODE = 100
        const val MAX_FIELD_REMARK = 500
        const val MAX_QUANTITY = 1_000_000
    }
    
    // ==================== 同步超时设置 ====================
    object Sync {
        const val TIMEOUT_PUSH = 30_000L  // 30秒
        const val TIMEOUT_PULL = 30_000L  // 30秒
        const val TIMEOUT_MERGE = 60_000L // 60秒
    }
    
    // ==================== 网络超时设置 ====================
    object Network {
        const val CONNECTION_TIMEOUT = 15_000  // 15秒
        const val SOCKET_TIMEOUT = 30_000      // 30秒
        const val MAX_ERROR_RETRY = 3          // 重试3次
    }
    
    // ==================== 密码策略 ====================
    object Password {
        const val MIN_LENGTH = 10
        const val PBKDF2_ITERATIONS = 100_000
        const val SALT_LENGTH = 32
        const val HASH_LENGTH = 256
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val HASH_FORMAT_PREFIX = "pbkdf2_sha256"
    }
    
    // ==================== 错误消息 ====================
    object ErrorMessage {
        const val S3_CONFIG_NOT_FOUND = "未配置S3"
        const val S3_UPLOAD_FAILED = "上传失败"
        const val S3_DOWNLOAD_FAILED = "下载失败"
        const val BACKUP_FAILED = "备份失败"
        const val RESTORE_FAILED = "数据库恢复失败"
        const val NO_SYNC_RECORD = "无同步记录"
        const val FILE_TOO_LARGE = "文件超过最大允许大小"
        const val INVALID_EXCEL_FILE = "无效的Excel文件"
        const val INVALID_MODEL_PATH = "非法的模型文件路径"
        const val UNTRUSTED_CLASS = "不可信的类源"
        const val PASSWORD_POLICY_FAILED = "密码不符合策略"
        const val USER_ALREADY_EXISTS = "用户已存在"
        const val USER_NOT_FOUND = "未找到用户"
        const val USERNAME_EMPTY = "用户名不能为空"
    }

    // ==================== 缓存配置 ====================
    object Cache {
        // 图片缓存
        const val IMAGE_MEMORY_CACHE_DIVIDER = 8 // 内存缓存占比 1/8
        const val IMAGE_DISK_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
        const val IMAGE_DEFAULT_WIDTH = 1920
        const val IMAGE_DEFAULT_HEIGHT = 1080
        const val IMAGE_COMPRESS_QUALITY = 100
        const val IMAGE_COMPRESS_QUALITY_JPEG = 85

        // 查询缓存
        const val QUERY_DEFAULT_MAX_SIZE = 100
        const val QUERY_DEFAULT_TTL_MILLIS = 5 * 60 * 1000L // 5分钟
        const val QUERY_COMPUTE_RETRY_DELAY_MS = 10L
        const val QUERY_COMPUTE_MAX_RETRY_COUNT = 100

        // 列表缓存
        const val ITEMS_CACHE_MAX_SIZE = 50
        const val ITEMS_CACHE_TTL_MILLIS = 2 * 60 * 1000L // 2分钟

        // 记录缓存
        const val RECORDS_CACHE_MAX_SIZE = 100
        const val RECORDS_CACHE_TTL_MILLIS = 5 * 60 * 1000L // 5分钟

        // 搜索缓存
        const val SEARCH_CACHE_TTL_MILLIS = 30 * 1000L // 30秒
    }

    // ==================== 撤销/重做配置 ====================
    object Undo {
        const val MAX_HISTORY_SIZE = 30
    }
    
    // ==================== UI相关 ====================
    object UI {
        const val SCROLL_THRESHOLD = 200f  // 距离底部边缘触发滚动的像素值
        const val DRAG_THRESHOLD = 10f     // 拖拽识别阈值
        const val ANIMATION_DURATION = 300 // 动画持续时间（毫秒）
        const val SCROLL_SPEED = 20f       // 拖拽时滚动速度
        const val VERTICAL_SCROLL_SPEED = 20f // 垂直滚动速度
        const val HORIZONTAL_SCROLL_SPEED = 20f // 水平滚动速度
        const val SCANNING_ANIMATION_DURATION = 2000 // 扫描动画持续时间（毫秒）
    }
}
