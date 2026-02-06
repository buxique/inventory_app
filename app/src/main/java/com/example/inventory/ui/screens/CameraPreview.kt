package com.example.inventory.ui.screens

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import com.example.inventory.util.AppLogger
import java.io.File
import java.util.concurrent.Executor

@Composable
internal fun CameraPreview(
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                preview.setSurfaceProvider(previewView.surfaceProvider)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    AppLogger.e("Binding failed: ${e.message}", "CameraPreview", e)
                }
            }, executor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

internal fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageSaved: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        "captured_image_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                onImageSaved(uri)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (photoFile.exists()) {
                            photoFile.delete()
                        }
                    } catch (e: Exception) {
                    }
                }, 5000)
            }

            override fun onError(exc: ImageCaptureException) {
                try {
                    if (photoFile.exists()) {
                        photoFile.delete()
                    }
                } catch (e: Exception) {
                }
                onError(exc)
            }
        }
    )
}

internal fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            AppLogger.e("无法打开输入流: $uri", "CaptureScreen")
            return null
        }

        val file = File(context.cacheDir, "temp_ocr_${System.currentTimeMillis()}.jpg")

        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (file.exists() && file.length() > 0) {
            AppLogger.d("文件转换成功: ${file.absolutePath}, 大小: ${file.length()} 字节", "CaptureScreen")
            file
        } else {
            AppLogger.e("文件转换失败: 文件为空或不存在", "CaptureScreen")
            if (file.exists()) {
                file.delete()
            }
            null
        }
    } catch (e: java.io.IOException) {
        AppLogger.e("文件 I/O 错误: ${e.message}", "CaptureScreen", e)
        null
    } catch (e: SecurityException) {
        AppLogger.e("权限错误: ${e.message}", "CaptureScreen", e)
        null
    } catch (e: Exception) {
        AppLogger.e("文件转换失败: ${e.message}", "CaptureScreen", e)
        null
    }
}
