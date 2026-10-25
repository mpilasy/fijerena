package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.WatchedItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.ui.di.AppContainer

/** Back from the player, the details page shows the position the player just saved. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnFromPlayerTest {
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
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `the film page reads the new resume time on return`() =
        runBlocking {
            coEvery { repository.getMovieDetail(MOVIE) } returns Result.success(MovieDetail(id = MOVIE, name = "Film"))
            coEvery { repository.getFilteredCategories(any()) } returns Result.success(emptyList())
            coEvery { repository.getSiblingCompletedMovieIds() } returns emptySet()
            coEvery { repository.getPlaybackPositionSuspend(MOVIE, "MOVIES") } returns null
            val viewModel = MovieDetailsViewModelFactory(context, MOVIE, "c1", "Film").create(MovieDetailsViewModel::class.java)
            assertEquals(0L, awaitSuccess(viewModel).resumePositionMs)

            // The player saved a position, then Back.
            coEvery { repository.getPlaybackPositionSuspend(MOVIE, "MOVIES") } returns
                WatchedItem(MOVIE, "Film", "c1", "MOVIES", playbackPosition = POSITION_MS, duration = DURATION_MS)
            viewModel.refreshResumeState()

            withTimeout(TIMEOUT_MS) {
                while (awaitSuccess(viewModel).resumePositionMs != POSITION_MS) kotlinx.coroutines.delay(10)
            }
        }

    @Test
    fun `the player's awaited stop waits for the queued position write`() =
        runBlocking {
            val viewModel =
                StreamLoaderViewModelFactory(context, MOVIE, "Film", "c1", ContentType.MOVIES).create(StreamLoaderViewModel::class.java)
            coVerify(timeout = TIMEOUT_MS) { repository.getProviderId() }

            viewModel.stopPlaybackAwaited(POSITION_MS, DURATION_MS)

            coVerify { repository.awaitPendingWrites() }
        }

    private suspend fun awaitSuccess(viewModel: MovieDetailsViewModel): MovieDetailsViewModel.UiState.Success =
        withTimeout(TIMEOUT_MS) {
            var state = viewModel.uiState.value
            while (state !is MovieDetailsViewModel.UiState.Success) {
                kotlinx.coroutines.delay(10)
                state = viewModel.uiState.value
            }
            state
        }

    private companion object {
        const val MOVIE = "m1"
        const val POSITION_MS = 600_000L
        const val DURATION_MS = 3_600_000L
        const val TIMEOUT_MS = 5_000L
    }
}
