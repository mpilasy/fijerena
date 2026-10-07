package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity

/** [ProviderSyncRunner.running] names a source exactly while a sync of it runs, overlapping ones included. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSyncRunnerRunningTest {
    private val context = mockk<Context>(relaxed = true)
    private val provider = ProviderEntity(id = 9, name = "test", url = "http://example.invalid", username = "u")

    @Before
    fun setUp() {
        mockkObject(MediaProviderFactory)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun connectedProvider(syncAll: suspend () -> SyncDelta) {
        val mediaProvider = mockk<XtreamMediaProvider>()
        every { mediaProvider.isConnected() } returns true
        coEvery { mediaProvider.syncAll() } coAnswers { syncAll() }
        every { MediaProviderFactory.create(provider, context, "pw") } returns mediaProvider
    }

    @Test
    fun runningWhileSyncingAndClearedAfterAFailure() =
        runTest {
            val release = CompletableDeferred<Unit>()
            connectedProvider {
                release.await()
                throw IllegalStateException("bad payload")
            }

            val job = launch { ProviderSyncRunner.syncProvider(context, provider, "pw") }
            assertEquals(setOf(9L), ProviderSyncRunner.running.first { it.isNotEmpty() })
            release.complete(Unit)
            job.join()

            assertEquals(emptySet<Long>(), ProviderSyncRunner.running.first())
        }

    @Test
    fun overlappingSyncsKeepTheSourceRunningUntilTheLastEnds() =
        runTest {
            val started = CompletableDeferred<Unit>()
            connectedProvider {
                started.complete(Unit)
                CompletableDeferred<SyncDelta>().await() // suspends until cancelled
            }

            val first = launch { ProviderSyncRunner.syncProvider(context, provider, "pw") }
            val second = launch { ProviderSyncRunner.syncProvider(context, provider, "pw") }
            started.await()
            first.cancel()
            first.join()
            assertEquals(setOf(9L), ProviderSyncRunner.running.first())

            second.cancel()
            second.join()
            assertEquals(emptySet<Long>(), ProviderSyncRunner.running.first())
        }
}
