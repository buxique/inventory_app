package com.example.inventory.data.db

import androidx.room.*
import com.example.inventory.data.model.SearchHistoryEntity

/**
 * 搜索历史记录 DAO
 */
@Dao
interface SearchHistoryDao {
    
    /**
     * 添加或更新搜索历史
     * 如果查询已存在，增加搜索次数并更新时间戳
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistoryEntity): Long
    
    /**
     * 根据查询和类型获取搜索历史
     */
    @Query("SELECT * FROM search_history WHERE query = :query AND searchType = :searchType LIMIT 1")
    suspend fun getSearchHistoryByQuery(query: String, searchType: String): SearchHistoryEntity?
    
    @Query("""
        SELECT * FROM search_history 
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getSearchHistoryPaged(offset: Int, limit: Int): List<SearchHistoryEntity>
    
    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getSearchHistoryCount(): Int
    
    /**
     * 获取所有搜索历史（按时间倒序）
     * 
     * @param limit 限制数量
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllSearchHistory(limit: Int = 100): List<SearchHistoryEntity>
    
    /**
     * 获取指定类型的搜索历史
     */
    @Query("SELECT * FROM search_history WHERE searchType = :searchType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSearchHistoryByType(searchType: String, limit: Int = 50): List<SearchHistoryEntity>
    
    /**
     * 获取热门搜索（按搜索次数排序）
     * 
     * @param limit 限制数量
     */
    @Query("SELECT * FROM search_history ORDER BY searchCount DESC, timestamp DESC LIMIT :limit")
    suspend fun getPopularSearches(limit: Int = 10): List<SearchHistoryEntity>
    
    /**
     * 获取收藏的搜索
     */
    @Query("SELECT * FROM search_history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    suspend fun getFavoriteSearches(): List<SearchHistoryEntity>
    
    /**
     * 搜索历史记录（模糊匹配）
     */
    @Query("SELECT * FROM search_history WHERE query LIKE :pattern ORDER BY searchCount DESC, timestamp DESC LIMIT :limit")
    suspend fun searchHistory(pattern: String, limit: Int = 20): List<SearchHistoryEntity>
    
    /**
     * 获取搜索建议
     * 基于历史记录和搜索频率
     */
    @Query("""
        SELECT * FROM search_history 
        WHERE query LIKE :pattern AND searchType = :searchType
        ORDER BY searchCount DESC, timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getSearchSuggestions(pattern: String, searchType: String, limit: Int = 5): List<SearchHistoryEntity>
    
    /**
     * 切换收藏状态
     */
    @Query("UPDATE search_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean): Int
    
    /**
     * 删除指定搜索历史
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchHistory(id: Long): Int
    
    /**
     * 删除指定类型的搜索历史
     */
    @Query("DELETE FROM search_history WHERE searchType = :searchType")
    suspend fun deleteSearchHistoryByType(searchType: String): Int
    
    /**
     * 清空所有搜索历史（保留收藏）
     */
    @Query("DELETE FROM search_history WHERE isFavorite = 0")
    suspend fun clearNonFavoriteHistory(): Int
    
    /**
     * 清空所有搜索历史
     */
    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory(): Int
    
    /**
     * 删除旧记录（保留最近N条和收藏）
     * 
     * @param keepCount 保留的最近记录数
     */
    @Query("""
        DELETE FROM search_history 
        WHERE isFavorite = 0 
        AND id NOT IN (
            SELECT id FROM search_history 
            WHERE isFavorite = 0 
            ORDER BY timestamp DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldHistory(keepCount: Int = 100): Int
    
    /**
     * 获取搜索历史统计
     */
    @Query("""
        SELECT 
            COUNT(*) as totalSearches,
            COUNT(DISTINCT query) as uniqueQueries,
            AVG(CAST(resultCount AS REAL)) as avgResultCount
        FROM search_history
    """)
    suspend fun getSearchHistoryStats(): SearchHistoryStatsRaw
    
    /**
     * 获取总搜索次数（累加所有searchCount）
     */
    @Query("SELECT SUM(searchCount) FROM search_history")
    suspend fun getTotalSearchCount(): Int?
}

/**
 * 搜索历史统计原始数据
 */
data class SearchHistoryStatsRaw(
    val totalSearches: Int,
    val uniqueQueries: Int,
    val avgResultCount: Float
)
