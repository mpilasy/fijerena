package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamEpgManagerTest {
    private fun createManager(
        apiService: XtreamApiService,
        cachingEnabled: Boolean = false,
    ): XtreamEpgManager {
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
        val providerSettings = mockk<ProviderSettings>()
        val sessionManager = mockk<XtreamSessionManager>()

        every { sessionManager.apiService } returns apiService
        every { providerSettings.cachingEnabled } returns cachingEnabled

        return XtreamEpgManager(
            sessionManager = sessionManager,
            sharedPreferences = sharedPrefs,
            providerSettings = providerSettings,
            epgCacheDao = mockk(relaxed = true),
            providerId = 1L,
        )
    }

    @Test
    fun `getEpgForStreams runs in parallel`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val manager = createManager(apiService)

            val delayMs = 100L
            coEvery { apiService.getEpgForStream(any()) } coAnswers {
                delay(delayMs)
                EpgResponse(emptyList())
            }

            val streamIds = (1..10).toList()
            // Semaphore(10) allows all 10 to run concurrently.
            // Parallel: ~100ms + overhead. Sequential: ~1000ms.

            val time =
                measureTimeMillis {
                    manager.getEpgForStreams(streamIds)
                }

            println("Execution time: ${time}ms")
            assertTrue("Execution should be parallel (took ${time}ms)", time < 600)
        }

    @Test
    fun `getEpgForStreams returns correct results`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val manager = createManager(apiService)

            coEvery { apiService.getEpgForStream(1) } returns
                EpgResponse(
                    listOf(EpgProgram(id = "p1", title = "Program 1", start = "100", end = "200")),
                )
            coEvery { apiService.getEpgForStream(2) } returns
                EpgResponse(
                    listOf(EpgProgram(id = "p2", title = "Program 2", start = "100", end = "200")),
                )
            coEvery { apiService.getEpgForStream(3) } throws Exception("Network error")

            val result = manager.getEpgForStreams(listOf(1, 2, 3))

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals(2, data.size)
            assertEquals("Program 1", data[1]!!.listings[0].title)
            assertEquals("Program 2", data[2]!!.listings[0].title)
        }

    @Test
    fun `getEpgForStreams continues on individual stream failure`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val manager = createManager(apiService)

            coEvery { apiService.getEpgForStream(1) } returns EpgResponse(emptyList())
            coEvery { apiService.getEpgForStream(2) } throws Exception("Server error")
            coEvery { apiService.getEpgForStream(3) } returns EpgResponse(emptyList())

            val result = manager.getEpgForStreams(listOf(1, 2, 3))

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals(2, data.size)
            assertTrue(1 in data)
            assertTrue(3 in data)
        }

    @Test
    fun `getEpgForStreams with empty list returns empty map`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val manager = createManager(apiService)

            val result = manager.getEpgForStreams(emptyList())

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertTrue(data.isEmpty())
        }

    @Test
    fun `getEpgForStreams resolves API service once`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
            val providerSettings = mockk<ProviderSettings>()
            val sessionManager = mockk<XtreamSessionManager>()

            every { sessionManager.apiService } returns apiService
            every { providerSettings.cachingEnabled } returns false

            val manager =
                XtreamEpgManager(
                    sessionManager = sessionManager,
                    sharedPreferences = sharedPrefs,
                    providerSettings = providerSettings,
                    epgCacheDao = mockk(relaxed = true),
                    providerId = 1L,
                )

            coEvery { apiService.getEpgForStream(any()) } returns EpgResponse(emptyList())

            manager.getEpgForStreams(listOf(1, 2, 3))

            // API service property is read once (in getEpgForStreams), not per stream
            io.mockk.verify(exactly = 1) { sessionManager.apiService }
        }

    @Test
    fun `benchmark getEpgForStreams with 50 streams`() =
        runBlocking {
            val apiService = mockk<XtreamApiService>()
            val manager = createManager(apiService)

            val delayMs = 50L
            coEvery { apiService.getEpgForStream(any()) } coAnswers {
                delay(delayMs)
                EpgResponse(emptyList())
            }

            val streamIds = (1..50).toList()
            // 50 streams with Semaphore(10): 5 batches of ~50ms = ~250ms parallel
            // Sequential would be 50 * 50ms = 2500ms

            val time =
                measureTimeMillis {
                    manager.getEpgForStreams(streamIds)
                }

            println("BENCHMARK_RESULT: 50 streams took ${time}ms (sequential would be ~${50 * delayMs}ms)")
            assertTrue("Batch of 50 should complete within 1000ms (took ${time}ms)", time < 1000)
        }
}
