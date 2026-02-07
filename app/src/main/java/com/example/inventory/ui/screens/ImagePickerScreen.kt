package com.example.inventory.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.inventory.ui.components.CustomImagePicker

/**
 * 图片选择器页面
 * 
 * 显示网格布局的图片列表，支持多选
 * 每个图片右上角有圆形选择按钮
 * 
 * @param onBack 返回回调
 * @param onComplete 完成选择回调，传递选中的图片 URI 列表
 */
@Composable
fun ImagePickerScreen(
    onBack: () -> Unit,
    onComplete: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        CustomImagePicker(
            onDismiss = onBack,
            onImagesSelected = onComplete
        )
    }
}
