package com.example.inventory.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 性能监控工具
 * 
 * 用于监控和记录关键操作的性能指标
 */
class PerformanceMonitor {
    
    // 性能指标存储
    private val metrics = ConcurrentHashMap<String, QueryMetric>()
    
    /**
     * 测量操作执行时间
     * 
     * @param name 操作名称
     * @param block 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> measure(name: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime
        
        // 记录指标
        val metric = metrics.getOrPut(name) { QueryMetric(name) }
        metric.record(duration)
        
        // 慢操作警告（超过1秒）
        if (duration > 1000) {
            AppLogger.w("慢操作: $name (${duration}ms)", "PerformanceMonitor")
        } else {
            AppLogger.d("操作完成: $name (${duration}ms)", "PerformanceMonitor")
        }
        
        return result
    }
    
    /**
     * 获取所有性能指标
     * 
     * @return 指标映射表
     */
    fun getMetrics(): Map<String, QueryMetric> = metrics.toMap()
    
    /**
     * 获取指定操作的性能指标
     * 
     * @param name 操作名称
     * @return 性能指标，如果不存在返回 null
     */
    fun getMetric(name: String): QueryMetric? = metrics[name]
    
    /**
     * 重置所有指标
     */
    fun reset() {
        metrics.clear()
        AppLogger.i("性能指标已重置", "PerformanceMonitor")
    }
    
    /**
     * 重置指定操作的指标
     * 
     * @param name 操作名称
     */
    fun reset(name: String) {
        metrics.remove(name)
        AppLogger.d("已重置指标: $name", "PerformanceMonitor")
    }
    
    /**
     * 生成性能报告
     * 
     * @return 格式化的性能报告字符串
     */
    fun generateReport(): String {
        if (metrics.isEmpty()) {
            return "暂无性能数据"
        }
        
        val report = StringBuilder()
        report.appendLine("=== 性能监控报告 ===")
        report.appendLine()
        
        metrics.values.sortedByDescending { it.avgTime }.forEach { metric ->
            report.appendLine("操作: ${metric.name}")
            report.appendLine("  调用次数: ${metric.count}")
            report.appendLine("  总耗时: ${metric.totalTime}ms")
            report.appendLine("  平均耗时: ${metric.avgTime}ms")
            report.appendLine("  最大耗时: ${metric.maxTime}ms")
            report.appendLine("  最小耗时: ${metric.minTime}ms")
            report.appendLine()
        }
        
        return report.toString()
    }
}

/**
 * 查询性能指标
 * 
 * 记录单个操作的性能统计信息
 */
data class QueryMetric(
    val name: String,
    @Volatile var count: Long = 0,
    @Volatile var totalTime: Long = 0,
    @Volatile var maxTime: Long = 0,
    @Volatile var minTime: Long = Long.MAX_VALUE
) {
    /**
     * 记录一次执行
     * 
     * @param duration 执行时长（毫秒）
     */
    @Synchronized
    fun record(duration: Long) {
        count++
        totalTime += duration
        maxTime = maxOf(maxTime, duration)
        minTime = minOf(minTime, duration)
    }
    
    /**
     * 平均执行时间
     */
    val avgTime: Long 
        get() = if (count > 0) totalTime / count else 0
}
