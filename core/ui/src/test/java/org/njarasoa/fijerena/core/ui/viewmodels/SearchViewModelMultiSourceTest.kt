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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * Search covers every Xtream source of the profile in use, one result per source, each tagged with
 * its source when there are several. See docs/plans/20261012_multi-source-search-history-plan.md →
 * Phase 3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelMultiSourceTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        every { container.providerRepository } returns providers
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `without Expert mode nothing says how many results the filters hide`() {
        givenSources(xtream(A, "Alpha"))
        source(A, items = listOf(movie("1", "Matrix")), category = "Sci-fi", excluded = 2)

        assertTrue(search("matrix").excludedCountByType.isEmpty())
    }

    @Test
    fun `results of two sources are merged and ranked together, each tagged with its source`() {
        givenSources(xtream(A, "Alpha"), xtream(B, "Beta"))
        source(A, items = listOf(movie("1", "Matrix Reloaded")), category = "Action Matrix", excluded = 2)
        source(B, items = listOf(movie("1", "Matrix")), category = "Sci-fi", excluded = 1)
        // "N hidden" is counted for an Expert only.
        every { prefs.getBoolean(match { it.startsWith("dev_mode") }, any()) } returns true

        val state = search("matrix")

        assertEquals(listOf("Matrix" to B, "Matrix Reloaded" to A), state.filteredResults.map { it.streamName to it.providerId })
        assertEquals(listOf("Beta", "Alpha"), state.filteredResults.map { it.sourceName })
        assertEquals(listOf(A), state.categoryResults.map { it.providerId })
        assertEquals("Alpha", state.categoryResults.single().sourceName)
        assertEquals(mapOf(ContentType.MOVIES to 3), state.excludedCountByType)
        assertTrue(state.sourceErrors.isEmpty())
    }

    @Test
    fun `the Guide tab shows once a local source gets a playlist`() {
        val rows = MutableStateFlow(listOf(local(A, config = "")))
        every { providers.profileSources(any()) } returns rows
        val viewModel = SearchViewModel(context, ContentType.MOVIES)
        assertFalse(viewModel.hasGuide.value)

        // Only the row's config changes: the same source, still the profile's only one.
        rows.value = listOf(local(A, config = """{"m3uPath":"/sdcard/tv.m3u"}"""))

        assertTrue(viewModel.hasGuide.value)
    }

    @Test
    fun `a source that searches locally finds its titles without signing in`() {
        givenSources(xtream(A, "Alpha"))
        val offline = source(A, items = listOf(movie("1", "Matrix")))
        every { offline.isConnected() } returns false
        every { offline.getCapabilities() } returns
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.MOVIES),
                supportsEpg = false,
                supportsSearch = true,
                supportsAuthentication = true,
                supportsProgressSync = false,
                searchesLocally = true,
            )

        val state = search("matrix")

        assertEquals(listOf("Matrix"), state.filteredResults.map { it.streamName })
        assertTrue(state.sourceErrors.isEmpty())
        coVerify(exactly = 0) { offline.connect() }
    }

    @Test
    fun `a source whose search fails is left out`() {
        givenSources(xtream(A, "Alpha"), xtream(B, "Beta"))
        source(A, items = listOf(movie("1", "Matrix")))
        val failing = source(B, items = emptyList())
        coEvery { failing.search(any(), any(), any()) } throws IllegalStateException("boom")

        val state = search("matrix")

        assertEquals(listOf(A), state.filteredResults.map { it.providerId })
        assertEquals(listOf("[dev] Beta: boom"), state.sourceErrors)
    }

    @Test
    fun `when every source fails the error shows`() {
        givenSources(xtream(A, "Alpha"))
        val failing = source(A, items = emptyList())
        coEvery { failing.search(any(), any(), any()) } throws IllegalStateException("boom")

        val viewModel = SearchViewModel(context, ContentType.MOVIES)
        viewModel.performSearch("matrix")

        runBlocking { withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it is SearchViewModel.UiState.Error } } }
    }

    @Test
    fun `the same item on two sources has distinct keys`() {
        givenSources(xtream(A, "Alpha"), xtream(B, "Beta"))
        source(A, items = listOf(movie("1", "Matrix")), category = "Matrix")
        source(B, items = listOf(movie("1", "Matrix")), category = "Matrix")

        val state = search("matrix")

        assertEquals(2, state.filteredResults.size)
        assertNotEquals(state.filteredResults[0].listKey, state.filteredResults[1].listKey)
        assertNotEquals(state.categoryResults[0].listKey, state.categoryResults[1].listKey)
    }

    @Test
    fun `a single source shows no source badge`() {
        givenSources(xtream(A, "Alpha"))
        source(A, items = listOf(movie("1", "Matrix")), category = "Matrix")

        val state = search("matrix")

        assertEquals(A, state.filteredResults.single().providerId)
        assertNull(state.filteredResults.single().sourceName)
        assertNull(state.categoryResults.single().sourceName)
    }

    @Test
    fun `a Jellyfin or M3U source is left out while the profile has an Xtream one`() {
        givenSources(ProviderEntity(id = A, name = "Jelly", url = "", username = "", type = "JELLYFIN"), xtream(B, "Beta"))
        source(A, items = listOf(movie("1", "Matrix")))
        source(B, items = listOf(movie("2", "Matrix")))

        val state = search("matrix")

        assertEquals(listOf(B), state.filteredResults.map { it.providerId })
        coVerify(exactly = 0) { container.getStoredDataRepository(A) }
    }

    @Test
    fun `a profile with no Xtream source searches its other sources`() {
        givenSources(ProviderEntity(id = A, name = "Jelly", url = "", username = "", type = "JELLYFIN"))
        source(A, items = listOf(movie("1", "Matrix")))

        val state = search("matrix")

        assertEquals(listOf(A), state.filteredResults.map { it.providerId })
        assertNull(state.filteredResults.single().sourceName)
    }

    @Test
    fun `a result's favourite uses its own source`() {
        givenSources(xtream(A, "Alpha"), xtream(B, "Beta"))
        val alpha = source(A, items = emptyList())
        val beta = source(B, items = emptyList())

        val viewModel = SearchViewModel(context, ContentType.MOVIES)
        viewModel.toggleFavorite(B, "1", "Matrix", "c", ContentType.MOVIES, isFavorite = false)

        coVerify(timeout = TIMEOUT_MS) { beta.addFavorite("1", "Matrix", "c", ContentType.MOVIES) }
        coVerify(exactly = 0) { alpha.addFavorite(any(), any(), any(), any()) }
    }

    @Test
    fun `a film result's menu offers Mark watched, read on its own source`() {
        givenSources(xtream(A, "Alpha"), xtream(B, "Beta"))
        val alpha = source(A, items = emptyList())
        val beta = source(B, items = emptyList())
        every { beta.supportsMarkWatched } returns true
        coEvery { beta.getPlaybackPositionSuspend("1", ContentType.MOVIES) } returns null
        val result = SearchViewModel.SearchResult("1", "Matrix", "c", "Films", ContentType.MOVIES, providerId = B, sourceName = "Beta")

        val target = runBlocking { withTimeout(TIMEOUT_MS) { SearchViewModel(context, ContentType.MOVIES).menuTarget(result) } }

        assertEquals(false, target.isWatched)
        assertNull(target.isShowWatched)
        coVerify(exactly = 0) { alpha.getPlaybackPositionSuspend(any(), any()) }
    }

    private fun search(query: String): SearchViewModel.UiState.Success {
        val viewModel = SearchViewModel(context, ContentType.MOVIES)
        viewModel.performSearch(query)
        return runBlocking {
            withTimeout(TIMEOUT_MS) {
                viewModel.uiState.first { it is SearchViewModel.UiState.Success && it.query == query } as SearchViewModel.UiState.Success
            }
        }
    }

    private fun givenSources(vararg sources: ProviderEntity) {
        coEvery { providers.profileSourcesList(any()) } returns sources.toList()
    }

    private fun xtream(
        id: Long,
        name: String,
    ) = ProviderEntity(id = id, name = name, url = "", username = "", type = "XTREAM")

    private fun local(
        id: Long,
        config: String,
    ) = ProviderEntity(id = id, name = "Local", url = "", username = "", type = "LOCAL", config = config)

    private fun movie(
        id: String,
        name: String,
    ) = MediaItem(id = id, name = name, mediaType = MediaType.MOVIE, categoryId = "c")

    /**
     * A source's repository: [items] in its cached catalogue (its own search answers nothing, so the
     * catalogue is scanned — mockk can't stub `search`'s nullable `Result`), in one category named
     * [category], and [excluded] hidden matches.
     */
    private fun source(
        id: Long,
        items: List<MediaItem>,
        category: String = "Films",
        excluded: Int = 0,
    ): MediaRepository {
        val repo = mockk<MediaRepository>(relaxed = true)
        every { repo.isConnected() } returns true
        every { repo.getCapabilities() } returns null
        coEvery { repo.getFilteredCategories(any()) } returns Result.success(listOf(MediaCategory("c", category)))
        coEvery { repo.getCategories(any()) } returns Result.success(listOf(MediaCategory("c", category)))
        coEvery { repo.countExcludedSearchMatches(any(), any()) } returns excluded
        coEvery { repo.search(any(), any(), any()) } returns null
        every { repo.getItemsIfCached("c", ContentType.MOVIES) } returns items
        coEvery { container.getStoredDataRepository(id) } returns repo
        return repo
    }

    private companion object {
        const val A = 1L
        const val B = 2L
        const val TIMEOUT_MS = 5_000L
    }
}
