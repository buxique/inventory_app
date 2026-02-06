package com.example.inventory.ui.state

import com.example.inventory.data.model.OcrGroup

/**
 * OCR采集页面UI状态
 */
data class CaptureUiState(
    val ocrGroups: List<OcrGroup> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedGroupIds: Set<String> = emptySet(),
    val viewState: ViewState = ViewState.Normal,
    val processingState: ProcessingState = ProcessingState.Idle,
    val imageUri: android.net.Uri? = null,
    val isCameraActive: Boolean = false,
    val imageQueue: List<android.net.Uri> = emptyList(),
    val undoStack: List<List<OcrGroup>> = emptyList() // 撤销栈
)

/**
 * 视图状态 - 管理界面布局
 */
sealed class ViewState {
    object Normal : ViewState()
    
    object ImageMinimized : ViewState()
    
    object ListMinimized : ViewState()
    
    object BothMinimized : ViewState()
}

/**
 * 处理状态 - 管理OCR识别进程
 */
sealed class ProcessingState {
    object Idle : ProcessingState()
    
    data class Processing(val progress: Float = 0f) : ProcessingState()
    
    data class Success(val resultCount: Int) : ProcessingState()
    
    data class Error(val message: String) : ProcessingState()
}
