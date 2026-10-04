package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.queue.RefreshPriority
import org.njarasoa.fijerena.core.network.queue.RefreshQueue
import org.njarasoa.fijerena.core.network.queue.RefreshTask
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamContentManager
import org.njarasoa.fijerena.core.network.xtream.manager.XtreamSessionManager
import org.njarasoa.fijerena.core.player.api.XtreamApiService
import java.io.IOException

/**
 * A failed catalogue task must reach the caller as a failed sync, never as Success
 * (docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-08).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSyncFailureTest {
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
    fun aFailedTaskDoesNotStopTheOthersAndIsReturned() =
        runTest {
            val failure = IOException("connection reset")
            val failed = CompletableDeferred<Unit>().apply { completeExceptionally(failure) }
            val late = CompletableDeferred<Unit>()

            val result = async { awaitCatalogTasks(listOf(failed, late, CompletableDeferred(Unit))) }
            testScheduler.runCurrent()
            assertFalse("must keep waiting for the remaining tasks", result.isCompleted)
            late.complete(Unit)

            // await() may rethrow a stack-trace-recovered copy, so compare by type and message.
            val failures = result.await()
            assertEquals(1, failures.size)
            assertTrue(failures.single() is IOException)
            assertEquals(failure.message, failures.single().message)
        }

    @Test
    fun aCancelledTaskIsRethrownNotCollected() =
        runTest {
            val cancelled = CompletableDeferred<Unit>().apply { cancel(CancellationException("queue cleared")) }

            try {
                val failures = awaitCatalogTasks(listOf(CompletableDeferred(Unit), cancelled))
                fail("expected CancellationException but got $failures")
            } catch (_: CancellationException) {
                // expected
            }
        }

    @Test
    fun aRefreshQueueTaskThatThrowsIsCollected() =
        runBlocking {
            val task =
                object : RefreshTask {
                    override val id = "catalog_sync_failure_test"
                    override val priority = RefreshPriority.LOW

                    override suspend fun execute(): Unit = throw IOException("cut mid get_vod_streams")
                }

            val failures = withTimeout(5_000) { awaitCatalogTasks(listOf(RefreshQueue.submit(task))) }

            assertEquals(1, failures.size)
            assertTrue(failures.single() is IOException)
        }

    @Test
    fun aCategoryDownloadCutMidwayFailsItsTask() =
        runBlocking {
            val api = mockk<XtreamApiService>()
            coEvery { api.getCategories() } throws IOException("connection reset")
            val session = mockk<XtreamSessionManager>()
            every { session.apiService } returns api
            val manager =
                XtreamContentManager(
                    session,
                    mockk(relaxed = true),
                    FakeSharedPreferences(),
                    ProviderSettings(),
                    mockk(relaxed = true),
                    provider.id,
                )

            val failures = withTimeout(5_000) { awaitCatalogTasks(listOf(manager.syncCategories(XtreamCategoryEntity.TYPE_LIVE))) }

            assertEquals(1, failures.size)
            assertTrue(failures.single() is IOException)
        }

    @Test
    fun syncAllThrowsWhenAnyTaskFailed() =
        runTest {
            val repository = mockk<XtreamRepository>(relaxed = true)
            coEvery { repository.syncCategories(any()) } returns CompletableDeferred(Unit)
            coEvery { repository.syncStreams(any()) } returns CompletableDeferred(Unit)
            coEvery { repository.syncStreams(XtreamStreamEntity.TYPE_VOD) } returns
                CompletableDeferred<Unit>().apply { completeExceptionally(IOException("cut mid get_vod_streams")) }
            coEvery { repository.syncSeries() } returns CompletableDeferred(Unit)
            every { repository.consumeSyncDelta() } returns SyncDelta(inserted = 3)

            try {
                val delta = XtreamMediaProvider(provider.id, repository, mockk<TmdbApiService>()).syncAll()
                fail("expected CatalogSyncException but got $delta")
            } catch (e: CatalogSyncException) {
                assertEquals(1, e.failures.size)
                assertTrue(e.failures.single() is IOException)
            }
            // What did arrive is still filtered by the exclusion pass.
            coVerify { repository.recomputeExclusions() }
        }

    @Test
    fun aNetworkFailureMidSyncIsTransientAndRetriedInRun() =
        runTest {
            val mediaProvider = connectedProvider { throw CatalogSyncException(listOf(IOException("connection reset"))) }

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")

            assertTrue("got $outcome", outcome is ProviderSyncRunner.Outcome.Transient)
            coVerify(exactly = 3) { mediaProvider.syncAll() }
        }

    @Test
    fun anAuthRejectionMidSyncIsPermanent() =
        runTest {
            // Ktor's ClientRequestException (expectSuccess) is an IllegalStateException carrying the status.
            connectedProvider {
                throw CatalogSyncException(listOf(IllegalStateException("Client request(GET get_vod_streams) invalid: 403 Forbidden")))
            }

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")

            assertTrue("got $outcome", outcome is ProviderSyncRunner.Outcome.Permanent)
        }

    @Test
    fun aMixOfFailuresIsTransientWhenAnyIsTransient() =
        runTest {
            connectedProvider {
                throw CatalogSyncException(listOf(IllegalStateException("403 Forbidden"), IOException("timeout")))
            }

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")

            assertTrue("got $outcome", outcome is ProviderSyncRunner.Outcome.Transient)
        }

    @Test
    fun aCleanRunStillReportsSuccessWithItsDelta() =
        runTest {
            mockkObject(XtreamDatabase.Companion)
            every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
            val delta = SyncDelta(inserted = 2, updated = 1, deleted = 0)
            connectedProvider { delta }

            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")

            assertEquals(ProviderSyncRunner.Outcome.Success(delta), outcome)
        }
}
