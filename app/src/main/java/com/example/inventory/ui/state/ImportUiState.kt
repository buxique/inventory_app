package com.example.inventory.ui.state

/**
 * 导入进度状态
 * 
 * 用于显示文件导入的进度信息
 */
data class ImportProgress(
    val isImporting: Boolean = false,      // 是否正在导入
    val currentCount: Int = 0,             // 已导入数量
    val totalCount: Int = 0,               // 总数量
    val progress: Float = 0f,              // 进度（0.0 - 1.0）
    val error: String? = null              // 错误信息
)

/**
 * 文件类型枚举
 */
enum class FileType {
    EXCEL,      // Excel 文件 (.xlsx, .xls)
    ACCESS,     // Access 数据库 (.mdb, .accdb)
    DATABASE    // SQLite 数据库 (.db)
}
