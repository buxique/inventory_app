package com.example.inventory.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inventory.R

/**
 * 图片选择器页面
 * 
 * 显示网格布局的图片列表，支持多选
 * 每个图片右上角有圆形选择按钮
 * 
 * @param onBack 返回回调
 * @param onComplete 完成选择回调，传递选中的图片ID列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerScreen(
    onBack: () -> Unit,
    onComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    // 选中的图片ID集合
    var selectedImages by remember { mutableStateOf(setOf<Int>()) }
    
    // 示例图片数据（实际应该从相册加载）
    val sampleImages = remember {
        listOf(
            ImageItem(1, Color(0xFFE0E0E0)),  // 灰色 - 尺子
            ImageItem(2, Color(0xFFFDD835)),  // 黄色 - 耳机
            ImageItem(3, Color(0xFFEEEEEE)),  // 浅灰 - 眼镜
            ImageItem(4, Color(0xFFFFCDD2)),  // 粉色 - 鞋子
            ImageItem(5, Color(0xFF212121)),  // 深色 - 手表
            ImageItem(6, Color(0xFFFFCCBC)),  // 橙粉 - 运动鞋
            ImageItem(7, Color(0xFFEEEEEE)),  // 灰色 - 运动鞋2
            ImageItem(8, Color(0xFFE53935)),  // 红色 - 红鞋
            ImageItem(9, Color(0xFFFFA726)),  // 橙色 - 帆布鞋
            ImageItem(10, Color(0xFF212121)), // 黑色 - 牛仔夹克
            ImageItem(11, Color(0xFFF48FB1)), // 粉色 - 香水
            ImageItem(12, Color(0xFF546E7A))  // 蓝灰 - 电脑
        )
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.image_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.menu_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onComplete(selectedImages.toList()) },
                        enabled = selectedImages.isNotEmpty()
                    ) {
                        Text(
                            text = stringResource(R.string.complete),
                            color = if (selectedImages.isNotEmpty()) 
                                Color(0xFF2196F3) 
                            else 
                                Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sampleImages) { image ->
                ImageGridItem(
                    image = image,
                    isSelected = selectedImages.contains(image.id),
                    onToggleSelection = {
                        selectedImages = if (selectedImages.contains(image.id)) {
                            selectedImages - image.id
                        } else {
                            selectedImages + image.id
                        }
                    }
                )
            }
        }
    }
}

/**
 * 图片网格项
 */
@Composable
private fun ImageGridItem(
    image: ImageItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onToggleSelection)
    ) {
        // 图片背景（示例用纯色代替）
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = image.color,
            shape = MaterialTheme.shapes.medium
        ) {
            // 这里应该显示实际图片
            // Image(painter = ..., contentDescription = null)
        }
        
        // 右上角选择按钮
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(28.dp),
            shape = CircleShape,
            color = if (isSelected) Color(0xFF2196F3) else Color.White,
            border = if (!isSelected) {
                androidx.compose.foundation.BorderStroke(2.dp, Color.White)
            } else null,
            shadowElevation = 2.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.image_picker_selected),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 图片数据类
 */
private data class ImageItem(
    val id: Int,
    val color: Color  // 示例用颜色代替实际图片
)
