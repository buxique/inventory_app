package com.example.inventory.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.inventory.data.model.InventoryItemEntity
import com.example.inventory.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * 导出进度回调
 * @param current 当前进度
 * @param total 总数
 * @param message 进度消息
 */
typealias ProgressCallback = (current: Int, total: Int, message: String) -> Unit

interface ExportRepository {
    suspend fun exportCsv(items: List<InventoryItemEntity>, onProgress: ProgressCallback? = null): File
    suspend fun exportXlsx(items: List<InventoryItemEntity>, onProgress: ProgressCallback? = null): File
    suspend fun backupDatabase(onProgress: ProgressCallback? = null): File?
    suspend fun restoreDatabase(file: File, onProgress: ProgressCallback? = null): Boolean
}

class ExportRepositoryImpl(
    private val context: Context
) : ExportRepository {
    override suspend fun exportCsv(items: List<InventoryItemEntity>, onProgress: ProgressCallback?): File = withContext(Dispatchers.IO) {
        val total = items.size
        onProgress?.invoke(0, total, "开始导出CSV...")
        
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "inventory-${System.currentTimeMillis()}.csv")
        
        file.bufferedWriter().use { writer ->
            // 写入表头（包含所有字段）
            writer.appendLine("listId,name,brand,model,parameters,barcode,quantity,unit,location,remark")
            onProgress?.invoke(0, total, "写入表头...")
            
            // 写入数据
            items.forEachIndexed { index, item ->
                writer.appendLine(
                    listOf(
                        item.listId.toString(),
                        escapeCsvValue(item.name),
                        escapeCsvValue(item.brand),
                        escapeCsvValue(item.model),
                        escapeCsvValue(item.parameters),
                        escapeCsvValue(item.barcode),
                        item.quantity.toString(),
                        escapeCsvValue(item.unit),
                        escapeCsvValue(item.location),
                        escapeCsvValue(item.remark)
                    ).joinToString(",")
                )
                
                // 每10条数据报告一次进度
                if (index % 10 == 0 || index == total - 1) {
                    onProgress?.invoke(index + 1, total, "正在导出 ${index + 1}/$total 条数据...")
                }
            }
        }
        
        onProgress?.invoke(total, total, "导出完成")
        file
    }
    
    private fun escapeCsvValue(value: String): String {
        // 如果字符串包含逗号、引号或换行符，需要用引号包裹
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            // 引号需要双写转义
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    override suspend fun exportXlsx(items: List<InventoryItemEntity>, onProgress: ProgressCallback?): File = withContext(Dispatchers.IO) {
        val total = items.size
        onProgress?.invoke(0, total, "开始导出Excel...")
        
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "inventory-${System.currentTimeMillis()}.xlsx")
        val workbook = SXSSFWorkbook(100) // 内存保留 100 行，其余写入磁盘
        
        try {
            val sheet = workbook.createSheet("Inventory")
            
            // 创建表头（包含所有字段）
            onProgress?.invoke(0, total, "创建Excel表头...")
            val header = sheet.createRow(0)
            listOf("listId", "name", "brand", "model", "parameters", "barcode", "quantity", "unit", "location", "remark")
                .forEachIndexed { index, title -> header.createCell(index).setCellValue(title) }
            
            // 写入数据
            items.forEachIndexed { index, item ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(item.listId.toDouble())
                row.createCell(1).setCellValue(item.name)
                row.createCell(2).setCellValue(item.brand)
                row.createCell(3).setCellValue(item.model)
                row.createCell(4).setCellValue(item.parameters)
                row.createCell(5).setCellValue(item.barcode)
                row.createCell(6).setCellValue(item.quantity.toDouble())
                row.createCell(7).setCellValue(item.unit)
                row.createCell(8).setCellValue(item.location)
                row.createCell(9).setCellValue(item.remark)
                
                // 每10条数据报告一次进度
                if (index % 10 == 0 || index == total - 1) {
                    onProgress?.invoke(index + 1, total, "正在写入 ${index + 1}/$total 条数据...")
                }
            }
            
            // 保存文件
            onProgress?.invoke(total, total, "正在保存文件...")
            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
            
            onProgress?.invoke(total, total, "导出完成")
            file
        } finally {
            workbook.dispose() // 删除临时文件
            workbook.close()
        }
    }

    override suspend fun backupDatabase(onProgress: ProgressCallback?): File? = withContext(Dispatchers.IO) {
        onProgress?.invoke(0, 100, "开始备份数据库...")
        
        val dbFile = context.getDatabasePath("inventory.db")
        if (!dbFile.exists()) {
            onProgress?.invoke(0, 100, "数据库文件不存在")
            return@withContext null
        }
        
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val backup = File(dir, "inventory-backup-${System.currentTimeMillis()}.db")
        
        onProgress?.invoke(30, 100, "正在复制数据库...")
        dbFile.copyTo(backup, overwrite = true)
        
        onProgress?.invoke(100, 100, "备份完成")
        backup
    }

    override suspend fun restoreDatabase(file: File, onProgress: ProgressCallback?): Boolean = withContext(Dispatchers.IO) {
        onProgress?.invoke(0, 100, "开始恢复数据库...")
        
        val dbFile = context.getDatabasePath("inventory.db")
        if (!file.exists()) {
            onProgress?.invoke(0, 100, "备份文件不存在")
            return@withContext false
        }
        
        onProgress?.invoke(10, 100, "验证备份文件...")
        if (!isDatabaseValid(file)) {
            onProgress?.invoke(0, 100, "备份文件无效")
            return@withContext false
        }
        
        // 1. 创建临时备份，防止数据丢失
        val tempBackup = File(dbFile.parent, "inventory_temp_backup_${System.currentTimeMillis()}.db")
        var backupCreated = false
        
        try {
            // 2. 备份现有数据库
            onProgress?.invoke(20, 100, "备份当前数据库...")
            if (dbFile.exists()) {
                dbFile.copyTo(tempBackup, overwrite = true)
                backupCreated = true
            }
            
            // 3. 关闭数据库连接（通过 Application Context）
            onProgress?.invoke(40, 100, "关闭数据库连接...")
            try {
                // 获取 Application 实例并关闭数据库
                val app = context.applicationContext as? com.example.inventory.InventoryApplication
                if (app != null) {
                    // 注意：这里需要在 AppContainer 中添加 closeDatabase() 方法
                    // 暂时使用重试机制确保数据库文件可写
                    var retryCount = 0
                    val maxRetries = 10
                    while (retryCount < maxRetries) {
                        try {
                            // 尝试打开文件进行写入测试
                            val testStream = dbFile.outputStream()
                            testStream.close()
                            break  // 文件可写，退出循环
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount >= maxRetries) {
                                throw IllegalStateException("数据库文件被占用，无法恢复")
                            }
                            delay(100)  // 等待 100ms 后重试
                        }
                    }
                } else {
                    // 如果无法获取 Application，使用重试机制
                    var retryCount = 0
                    val maxRetries = 10
                    while (retryCount < maxRetries) {
                        try {
                            val testStream = dbFile.outputStream()
                            testStream.close()
                            break
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount >= maxRetries) {
                                throw IllegalStateException("数据库文件被占用，无法恢复")
                            }
                            delay(100)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("关闭数据库连接失败: ${e.message}", "ExportRepository", e)
                // 继续执行，使用重试机制
            }
            
            // 4. 执行恢复
            onProgress?.invoke(60, 100, "正在恢复数据库...")
            file.copyTo(dbFile, overwrite = true)
            
            // 5. 验证恢复后的数据库
            onProgress?.invoke(80, 100, "验证恢复结果...")
            delay(100)
            if (!isDatabaseValid(dbFile)) {
                onProgress?.invoke(90, 100, "恢复失败，正在回滚...")
                // 恢复失败，回滚到备份
                if (backupCreated) {
                    tempBackup.copyTo(dbFile, overwrite = true)
                }
                onProgress?.invoke(0, 100, "恢复失败")
                return@withContext false
            }
            
            // 6. 成功，删除临时备份
            if (tempBackup.exists()) {
                tempBackup.delete()
            }
            onProgress?.invoke(100, 100, "恢复完成")
            AppLogger.i("数据库恢复成功", "ExportRepository")
            true
        } catch (e: Exception) {
            onProgress?.invoke(90, 100, "发生错误，正在回滚...")
            AppLogger.e("数据库恢复失败: ${e.message}", "ExportRepository", e)
            // 出错时回滚
            if (backupCreated && tempBackup.exists()) {
                try {
                    tempBackup.copyTo(dbFile, overwrite = true)
                    AppLogger.i("已回滚到备份数据库", "ExportRepository")
                } catch (rollbackError: Exception) {
                    AppLogger.e("回滚失败: ${rollbackError.message}", "ExportRepository", rollbackError)
                }
            }
            if (tempBackup.exists()) {
                tempBackup.delete()
            }
            onProgress?.invoke(0, 100, "恢复失败: ${e.message}")
            false
        }
    }

    private fun isDatabaseValid(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val db = runCatching {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull() ?: return false
        return try {
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        } finally {
            db.close()
        }
    }
}
