package com.example.inventory.data.repository

import com.example.inventory.data.db.SearchHistoryDao
import com.example.inventory.data.model.SearchHistoryEntity
import com.example.inventory.data.model.SearchHistoryStats
import com.example.inventory.data.model.SearchSuggestion
import com.example.inventory.data.model.SuggestionType
import com.example.inventory.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 搜索历史仓库接口
 */
interface SearchHistoryRepository {
    /**
     * 记录搜索
     */
    suspend fun recordSearch(query: String, searchType: String, resultCount: Int)
    
    /**
     * 获取搜索历史
     */
    suspend fun getSearchHistory(offset: Int = 0, limit: Int = 50): List<SearchHistoryEntity>
    
    /**
     * 获取搜索历史总数
     */
    suspend fun getSearchHistoryCount(): Int
    
    /**
     * 获取热门搜索
     */
    suspend fun getPopularSearches(limit: Int = 10): List<SearchHistoryEntity>
    
    /**
     * 获取搜索建议
     */
    suspend fun getSearchSuggestions(query: String, searchType: String, limit: Int = 5): List<SearchSuggestion>
    
    /**
     * 切换收藏状态
     */
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean): Boolean
    
    /**
     * 删除搜索历史
     */
    suspend fun deleteSearchHistory(id: Long): Boolean
    
    /**
     * 清空搜索历史
     */
    suspend fun clearHistory(keepFavorites: Boolean = true): Boolean
    
    /**
     * 获取搜索统计
     */
    suspend fun getSearchStats(): SearchHistoryStats
}

/**
 * 搜索历史仓库实现
 */
class SearchHistoryRepositoryImpl(
    private val searchHistoryDao: SearchHistoryDao
) : SearchHistoryRepository {
    
    companion object {
        /**
         * 最大搜索历史记录数
         * 
         * 超过此数量时自动清理最旧的记录（保留收藏）
         */
        private const val MAX_HISTORY_COUNT = 100
    }
    
    override suspend fun recordSearch(
        query: String, 
        searchType: String, 
        resultCount: Int
    ) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        val trimmedQuery = query.trim()
        val existing = searchHistoryDao.getSearchHistoryByQuery(trimmedQuery, searchType)
        if (existing != null) {
            val updated = existing.copy(
                timestamp = System.currentTimeMillis(),
                searchCount = existing.searchCount + 1,
                resultCount = resultCount
            )
            searchHistoryDao.insertSearchHistory(updated)
        } else {
            val newHistory = SearchHistoryEntity(
                query = trimmedQuery,
                searchType = searchType,
                timestamp = System.currentTimeMillis(),
                searchCount = 1,
                resultCount = resultCount,
                isFavorite = false
            )
            searchHistoryDao.insertSearchHistory(newHistory)
            
            // 自动清理旧记录，保持数据库大小合理
            val count = searchHistoryDao.getSearchHistoryCount()
            if (count > MAX_HISTORY_COUNT) {
                AppLogger.d("搜索历史超过限制($count > $MAX_HISTORY_COUNT)，开始清理", "SearchHistory")
                val deleted = cleanupOldHistory(keepCount = MAX_HISTORY_COUNT)
                AppLogger.d("已清理 $deleted 条旧搜索记录", "SearchHistory")
            }
        }
    }
    
    override suspend fun getSearchHistory(offset: Int, limit: Int): List<SearchHistoryEntity> = 
        withContext(Dispatchers.IO) {
            searchHistoryDao.getSearchHistoryPaged(offset, limit)
        }
    
    override suspend fun getSearchHistoryCount(): Int = withContext(Dispatchers.IO) {
        searchHistoryDao.getSearchHistoryCount()
    }
    
    override suspend fun getPopularSearches(limit: Int): List<SearchHistoryEntity> = 
        withContext(Dispatchers.IO) {
            searchHistoryDao.getPopularSearches(limit)
        }
    
    override suspend fun getSearchSuggestions(
        query: String, 
        searchType: String, 
        limit: Int
    ): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            // 返回热门搜索作为建议
            val popular = searchHistoryDao.getPopularSearches(limit)
            return@withContext popular.map { 
                SearchSuggestion(
                    query = it.query,
                    type = SuggestionType.POPULAR,
                    score = it.searchCount
                )
            }
        }
        
        // 基于历史记录的建议
        val pattern = "%${query.trim()}%"
        val historySuggestions = searchHistoryDao.getSearchSuggestions(pattern, searchType, limit)
        
        historySuggestions.map {
            SearchSuggestion(
                query = it.query,
                type = if (it.query == query.trim()) SuggestionType.HISTORY else SuggestionType.AUTOCOMPLETE,
                score = it.searchCount
            )
        }
    }
    
    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean): Boolean = 
        withContext(Dispatchers.IO) {
            searchHistoryDao.updateFavoriteStatus(id, isFavorite) > 0
        }
    
    override suspend fun deleteSearchHistory(id: Long): Boolean = 
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteSearchHistory(id) > 0
        }
    
    override suspend fun clearHistory(keepFavorites: Boolean): Boolean = 
        withContext(Dispatchers.IO) {
            val count = if (keepFavorites) {
                searchHistoryDao.clearNonFavoriteHistory()
            } else {
                searchHistoryDao.clearAllHistory()
            }
            count > 0
        }
    
    override suspend fun getSearchStats(): SearchHistoryStats = 
        withContext(Dispatchers.IO) {
            val rawStats = searchHistoryDao.getSearchHistoryStats()
            val topQueries = searchHistoryDao.getPopularSearches(10)
            val totalCount = searchHistoryDao.getTotalSearchCount() ?: 0
            
            SearchHistoryStats(
                totalSearches = totalCount,
                uniqueQueries = rawStats.uniqueQueries,
                averageResultCount = rawStats.avgResultCount,
                topQueries = topQueries
            )
        }
    
    /**
     * 清理旧记录（保留最近100条和收藏）
     */
    suspend fun cleanupOldHistory(keepCount: Int = 100): Int = 
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteOldHistory(keepCount)
        }
}
