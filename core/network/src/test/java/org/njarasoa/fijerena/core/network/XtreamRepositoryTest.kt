package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class XtreamRepositoryTest {

    @Test
    fun `getEpgForStreams runs in parallel`() = runBlocking {
        val apiService = mockk<XtreamApiService>()
        val database = mockk<XtreamDatabase>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val accountManager = mockk<AccountManager>(relaxed = true)

        // Mock SharedPreferences
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs

        // Mock AccountManager has credentials
        every { accountManager.hasStoredCredentials() } returns true

        val repository = XtreamRepository(
            accountManager = accountManager,
            context = context,
            database = database,
            apiService = apiService,
            providerSettings = ProviderSettings.DEFAULT
        )

        // Mock API call with delay
        val delayMs = 100L
        coEvery { apiService.getEpgForStream(any()) } coAnswers {
            delay(delayMs)
            EpgResponse(emptyList()) // Return dummy response
        }

        val streamIds = (1..10).toList() // 10 items
        // Batch size 5.
        // If parallel chunks: 2 batches * 100ms = 200ms.
        // If fully sequential: 10 * 100ms = 1000ms.

        val time = measureTimeMillis {
            repository.getEpgForStreams(streamIds)
        }

        println("Execution time: ${time}ms")

        // 600ms is generous for 200ms work. Sequential is 1000ms.
        // If it takes ~1000ms, then it's sequential.
        assertTrue("Execution should be parallel (took ${time}ms)", time < 600)
    }
}
