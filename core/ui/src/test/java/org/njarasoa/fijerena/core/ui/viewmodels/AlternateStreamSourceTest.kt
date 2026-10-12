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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.SeriesId
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * The Stream name picker's other copies of a title: one on the screen's source switches the screen
 * in place, as before; one on another source opens on that source (its route's), not on this one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlternateStreamSourceTest {
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
        every { repository.getProviderId() } returns SOURCE
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun copy(
        id: String,
        source: Long,
    ) = MediaItem(id = id, name = "Film $id", mediaType = MediaType.MOVIE, categoryId = "c", providerId = source)

    @Test
    fun `a film's copy on another source opens on that source, one on its own switches in place`() {
        val viewModel = MovieDetailsViewModelFactory(context, "m1", "c1", "Film", SOURCE).create(MovieDetailsViewModel::class.java)
        val elsewhere = copy("m2", OTHER)

        assertEquals(OTHER, viewModel.otherSourceOf(elsewhere))
        assertEquals(OTHER, elsewhere.sourceOr(SOURCE))
        viewModel.switchToAlternateStream(elsewhere)
        coVerify(exactly = 0) { repository.getMovieDetail("m2") }

        val here = copy("m3", SOURCE)
        assertNull(viewModel.otherSourceOf(here))
        viewModel.switchToAlternateStream(here)
        coVerify(timeout = TIMEOUT_MS) { repository.getMovieDetail("m3") }
    }

    @Test
    fun `a show's copy on another source opens on that source, one on its own switches in place`() {
        val viewModel = SeriesDetailsViewModelFactory(context, "s1", "c1", "Show", SOURCE).create(SeriesDetailsViewModel::class.java)
        val elsewhere = copy("s2", OTHER)

        assertEquals(OTHER, viewModel.otherSourceOf(elsewhere))
        viewModel.switchToAlternateStream(elsewhere)
        coVerify(exactly = 0) { repository.getCachedSeriesDetail(SeriesId("s2")) }

        val here = copy("s3", SOURCE)
        assertNull(viewModel.otherSourceOf(here))
        viewModel.switchToAlternateStream(here)
        coVerify(timeout = TIMEOUT_MS) { repository.getCachedSeriesDetail(SeriesId("s3")) }
    }

    @Test
    fun `after switching to a copy, Play records the copy's category and show, not the route's`() {
        val film = MovieDetailsViewModelFactory(context, "m1", "c1", "Film", SOURCE).create(MovieDetailsViewModel::class.java)
        assertEquals("c1", film.currentCategoryId)
        film.switchToAlternateStream(copy("m3", SOURCE))
        assertEquals("c", film.currentCategoryId)

        val show = SeriesDetailsViewModelFactory(context, "s1", "c1", "Show", SOURCE).create(SeriesDetailsViewModel::class.java)
        assertEquals(PlayedSeries("s1", "Show", "c1"), show.playedSeries())
        show.switchToAlternateStream(copy("s3", SOURCE))
        assertEquals(PlayedSeries("s3", "Film s3", "c"), show.playedSeries())
    }

    private companion object {
        const val SOURCE = 7L
        const val OTHER = 9L
        const val TIMEOUT_MS = 5_000L
    }
}
