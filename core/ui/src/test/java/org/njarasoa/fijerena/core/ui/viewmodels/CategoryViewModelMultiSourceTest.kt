package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.njarasoa.fijerena.core.network.RecentCategory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * Merged browsing (docs/plans/20261012_multi-source-search-history-plan.md → Phase 6, part 1): a
 * section lists every Xtream source's categories, a category opens on its own source, and per-row
 * state is keyed by source and id, so the same id on two sources never shows the other's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelMultiSourceTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val repoA = repo(A)
    private val repoB = repo(B)

    private fun repo(source: Long): MediaRepository =
        mockk<MediaRepository>(relaxed = true).also {
            every { it.getProviderId() } returns source
            every { it.isConnected() } returns true
            every { it.recentItems(any()) } returns MutableStateFlow(emptyList())
        }

    private fun category(id: String) = MediaCategory(id = id, name = "Cat $id")

    private fun movie(id: String) = MediaItem(id = id, name = "Film $id", mediaType = MediaType.MOVIE, categoryId = "c1")

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        every { container.providerRepository } returns providers
        coEvery { providers.profileSourcesList(any()) } returns
            listOf(
                ProviderEntity(id = A, name = "Alpha", url = "", username = ""),
                ProviderEntity(id = J, name = "Jelly", url = "", username = "", type = "JELLYFIN"),
                ProviderEntity(id = B, name = "Beta", url = "", username = ""),
            )
        coEvery { container.getMediaRepository(A) } returns repoA
        coEvery { container.getStoredDataRepository(B) } returns repoB
        // The same category id, and the same film id, on both sources.
        coEvery { repoA.getFilteredCategories(ContentType.MOVIES) } returns Result.success(listOf(category("c1"), category("c2")))
        coEvery { repoB.getFilteredCategories(ContentType.MOVIES) } returns Result.success(listOf(category("c1")))
        coEvery { repoA.getItems("c1", ContentType.MOVIES) } returns Result.success(listOf(movie("1")))
        coEvery { repoB.getItems("c1", ContentType.MOVIES) } returns Result.success(listOf(movie("1")))
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun section(): CategoryViewModel = CategoryViewModelFactory(context, ContentType.MOVIES).create(CategoryViewModel::class.java)

    private fun <T> await(read: suspend () -> T): T = runBlocking { withTimeout(TIMEOUT_MS) { read() } }

    private fun CategoryViewModel.success(predicate: (CategoryViewModel.UiState.Success) -> Boolean) =
        await { uiState.first { it is CategoryViewModel.UiState.Success && predicate(it) } as CategoryViewModel.UiState.Success }

    @Test
    fun `a section lists every Xtream source's categories by source, keyed by source and id`() {
        val state = section().success { it.categories.any { category -> !category.isVirtual } }
        val regular = state.categories.filterNot { it.isVirtual }

        // The profile's order (Alpha, then Beta; the Jellyfin source is browsed on its own), then
        // each source's own order.
        assertEquals(listOf(A to "c1", A to "c2", B to "c1"), regular.map { it.providerId to it.id })
        assertEquals(regular.size, regular.map { it.key }.toSet().size)
        assertEquals(listOf("$A/c1", "$A/c2", "$B/c1"), regular.map { it.key })
    }

    @Test
    fun `a category opens on its own source, its rows on that source`() {
        val vm = section()
        vm.success { it.categories.any { category -> !category.isVirtual } }

        vm.loadStreams("c1", B)
        val state = vm.success { it.selectedCategoryId == "c1" && !it.streamsLoading }

        assertEquals(B, state.selectedCategorySource)
        assertEquals("$B/c1", state.selectedCategoryKey)
        assertEquals(listOf(B), state.streams?.map { it.providerId })
        coVerify { repoB.getItems("c1", ContentType.MOVIES) }
        coVerify(exactly = 0) { repoA.getItems("c1", any()) }
        verify { repoB.addToCategoryHistory("c1", "Cat c1", ContentType.MOVIES) }
    }

    @Test
    fun `a row's star and watched check are its own source's`() {
        // Film 1 is a favourite on Beta and watched (a sibling) on Alpha.
        every { repoB.getFavoriteItemIds(ContentType.MOVIES) } returns setOf("1")
        every { repoA.getFavoriteItemIds(ContentType.MOVIES) } returns emptySet()
        coEvery { repoA.getSiblingCompletedMovieIds() } returns setOf("1")
        coEvery { repoB.getSiblingCompletedMovieIds() } returns emptySet()
        val vm = section()
        vm.success { it.categories.any { category -> !category.isVirtual } }

        vm.loadStreams("c1", B)
        vm.success { it.selectedCategorySource == B && !it.streamsLoading }
        val favorites = await { vm.favoriteIds.first { it.isNotEmpty() } }

        assertEquals(setOf("$B/1"), favorites)
        assertFalse("$A/1" in favorites)
        assertTrue(vm.watchedIds.value.none { it == "$B/1" })

        vm.loadStreams("c1", A)
        vm.success { it.selectedCategorySource == A && !it.streamsLoading }
        val watched = await { vm.watchedIds.first { it.isNotEmpty() } }
        assertEquals(setOf("$A/1"), watched)
        assertTrue(await { vm.favoriteIds.first { "$B/1" !in it } }.isEmpty())
    }

    @Test
    fun `favouriting a Recent Categories row favourites its category on its own source`() {
        every { repoB.getRecentlyViewedCategories(ContentType.MOVIES) } returns
            listOf(RecentCategory("c1", "Cat c1", ContentType.MOVIES, timestamp = 10))
        every { repoA.getRecentlyViewedCategories(ContentType.MOVIES) } returns
            listOf(RecentCategory("c1", "Cat c1", ContentType.MOVIES, timestamp = 5))
        val vm = section()
        vm.success { it.categories.any { category -> !category.isVirtual } }

        vm.loadStreams(CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID)
        val rows =
            vm.success { it.selectedCategoryId == CategoryViewModel.RECENTLY_VIEWED_CATEGORIES_ID && !it.streamsLoading }.streams.orEmpty()
        // One row per source, ids apart, newest first.
        assertEquals(listOf(B, A), rows.map { it.providerId })
        assertEquals(2, rows.map { it.id }.toSet().size)

        val row = rows.first()
        vm.toggleFavoriteStream(row.id, row.name, row.categoryId, ContentType.MOVIES, row.providerId)

        verify { repoB.addFavoriteCategory("c1", "Cat c1", ContentType.MOVIES) }
        verify(exactly = 0) { repoA.addFavoriteCategory(any(), any(), any()) }
        verify(exactly = 0) { repoB.addFavorite(any(), any(), any(), any()) }
    }

    private companion object {
        const val A = 1L
        const val B = 2L
        const val J = 3L
        const val TIMEOUT_MS = 5_000L
    }
}
