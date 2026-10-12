package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * A screen opened on a route's source reads that source's repository; 0 still means the active
 * one. See docs/plans/20261012_multi-source-search-history-plan.md → Phase 1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteSourceViewModelTest {
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
    fun `movie details read the route's source`() {
        create<MovieDetailsViewModel>(MovieDetailsViewModelFactory(context, "m1", "c1", "Film", providerId = SOURCE))
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(SOURCE) }
    }

    @Test
    fun `movie details with no source read the active one`() {
        create<MovieDetailsViewModel>(MovieDetailsViewModelFactory(context, "m1", "c1", "Film"))
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(0L) }
    }

    @Test
    fun `episodes read the route's source`() {
        create<SeriesDetailsViewModel>(SeriesDetailsViewModelFactory(context, "s1", "c1", "Show", providerId = SOURCE))
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(SOURCE) }
    }

    @Test
    fun `the player reads the route's source`() {
        create<StreamLoaderViewModel>(
            StreamLoaderViewModelFactory(context, "v1", "Film", "c1", ContentType.MOVIES, providerId = SOURCE),
        )
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(SOURCE) }
    }

    @Test
    fun `a category list reads the route's source`() {
        create<CategoryViewModel>(CategoryViewModelFactory(context, ContentType.MOVIES, providerId = SOURCE))
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(SOURCE) }
    }

    @Test
    fun `the guide reads the route's source`() {
        create<EpgViewModel>(EpgViewModelFactory(context, "c1", providerId = SOURCE))
        coVerify(timeout = TIMEOUT_MS) { container.getMediaRepository(SOURCE) }
    }

    private inline fun <reified T : ViewModel> create(factory: ViewModelProvider.Factory): T = factory.create(T::class.java)

    private companion object {
        const val SOURCE = 7L
        const val TIMEOUT_MS = 5_000L
    }
}
