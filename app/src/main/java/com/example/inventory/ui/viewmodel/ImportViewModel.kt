package com.example.inventory.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.importer.ImportCoordinator
import com.example.inventory.data.repository.InventoryRepository
import com.example.inventory.ui.state.FileType
import com.example.inventory.ui.state.ImportProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 导入视图模型
 * 
 * 负责管理文件导入流程和进度显示
 */
class ImportViewModel(
    private val importCoordinator: ImportCoordinator,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    
    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()
    
    private var importJob: Job? = null
    
    /**
     * 从输入流导入文件到指定列表
     * 
     * @param listId 目标列表ID
     * @param inputStream 文件输入流（由调用方负责关闭）
     * @param fileType 文件类型（excel, access, database）
     * @return 导入的商品数量
     */
    suspend fun importFromStream(
        listId: Long,
        inputStream: InputStream,
        fileType: FileType
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _progress.value = ImportProgress(isImporting = true)
            
            // 根据文件类型获取扩展名
            val extension = when (fileType) {
                FileType.EXCEL -> "xlsx"
                FileType.ACCESS -> "mdb"
                FileType.DATABASE -> "db"
            }
            
            // 使用 ImportCoordinator 读取和解析文件
            val items = importCoordinator.importByExtension(extension, inputStream)
            
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
