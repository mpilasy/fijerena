package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity

/**
 * A cancelled sync must surface as a CancellationException, never as an [ProviderSyncRunner.Outcome].
 * Every caller (ProviderSyncManager, XtreamSyncWorker) persists `outcome.errorOrNull()` through
 * `updateSyncStats`; with no Outcome there is nothing to persist, so a worker stop or timeout
 * cannot leave a permanent lastSyncError on the provider (round-2 plan, finding on cancellation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSyncRunnerCancellationTest {
    private val context = mockk<Context>(relaxed = true)
    private val provider = ProviderEntity(id = 7, name = "test", url = "http://example.invalid", username = "u")

    @Before
    fun setUp() {
        mockkObject(MediaProviderFactory)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun connectedProvider(syncAll: suspend () -> SyncDelta): XtreamMediaProvider =
        mockk<XtreamMediaProvider>().also {
            every { it.isConnected() } returns true
            coEvery { it.syncAll() } coAnswers { syncAll() }
            every { MediaProviderFactory.create(provider, context, "pw") } returns it
        }

    @Test
    fun cancellationThrownBySyncAllIsRethrownNotReturnedAsOutcome() =
        runTest {
            connectedProvider { throw CancellationException("worker stopped") }

            try {
                val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")
                fail("expected CancellationException but got $outcome")
            } catch (e: CancellationException) {
                assertEquals("worker stopped", e.message)
            }
        }

    @Test
    fun cancellationThrownByProviderCreationIsRethrown() =
        runTest {
            every { MediaProviderFactory.create(provider, context, "pw") } throws CancellationException("stopped")

            try {
                ProviderSyncRunner.syncProvider(context, provider, "pw")
                fail("expected CancellationException")
            } catch (_: CancellationException) {
                // expected
            }
        }

    @Test
    fun cancellingTheCallerMidSyncProducesNoOutcome() =
        runTest {
            val started = CompletableDeferred<Unit>()
            connectedProvider {
                started.complete(Unit)
                CompletableDeferred<SyncDelta>().await() // suspends until cancelled
            }

            var outcome: ProviderSyncRunner.Outcome? = null
            val job = launch { outcome = ProviderSyncRunner.syncProvider(context, provider, "pw") }
            started.await()
            job.cancel()
            job.join()

            assertTrue(job.isCancelled)
            assertNull("a cancelled sync must not yield an Outcome to persist", outcome)
        }

    @Test
    fun nonCancellationFailureStillYieldsPermanentOutcome() =
        runTest {
            connectedProvider { throw IllegalStateException("bad payload") }

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")

            assertTrue(outcome is ProviderSyncRunner.Outcome.Permanent)
        }
}
