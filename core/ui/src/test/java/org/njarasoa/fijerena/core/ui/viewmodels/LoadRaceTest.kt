package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.ui.di.AppContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A slow load or a slow favourite write that ends after the screen moved on (a switch to another
 * copy, another channel) must not write its old data over the new screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadRaceTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val repository = mockk<MediaRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        coEvery { container.getMediaRepository(any()) } returns repository
        coEvery { container.providerRepository.getProviderById(any()) } returns null
        every { repository.getProviderId() } returns SOURCE
        coEvery { repository.getFilteredCategories(any()) } returns Result.success(emptyList())
        coEvery { repository.getCachedSeriesDetail(any()) } returns null
        coEvery { repository.getPlaybackPositionSuspend(any(), any()) } returns null
        coEvery { repository.getSiblingCompletedMovieIds() } returns emptySet()
        coEvery { repository.getSeriesTrackPrefs(any(), any()) } returns null
        coEvery { repository.resolvePlayableStream(any(), any(), any(), any()) } answers
            { Result.success(PlayableStream(uri = "http://x/${firstArg<String>()}")) }
        coEvery { repository.getMovieDetail(any()) } answers { Result.success(MovieDetail(id = firstArg(), name = firstArg())) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `a show's slow load cancelled by a switch never publishes its episodes`() {
        val slowStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        coEvery { repository.getSeriesDetail(SeriesId("s1")) } coAnswers {
            slowStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (e: CancellationException) {
                slowCancelled.complete(Unit)
                throw e
            }
            Result.success(SeriesDetail(id = "s1", name = "Old"))
        }
        coEvery { repository.getSeriesDetail(SeriesId("s3")) } returns Result.success(SeriesDetail(id = "s3", name = "New"))
        val viewModel = SeriesDetailsViewModelFactory(context, "s1", "c1", "Show", SOURCE).create(SeriesDetailsViewModel::class.java)
        runBlocking { withTimeout(TIMEOUT_MS) { slowStarted.await() } }

        viewModel.switchToAlternateStream(copy("s3", MediaType.SERIES))

        runBlocking { withTimeout(TIMEOUT_MS) { slowCancelled.await() } }
        val shown = awaitState(viewModel.uiState) { it is SeriesDetailsViewModel.UiState.Success }
        assertEquals("s3", (shown as SeriesDetailsViewModel.UiState.Success).seriesDetail.id)
        assertEquals("c", shown.categoryId)
    }

    @Test
    fun `a film's favourite that ends after a switch leaves the new copy alone`() {
        val inWrite = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { repository.addFavorite("m1", any(), any(), any()) } answers {
            inWrite.countDown()
            release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        val viewModel = MovieDetailsViewModelFactory(context, "m1", "c1", "Film", SOURCE).create(MovieDetailsViewModel::class.java)
        awaitState(viewModel.uiState) { it is MovieDetailsViewModel.UiState.Success }

        viewModel.toggleFavorite("Film")
        assertTrue(inWrite.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        viewModel.switchToAlternateStream(copy("m3", MediaType.MOVIE))
        awaitState(viewModel.uiState) { (it as? MovieDetailsViewModel.UiState.Success)?.movieDetail?.id == "m3" }
        release.countDown()
        Thread.sleep(SETTLE_MS)

        val shown = viewModel.uiState.value as MovieDetailsViewModel.UiState.Success
        assertEquals("m3", shown.movieDetail.id)
        assertFalse(shown.isFavorite)
    }

    @Test
    fun `the player's favourite that ends after a channel change leaves the new channel alone`() {
        val inWrite = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { repository.addFavoriteSuspend("a", any(), any(), any()) } coAnswers {
            inWrite.complete(Unit)
            release.await()
            true
        }
        val viewModel = loader("a")
        awaitState(viewModel.state) { it is StreamLoaderViewModel.StreamState.Success }

        viewModel.toggleFavorite()
        runBlocking { withTimeout(TIMEOUT_MS) { inWrite.await() } }
        viewModel.loadStream(copy("b", MediaType.MOVIE))
        awaitState(viewModel.state) { (it as? StreamLoaderViewModel.StreamState.Success)?.streamId == "b" }
        release.complete(Unit)
        Thread.sleep(SETTLE_MS)

        val shown = viewModel.state.value as StreamLoaderViewModel.StreamState.Success
        assertEquals("b", shown.streamId)
        assertFalse(shown.isFavorite)
    }

    @Test
    fun `a preview re-pointed before the repository is ready plays the new channel, not the first`() {
        val ready = CompletableDeferred<Unit>()
        coEvery { container.getMediaRepository(any()) } coAnswers {
            ready.await()
            repository
        }
        val viewModel = loader("a")

        viewModel.loadStreamLight(copy("b", MediaType.MOVIE))
        ready.complete(Unit)

        awaitState(viewModel.state) { (it as? StreamLoaderViewModel.StreamState.Success)?.streamId == "b" }
        coVerify(exactly = 0) { repository.resolvePlayableStream("a", any(), any(), any()) }
    }

    @Test
    fun `an abandoned loader whose first load never resolved loads its channel again on return`() {
        val ready = CompletableDeferred<Unit>()
        coEvery { container.getMediaRepository(any()) } coAnswers {
            ready.await()
            repository
        }
        val viewModel = loader("a")

        viewModel.abandon()
        ready.complete(Unit)

        assertNull(viewModel.requestedStreamId)
    }

    private fun loader(streamId: String): StreamLoaderViewModel =
        StreamLoaderViewModelFactory(context, streamId, "Film $streamId", "c1", ContentType.MOVIES, providerId = SOURCE)
            .create(StreamLoaderViewModel::class.java)

    private fun copy(
        id: String,
        type: MediaType,
    ) = MediaItem(id = id, name = "Copy $id", mediaType = type, categoryId = "c", providerId = SOURCE)

    private fun <T> awaitState(
        flow: StateFlow<T>,
        predicate: (T) -> Boolean,
    ): T = runBlocking { withTimeout(TIMEOUT_MS) { flow.first(predicate) } }

    private companion object {
        const val SOURCE = 7L
        const val TIMEOUT_MS = 5_000L

        // Time for a write that would wrongly land to land.
        const val SETTLE_MS = 300L
    }
}
