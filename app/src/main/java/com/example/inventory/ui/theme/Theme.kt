package com.example.inventory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * 字号缩放比例的 CompositionLocal
 * 
 * 用于在整个应用中共享字号缩放设置
 */
val LocalFontScale = compositionLocalOf { 1.0f }

/**
 * 根据缩放比例创建 Typography
 * 
 * @param scale 缩放比例（0.85=小，1.0=标准，1.15=大，1.3=特大）
 * @return 缩放后的 Typography
 */
@Composable
fun scaledTypography(scale: Float): Typography {
    val base = AppTypography
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * scale),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * scale),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * scale),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * scale),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * scale),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * scale),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * scale),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * scale),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * scale),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * scale),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * scale),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * scale),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * scale),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * scale),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * scale)
    )
}

/**
 * 应用主题配置
 * 
 * 浅色主题：蓝色 + 粉色配色方案
 */
private val LightColorScheme = lightColorScheme(
    // 主色调
    primary = PrimaryBlue,
    onPrimary = TextWhite,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = Color(0xFF001D35),
    
    // 强调色
    secondary = AccentPink,
    onSecondary = TextWhite,
    secondaryContainer = AccentPinkContainer,
    onSecondaryContainer = Color(0xFF2D0011),
    
    // 第三色（可选）
    tertiary = WarningOrange,
    onTertiary = TextWhite,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF2D1600),
    
    // 错误色
    error = ErrorRed,
    onError = TextWhite,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    // 背景色
    background = BackgroundLight,
    onBackground = TextPrimary,
    
    // 表面色
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    // 轮廓色
    outline = Divider,
    outlineVariant = BorderLight,
    
    // 其他
    scrim = Color(0x80000000),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFF0F0F3),
    inversePrimary = Color(0xFF9CCAFF)
)

/**
 * 深色主题（可选）
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueVariant,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFCCE5FF),
    
    secondary = AccentPinkVariant,
    onSecondary = Color(0xFF5F1135),
    secondaryContainer = Color(0xFF7B2949),
    onSecondaryContainer = Color(0xFFFFD9E3),
    
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E5),
    
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE)
)

/**
 * 应用主题
 * 
 * @param darkTheme 是否使用深色主题
 * @param fontScale 字号缩放比例（0.85=小，1.0=标准，1.15=大，1.3=特大）
 * @param content 内容
 */
@Composable
fun InventoryAppTheme(
    darkTheme: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    
    val typography = scaledTypography(fontScale)
    
    CompositionLocalProvider(LocalFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
