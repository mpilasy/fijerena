package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * The Movies section's Wanted list (wanted titles plan, Phase 5): an entry beside Recent and
 * Favourites only while a film is wanted (never empty, W-D21), its rows the wanted films with
 * Available first and marked, and each row knowing its card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelWantedTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val repo =
        mockk<MediaRepository>(relaxed = true).also {
            every { it.getProviderId() } returns A
            every { it.isConnected() } returns true
            every { it.recentItems(any()) } returns MutableStateFlow(emptyList())
        }
    private val wantedState = MutableStateFlow(WantedTitles.State())
    private val wanted = mockk<WantedTitles>(relaxed = true).also { every { it.state } returns wantedState }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.getString(any()) } answers { "s${firstArg<Int>()}" }
        every { context.getString(any(), *anyVararg()) } answers { "s${firstArg<Int>()}" }
        mockkObject(AppContainer.Companion, WantedTitles.Companion)
        every { AppContainer.getInstance(any()) } returns container
        every { WantedTitles.getInstance(any()) } returns wanted
        every { container.providerRepository } returns providers
        coEvery { providers.profileSourcesList(any()) } returns listOf(ProviderEntity(id = A, name = "Alpha", url = "", username = ""))
        coEvery { container.getMediaRepository(A) } returns repo
        coEvery { container.getStoredDataRepository(A) } returns repo
        coEvery { repo.getFilteredCategories(ContentType.MOVIES) } returns Result.success(listOf(MediaCategory(id = "c1", name = "Cat")))
    }

    @After
    fun tearDown() {
        store.clear()
        await {
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .join()
            }
        }
        unmockkAll()
        Dispatchers.resetMain()
    }

    private val store = ViewModelStore()
    private val created = mutableListOf<CategoryViewModel>()

    private fun section(): CategoryViewModel =
        ViewModelProvider
            .create(store, CategoryViewModelFactory(context, ContentType.MOVIES))["section${created.size}", CategoryViewModel::class]
            .also { created += it }

    private fun <T> await(read: suspend () -> T): T = runBlocking { withTimeout(TIMEOUT_MS) { read() } }

    private fun CategoryViewModel.success(predicate: (CategoryViewModel.UiState.Success) -> Boolean) =
        await { uiState.first { it is CategoryViewModel.UiState.Success && predicate(it) } as CategoryViewModel.UiState.Success }

    private fun film(
        tmdbId: String,
        title: String,
    ) = WantedTitleEntity("p", ContentType.MOVIES, tmdbId, title, addedAt = 1)

    @Test
    fun `no Wanted entry while nothing is wanted`() {
        val state = section().success { it.categories.any { c -> c.id == "c1" } }

        assertFalse(state.categories.any { it.id == CategoryViewModel.WANTED_CATEGORY_ID })
    }

    @Test
    fun `the entry comes with a wanted film, and its rows put the available ones first`() {
        val vm = section()
        vm.success { it.categories.any { c -> c.id == "c1" } }

        wantedState.value =
            WantedTitles.State(
                titles = listOf(film("1", "Dune"), film("2", "Alien")),
                available = mapOf(WantedTitles.Key(ContentType.MOVIES, "2") to listOf(CatalogueHit(A, "42", "Alien", "c1", "2"))),
            )
        vm.success { it.categories.any { c -> c.id == CategoryViewModel.WANTED_CATEGORY_ID } }
        vm.loadStreams(CategoryViewModel.WANTED_CATEGORY_ID)
        val rows = vm.success { it.selectedCategoryId == CategoryViewModel.WANTED_CATEGORY_ID && !it.streams.isNullOrEmpty() }.streams!!

        assertEquals(listOf("Alien", "Dune"), rows.map { it.name })
        assertEquals("2", vm.wantedCardOf(rows.first())?.entity?.tmdbId)
        assertTrue(await { vm.rowStatus.first { it.size == 2 } }.isNotEmpty())
    }

    private companion object {
        const val A = 1L
        const val TIMEOUT_MS = 5_000L
    }
}
