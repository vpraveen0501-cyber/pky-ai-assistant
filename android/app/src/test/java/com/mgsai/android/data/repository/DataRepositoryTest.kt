package com.pkyai.android.data.repository

import com.pkyai.android.SystemStats
import com.pkyai.android.data.HistoryDao
import com.pkyai.android.data.network.PkyAiApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DataRepositoryTest {

    private lateinit var apiService: PkyAiApiService
    private lateinit var historyDao: HistoryDao
    private lateinit var repository: DataRepository

    @Before
    fun setup() {
        apiService = mock()
        historyDao = mock()
        repository = DataRepository(apiService, historyDao)
    }

    @Test
    fun `getSystemStats returns success when api call is successful`() = runTest {
        val mockStats = SystemStats("Online", "1.0", "500MB", 100, "GPT-4", "Whisper", 3, "24h")
        whenever(apiService.getSystemStats()).thenReturn(Response.success(mockStats))

        val result = repository.getSystemStats()

        assertTrue(result.isSuccess)
        assertEquals(mockStats, result.getOrNull())
    }

    @Test
    fun `getSystemStats returns failure when api call fails`() = runTest {
        whenever(apiService.getSystemStats()).thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "")))

        val result = repository.getSystemStats()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to load stats: 500") == true)
    }
}
