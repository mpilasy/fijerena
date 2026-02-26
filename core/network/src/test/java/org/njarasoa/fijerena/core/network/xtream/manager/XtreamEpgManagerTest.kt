package org.njarasoa.fijerena.core.network.xtream.manager

import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamEpgManagerTest {

    @Test
    fun `getEpgForStreams runs in parallel`() = runBlocking {
        val apiService = mockk<XtreamApiService>()
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
        val providerSettings = mockk<ProviderSettings>()

        // Mock XtreamSessionManager
        val sessionManager = mockk<XtreamSessionManager>()
        every { sessionManager.apiService } returns apiService

        every { providerSettings.cachingEnabled } returns false

        val manager = XtreamEpgManager(
            sessionManager = sessionManager,
            sharedPreferences = sharedPrefs,
            providerSettings = providerSettings
        )

        // Mock API call with delay
        val delayMs = 100L
        coEvery { apiService.getEpgForStream(any()) } coAnswers {
            delay(delayMs)
            EpgResponse(emptyList()) // Return dummy response
        }

        val streamIds = (1..10).toList() // 10 items
        // XtreamEpgManager uses Semaphore(10), so 10 items should run in parallel.
        // If parallel: 100ms + overhead.
        // If sequential: 1000ms.

        val time = measureTimeMillis {
            manager.getEpgForStreams(streamIds)
        }

        println("Execution time: ${time}ms")

        // 600ms is generous for 100ms work. Sequential is 1000ms.
        assertTrue("Execution should be parallel (took ${time}ms)", time < 600)
    }
}
