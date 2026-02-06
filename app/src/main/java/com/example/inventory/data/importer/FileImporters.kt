package com.example.inventory.data.importer

import com.example.inventory.data.model.InventoryItemEntity
import com.healthmarketscience.jackcess.DatabaseBuilder
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.InputStream
import java.io.IOException
import com.example.inventory.util.Constants

interface FileImporter {
    val supportedExtensions: Set<String>
    suspend fun import(stream: InputStream): List<InventoryItemEntity>
}

class ExcelImporter : FileImporter {
    override val supportedExtensions: Set<String> = setOf("xlsx", "xls")

    override suspend fun import(stream: InputStream): List<InventoryItemEntity> {
        val limitedStream = LimitedInputStream(stream, Constants.Import.MAX_FILE_SIZE.toLong())
        
        if (!isValidExcelFile(limitedStream)) {
            throw IllegalArgumentException("无效的Excel文件")
        }
        
        val workbook = WorkbookFactory.create(limitedStream)
        val sheet = workbook.getSheetAt(0)
        val results = mutableListOf<InventoryItemEntity>()
        
        var rowCount = 0
        
        val iterator = sheet.iterator()
        val header = if (iterator.hasNext()) iterator.next() else null
        
        while (iterator.hasNext() && rowCount < Constants.Import.MAX_ROWS) {
            val row = iterator.next()
            rowCount++
            
            val name = row.getCell(0)?.stringCellValue?.take(Constants.Import.MAX_FIELD_NAME).orEmpty()
            val brand = row.getCell(1)?.stringCellValue?.take(Constants.Import.MAX_FIELD_BRAND).orEmpty()
            val model = row.getCell(2)?.stringCellValue?.take(Constants.Import.MAX_FIELD_MODEL).orEmpty()
            val parameters = row.getCell(3)?.stringCellValue?.take(Constants.Import.MAX_FIELD_PARAMETERS).orEmpty()
            val barcode = row.getCell(4)?.stringCellValue?.take(Constants.Import.MAX_FIELD_BARCODE).orEmpty()
            val quantity = row.getCell(5)?.numericCellValue?.toInt()?.coerceIn(0, Constants.Import.MAX_QUANTITY) ?: 0
            val remark = row.getCell(6)?.stringCellValue?.take(Constants.Import.MAX_FIELD_REMARK).orEmpty()
            
            if (name.isNotBlank() || barcode.isNotBlank()) {
                results += InventoryItemEntity(
                    listId = 0L,
                    name = name,
                    brand = brand,
                    model = model,
                    parameters = parameters,
                    barcode = barcode,
                    quantity = quantity,
                    remark = remark
                )
            }
        }
        workbook.close()
        return results
    }
    
    private fun isValidExcelFile(stream: InputStream): Boolean {
        // 确保流支持 mark/reset
        val bufferedStream = if (stream.markSupported()) {
            stream
        } else {
            java.io.BufferedInputStream(stream)
        }
        
        val header = ByteArray(8)
        bufferedStream.mark(8)
        val bytesRead = bufferedStream.read(header)
        bufferedStream.reset()
        
        if (bytesRead < 8) return false
        
        // 验证Excel文件魔数
        // XLSX (ZIP格式): 50 4B
        val isXlsx = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        // XLS (OLE格式): D0 CF
        val isXls = header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte()
        
        return isXlsx || isXls
    }
}

class AccessImporter(
    private val tempDir: File
) : FileImporter {
    override val supportedExtensions: Set<String> = setOf("mdb", "accdb")

    override suspend fun import(stream: InputStream): List<InventoryItemEntity> {
        val temp = kotlin.runCatching {
            File.createTempFile(Constants.File.PREFIX_INVENTORY, ".mdb", tempDir)
        }.getOrElse {
            File.createTempFile(Constants.File.PREFIX_INVENTORY, ".accdb", tempDir)
        }
        
        try {
            // 复制流到临时文件
            stream.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // 打开数据库
            val db = DatabaseBuilder.open(temp)
            val results = mutableListOf<InventoryItemEntity>()
            
            try {
                val tableName = db.tableNames.firstOrNull()
                val table = if (tableName != null) db.getTable(tableName) else null
                
                if (table != null) {
                    var rowCount = 0
                    for (row in table) {
                        if (rowCount >= Constants.Import.MAX_ROWS) break
                        rowCount++

                        val name = row["name"]?.toString()?.take(Constants.Import.MAX_FIELD_NAME).orEmpty()
                        val brand = row["brand"]?.toString()?.take(Constants.Import.MAX_FIELD_BRAND).orEmpty()
                        val model = row["model"]?.toString()?.take(Constants.Import.MAX_FIELD_MODEL).orEmpty()
                        val parameters = row["parameters"]?.toString()?.take(Constants.Import.MAX_FIELD_PARAMETERS).orEmpty()
                        val barcode = row["barcode"]?.toString()?.take(Constants.Import.MAX_FIELD_BARCODE).orEmpty()
                        val quantity = row["quantity"]?.toString()?.toIntOrNull()?.coerceIn(0, Constants.Import.MAX_QUANTITY) ?: 0
                        val remark = row["remark"]?.toString()?.take(Constants.Import.MAX_FIELD_REMARK).orEmpty()
                        
                        if (name.isNotBlank() || barcode.isNotBlank()) {
                            results += InventoryItemEntity(
                                listId = 0L,
                                name = name,
                                brand = brand,
                                model = model,
                                parameters = parameters,
                                barcode = barcode,
                                quantity = quantity,
                                remark = remark
                            )
                        }
                    }
                }
                
                return results
            } finally {
                // 确保数据库被关闭
                db.close()
            }
        } finally {
            // 确保临时文件被删除
            if (temp.exists()) {
                temp.delete()
            }
        }
    }
}

class GenericDatabaseImporter : FileImporter {
    override val supportedExtensions: Set<String> = setOf("db", "sqlite")

    override suspend fun import(stream: InputStream): List<InventoryItemEntity> {
        return emptyList()
    }
}

class ImportCoordinator(
    private val importers: List<FileImporter>
) {
    suspend fun importByExtension(extension: String, stream: InputStream): List<InventoryItemEntity> {
        val importer = importers.firstOrNull { it.supportedExtensions.contains(extension.lowercase()) }
        return importer?.import(stream).orEmpty()
    }
}

// 辅助类：限制InputStream大小
private class LimitedInputStream(
    private val source: InputStream,
    private val maxBytes: Long
) : InputStream() {
    private var bytesRead = 0L
    private var markPosition = 0L  // 记录 mark 时的位置
    
    override fun read(): Int {
        if (bytesRead >= maxBytes) {
            throw IOException("文件超过最大允许大小: ${maxBytes / 1024 / 1024}MB")
        }
        val result = source.read()
        if (result != -1) bytesRead++
        return result
    }
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesRead >= maxBytes) {
            throw IOException("文件超过最大允许大小: ${maxBytes / 1024 / 1024}MB")
        }
        val maxLen = (maxBytes - bytesRead).toInt().coerceAtMost(len)
        val result = source.read(b, off, maxLen)
        if (result > 0) bytesRead += result
        return result
    }
    
    override fun close() = source.close()
    
    override fun mark(readlimit: Int) {
        source.mark(readlimit)
        markPosition = bytesRead  // 记录当前位置
    }
    
    override fun reset() {
        source.reset()
        bytesRead = markPosition  // 回滚到 mark 时的位置
    }
    
    override fun markSupported() = source.markSupported()
}
