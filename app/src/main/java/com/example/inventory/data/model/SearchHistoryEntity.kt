package com.example.inventory.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 搜索历史记录实体
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["query", "searchType"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["searchType"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 搜索关键词
     */
    val query: String,
    
    /**
     * 搜索类型
     * - "item": 商品搜索
     * - "category": 分类搜索
     * - "record": 记录搜索
     */
    val searchType: String = "item",
    
    /**
     * 搜索时间戳
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * 搜索次数（相同关键词累加）
     */
    val searchCount: Int = 1,
    
    /**
     * 结果数量
     */
    val resultCount: Int = 0,
    
    /**
     * 是否收藏（常用搜索）
     */
    val isFavorite: Boolean = false
)

/**
 * 搜索历史统计
 */
data class SearchHistoryStats(
    val totalSearches: Int,
    val uniqueQueries: Int,
    val averageResultCount: Float,
    val topQueries: List<SearchHistoryEntity>
)

/**
 * 搜索建议
 */
data class SearchSuggestion(
    val query: String,
    val type: SuggestionType,
    val score: Int  // 相关性得分
)

enum class SuggestionType {
    HISTORY,    // 历史搜索
    POPULAR,    // 热门搜索
    AUTOCOMPLETE // 自动完成
}
