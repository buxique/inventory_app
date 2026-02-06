package com.example.inventory.data.repository

import com.example.inventory.data.model.OcrPipelineOutput
import com.example.inventory.data.model.OcrResult
import java.io.File

interface OcrRepository {
    suspend fun recognizeLocal(file: File): OcrResult
    suspend fun recognizeLocalPipeline(file: File): OcrPipelineOutput
    suspend fun recognizeOnline(file: File): OcrResult
    suspend fun mergeResults(local: OcrResult, online: OcrResult): OcrResult
}
