package com.example.inventory.data.repository

import com.example.inventory.data.db.SearchHistoryDao
import com.example.inventory.data.db.SearchHistoryStatsRaw
import com.example.inventory.data.model.SearchHistoryEntity
import com.example.inventory.data.model.SearchSuggestion
import com.example.inventory.data.model.SuggestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryRepositoryTest {

    @Mock
    private lateinit var mockDao: SearchHistoryDao

    private lateinit var repository: SearchHistoryRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        repository = SearchHistoryRepositoryImpl(mockDao)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `recordSearch inserts new history when not exists`() = runTest {
        val query = "test"
        val type = "text"
        whenever(mockDao.getSearchHistoryByQuery(query, type)).thenReturn(null)
        whenever(mockDao.insertSearchHistory(any())).thenReturn(1L)
        whenever(mockDao.getSearchHistoryCount()).thenReturn(0)

        repository.recordSearch(query, type, 10)

        verify(mockDao).insertSearchHistory(argThat { history ->
            history.query == query && history.searchType == type && history.searchCount == 1 && history.resultCount == 10
        })
    }

    @Test
    fun `recordSearch updates existing history`() = runTest {
        val query = "test"
        val type = "text"
        val existing = SearchHistoryEntity(
            id = 1,
            query = query,
            searchType = type,
            searchCount = 5,
            resultCount = 8,
            timestamp = 1000
        )
        whenever(mockDao.getSearchHistoryByQuery(query, type)).thenReturn(existing)
        whenever(mockDao.insertSearchHistory(any())).thenReturn(1L)

        repository.recordSearch(query, type, 20)

        verify(mockDao).insertSearchHistory(argThat { history ->
            history.id == 1L && history.searchCount == 6 && history.resultCount == 20 && history.timestamp > 1000
        })
    }

    @Test
    fun `getSearchStats returns correct stats`() = runTest {
        val rawStats = SearchHistoryStatsRaw(100, 50, 5.5f)
        val topQueries = listOf(SearchHistoryEntity(query = "top", searchType = "text", resultCount = 1))
        
        whenever(mockDao.getSearchHistoryStats()).thenReturn(rawStats)
        whenever(mockDao.getPopularSearches(10)).thenReturn(topQueries)
        whenever(mockDao.getTotalSearchCount()).thenReturn(100)

        val stats = repository.getSearchStats()

        assertEquals(100, stats.totalSearches)
        assertEquals(50, stats.uniqueQueries)
        assertEquals(5.5f, stats.averageResultCount, 0.01f)
        assertEquals(topQueries, stats.topQueries)
    }

    @Test
    fun `getSearchSuggestions returns matching suggestions`() = runTest {
        val query = "test"
        val type = "text"
        val history = listOf(
            SearchHistoryEntity(query = "test", searchType = type, searchCount = 3),
            SearchHistoryEntity(query = "test2", searchType = type, searchCount = 1)
        )
        whenever(mockDao.getSearchSuggestions("%test%", type, 5)).thenReturn(history)

        val result = repository.getSearchSuggestions(query, type, 5)

        val expected = listOf(
            SearchSuggestion("test", SuggestionType.HISTORY, 3),
            SearchSuggestion("test2", SuggestionType.AUTOCOMPLETE, 1)
        )
        assertEquals(expected, result)
        verify(mockDao).getSearchSuggestions("%test%", type, 5)
    }

    @Test
    fun `toggleFavorite updates favorite status`() = runTest {
        val id = 1L
        val isFavorite = true
        whenever(mockDao.updateFavoriteStatus(id, isFavorite)).thenReturn(1)

        val result = repository.toggleFavorite(id, isFavorite)

        assertTrue(result)
        verify(mockDao).updateFavoriteStatus(id, isFavorite)
    }

    @Test
    fun `cleanupOldHistory delegates to dao`() = runTest {
        whenever(mockDao.deleteOldHistory(100)).thenReturn(5)

        val result = repository.cleanupOldHistory(100)

        assertEquals(5, result)
        verify(mockDao).deleteOldHistory(100)
    }
}
