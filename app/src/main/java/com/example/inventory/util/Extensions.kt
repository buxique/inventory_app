package com.example.inventory.util

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.ui.Modifier

// ==================== String 扩展 ====================

/**
 * 验证是否为有效的条码
 * 
 * 条码应为8-13位数字
 */
fun String.isValidBarcode(): Boolean {
    return this.matches(Regex("^[0-9]{8,13}$"))
}

/**
 * 验证是否为有效的价格
 * 
 * 价格应为非负数
 */
fun String.isValidPrice(): Boolean {
    return this.toDoubleOrNull()?.let { it >= 0 } ?: false
}

/**
 * 验证是否为有效的数量
 * 
 * 数量应为非负整数
 */
fun String.isValidQuantity(): Boolean {
    return this.toIntOrNull()?.let { it >= 0 } ?: false
}

/**
 * 截断字符串到指定长度
 * 
 * @param maxLength 最大长度
 * @param ellipsis 省略符号
 * @return 截断后的字符串
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return when {
        maxLength <= 0 -> ""
        this.length <= maxLength -> this
        maxLength <= ellipsis.length -> ellipsis.take(maxLength)
        else -> this.substring(0, maxLength - ellipsis.length) + ellipsis
    }
}

// ==================== Flow 扩展 ====================

/**
 * 节流操作符 - 在指定时间窗口内只发射第一个值
 * 
 * @param windowDuration 时间窗口（毫秒）
 * @return 节流后的 Flow
 */
fun <T> Flow<T>.throttleFirst(windowDuration: Long): Flow<T> = flow {
    var lastEmissionTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime >= windowDuration) {
            lastEmissionTime = currentTime
            emit(value)
        }
    }
}

/**
 * 防抖操作符 - 在指定时间内没有新值时才发射最后一个值
 * 
 * @param timeoutMillis 超时时间（毫秒）
 * @return 防抖后的 Flow
 */
fun <T> Flow<T>.debounce(timeoutMillis: Long): Flow<T> = flow {
    var lastValue: T? = null
    var lastEmitTime = 0L
    
    collect { value ->
        lastValue = value
        val currentTime = System.currentTimeMillis()
        lastEmitTime = currentTime
        
        delay(timeoutMillis)
        
        if (System.currentTimeMillis() - lastEmitTime >= timeoutMillis) {
            lastValue?.let { emit(it) }
        }
    }
}

// ==================== Context 扩展 ====================

/**
 * 显示 Toast 消息
 * 
 * @param message 消息内容
 * @param duration 显示时长
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * 检查网络是否可用
 * 
 * @return true 如果网络可用
 */
fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ==================== ViewModel 扩展 ====================

/**
 * 安全启动协程
 * 
 * 自动捕获异常并调用错误处理回调
 * 
 * @param onError 错误处理回调
 * @param block 协程代码块
 */
fun ViewModel.launchSafe(
    onError: (Exception) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            // 必须重新抛出 CancellationException
            // 否则协程无法正常取消，可能导致资源泄漏
            throw e
        } catch (e: Exception) {
            onError(e)
            AppLogger.e("协程执行失败: ${e.message}", "ViewModel", e)
        }
    }
}

// ==================== List 扩展 ====================

/**
 * 安全分块
 * 
 * 如果 size <= 0，返回包含整个列表的单个列表
 * 
 * @param size 每块的大小
 * @return 分块后的列表
 */
fun <T> List<T>.chunkedSafe(size: Int): List<List<T>> {
    return if (size <= 0) listOf(this) else chunked(size)
}

// ==================== Bitmap 扩展 ====================

/**
 * 将 Bitmap 转换为字节数组
 * 
 * @param format 压缩格式
 * @param quality 压缩质量（0-100）
 * @return 字节数组
 */
fun Bitmap.toByteArray(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100
): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(format, quality, stream)
    return stream.toByteArray()
}

// ==================== Long 扩展（时间戳） ====================

/**
 * 将时间戳转换为日期字符串
 * 
 * @param pattern 日期格式
 * @return 格式化的日期字符串
 */
fun Long.toDateString(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * 将时间戳转换为相对时间描述
 * 
 * @return 相对时间描述（如"刚刚"、"5分钟前"）
 */
fun Long.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 2592000_000 -> "${diff / 86400_000}天前"
        diff < 31536000_000 -> "${diff / 2592000_000}个月前"
        else -> "${diff / 31536000_000}年前"
    }
}

// ==================== Modifier 扩展 ====================

/**
 * 条件修饰符
 * 
 * 根据条件决定是否应用修饰符
 * 
 * @param condition 条件
 * @param modifier 要应用的修饰符
 * @return 修饰符
 */
fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}

// ==================== Double 扩展 ====================

/**
 * 格式化为货币字符串
 * 
 * @param symbol 货币符号
 * @return 格式化的货币字符串
 */
fun Double.toCurrencyString(symbol: String = "¥"): String {
    return "$symbol%.2f".format(this)
}

// ==================== Int 扩展 ====================

/**
 * 格式化为千分位字符串
 * 
 * @return 格式化的字符串（如 1,234,567）
 */
fun Int.toFormattedString(): String {
    return "%,d".format(this)
}
