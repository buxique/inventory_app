package com.example.inventory.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用形状系统
 * 
 * 使用更大的圆角创造柔和、现代的视觉效果
 */
val AppShapes = Shapes(
    /**
     * 超小圆角 - 4dp
     * 用于：小芯片、标签
     */
    extraSmall = RoundedCornerShape(4.dp),
    
    /**
     * 小圆角 - 8dp
     * 用于：按钮、输入框
     */
    small = RoundedCornerShape(8.dp),
    
    /**
     * 中等圆角 - 16dp
     * 用于：卡片、列表项
     */
    medium = RoundedCornerShape(16.dp),
    
    /**
     * 大圆角 - 24dp
     * 用于：对话框、底部表单
     */
    large = RoundedCornerShape(24.dp),
    
    /**
     * 超大圆角 - 32dp
     * 用于：特殊容器、启动页
     */
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * 自定义圆角尺寸
 */
object CornerRadius {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 20.dp
    val XXLarge = 24.dp
    val XXXLarge = 32.dp
    val Circle = 999.dp  // 用于创建完全圆形
}
