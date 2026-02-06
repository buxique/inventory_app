package com.example.inventory.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.model.OcrGroup
import com.example.inventory.data.model.OcrResult
import com.example.inventory.data.model.OcrToken
import com.example.inventory.data.repository.OcrRepository
import com.example.inventory.ui.state.CaptureUiState
import com.example.inventory.ui.state.ProcessingState
import com.example.inventory.ui.state.ViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CaptureViewModel(
    private val ocrRepository: OcrRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state

    fun toggleImageMinimize() {
        _state.update {
            val updated = when (it.viewState) {
                ViewState.ImageMinimized -> ViewState.Normal
                ViewState.ListMinimized -> ViewState.ImageMinimized
                ViewState.BothMinimized -> ViewState.ImageMinimized
                ViewState.Normal -> ViewState.ImageMinimized
            }
            it.copy(viewState = updated)
        }
    }
    
    fun setImageUri(uri: android.net.Uri?) {
        _state.update { it.copy(imageUri = uri, isCameraActive = false) }
    }

    fun addImagesToQueue(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        _state.update {
            val newQueue = it.imageQueue + uris
            if (it.imageUri == null) {
                val next = newQueue.first()
                it.copy(
                    imageQueue = newQueue.drop(1),
                    imageUri = next,
                    isCameraActive = false,
                    ocrGroups = emptyList(),
                    processingState = ProcessingState.Idle
                )
            } else {
                it.copy(
                    imageQueue = newQueue,
                    isCameraActive = false
                )
            }
        }
    }

    fun processNextImage() {
        _state.update { 
            val queue = it.imageQueue
            if (queue.isNotEmpty()) {
                val next = queue.first()
                it.copy(
                    imageUri = next,
                    imageQueue = queue.drop(1),
                    ocrGroups = emptyList(),
                    processingState = ProcessingState.Idle
                )
            } else {
                it
            }
        }
    }
    
    fun setCameraActive(active: Boolean) {
        _state.update { it.copy(isCameraActive = active) }
    }

    fun toggleListMinimize() {
        _state.update {
            val updated = when (it.viewState) {
                ViewState.ListMinimized -> ViewState.Normal
                ViewState.ImageMinimized -> ViewState.ListMinimized
                ViewState.BothMinimized -> ViewState.ListMinimized
                ViewState.Normal -> ViewState.ListMinimized
            }
            it.copy(viewState = updated)
        }
    }

    fun selectGroup(group: OcrGroup?) {
        _state.update { it.copy(selectedGroupId = group?.id) }
    }

    fun toggleGroupSelection(group: OcrGroup) {
        _state.update {
            val updated = it.selectedGroupIds.toMutableSet()
            if (updated.contains(group.id)) {
                updated.remove(group.id)
            } else {
                updated.add(group.id)
            }
            it.copy(selectedGroupIds = updated, selectedGroupId = group.id)
        }
    }

    fun splitSelectedGroups() {
        val selected = _state.value.selectedGroupIds
        if (selected.isEmpty()) return
        
        // 保存当前状态到撤销栈
        val currentGroups = _state.value.ocrGroups
        _state.update { 
            it.copy(undoStack = it.undoStack + listOf(currentGroups))
        }
        
        val updated = _state.value.ocrGroups.flatMap { group ->
            if (!selected.contains(group.id)) return@flatMap listOf(group)
            if (group.tokens.size > 1) {
                group.tokens.map { token ->
                    val box = if (token.box.isNotEmpty()) token.box else group.box
                    OcrGroup(
                        id = "${group.id}-${token.text}",
                        tokens = listOf(token),
                        confidence = token.confidence,
                        box = box
                    )
                }
            } else {
                val token = group.tokens.firstOrNull()
                if (token == null) {
                    listOf(group)
                } else {
                    if (token.text.isEmpty()) {
                        listOf(group)
                    } else {
                    val baseBox = if (token.box.isNotEmpty()) token.box else group.box
                    val boxes = if (baseBox.size == 4) {
                        val left = baseBox[0]
                        val top = baseBox[1]
                        val right = baseBox[2]
                        val bottom = baseBox[3]
                        val width = right - left
                        val charWidth = width / token.text.length
                        token.text.mapIndexed { index, _ ->
                            listOf(
                                left + charWidth * index,
                                top,
                                left + charWidth * (index + 1),
                                bottom
                            )
                        }
                    } else {
                        List(token.text.length) { emptyList() }
                    }
                    token.text.mapIndexed { index, ch ->
                        val box = boxes[index]
                        OcrGroup(
                            id = "${group.id}-${ch}",
                            tokens = listOf(OcrToken(ch.toString(), token.confidence, box)),
                            confidence = token.confidence,
                            box = box
                        )
                    }
                    }
                }
            }
        }
        _state.update { it.copy(ocrGroups = updated, selectedGroupIds = emptySet()) }
    }

    fun mergeSelectedGroups() {
        val selected = _state.value.selectedGroupIds
        if (selected.size < 2) return
        
        // 保存当前状态到撤销栈
        val currentGroups = _state.value.ocrGroups
        _state.update { 
            it.copy(undoStack = it.undoStack + listOf(currentGroups))
        }
        
        val groups = _state.value.ocrGroups
        val toMerge = groups.filter { selected.contains(it.id) }
        val mergedTokens = toMerge.flatMap { it.tokens }
        val confidence = if (toMerge.isEmpty()) 0f else toMerge.map { it.confidence }.average().toFloat()
        val boxes = toMerge.mapNotNull { if (it.box.size == 4) it.box else null }
        val mergedBox = if (boxes.isNotEmpty()) {
            val left = boxes.minOf { it[0] }
            val top = boxes.minOf { it[1] }
            val right = boxes.maxOf { it[2] }
            val bottom = boxes.maxOf { it[3] }
            listOf(left, top, right, bottom)
        } else {
            emptyList()
        }
        val merged = OcrGroup(
            id = "merged-${System.currentTimeMillis()}",
            tokens = mergedTokens,
            confidence = confidence,
            box = mergedBox
        )
        val remaining = groups.filterNot { selected.contains(it.id) }
        _state.update { it.copy(ocrGroups = remaining + merged, selectedGroupIds = emptySet()) }
    }
    
    /**
     * 撤销上一次操作
     */
    fun undo() {
        val undoStack = _state.value.undoStack
        if (undoStack.isEmpty()) return
        
        val previousState = undoStack.last()
        _state.update {
            it.copy(
                ocrGroups = previousState,
                undoStack = undoStack.dropLast(1),
                selectedGroupIds = emptySet()
            )
        }
    }

    fun runOcr(file: File) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(processingState = ProcessingState.Processing()) }
                val local = ocrRepository.recognizeLocal(file)
                val online = ocrRepository.recognizeOnline(file)
                val merged = ocrRepository.mergeResults(local, online)
                _state.update {
                    it.copy(
                        ocrGroups = merged.groups,
                        processingState = ProcessingState.Success(merged.groups.size)
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(processingState = ProcessingState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun updateGroups(groups: List<OcrGroup>) {
        _state.update { it.copy(ocrGroups = groups) }
    }
}
