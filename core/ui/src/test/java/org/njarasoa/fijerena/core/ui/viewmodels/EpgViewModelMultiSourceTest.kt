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
import io.mockk.verify
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
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.GuideData
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import org.njarasoa.fijerena.core.ui.catchup.guideChannelRoute
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * The TV Guide of Recent or Favourites lists every Xtream source's channels of the profile, each
 * row on its own source: its listings, catch-up days and favourite from that source, and opening it
 * goes to that source. Before, it showed only the route's source's channels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgViewModelMultiSourceTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val providers = mockk<ProviderRepository>(relaxed = true)
    private val repoA = repo(A, "Alpha", recent = listOf(channel("1", A)))

    // The same channel id on the other source, and one of its own.
    private val repoB = repo(B, "Beta", recent = listOf(channel("1", B), channel("7", B)))

    private fun channel(
        id: String,
        source: Long,
    ) = MediaItem(id = id, name = "Channel $id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "c$source", providerId = source)

    private fun repo(
        source: Long,
        name: String,
        recent: List<MediaItem>,
    ): MediaRepository =
        mockk<MediaRepository>(relaxed = true).also { repo ->
            every { repo.getProviderId() } returns source
            every { repo.isConnected() } returns true
            coEvery { repo.hasGuideForSource() } returns true
            coEvery { repo.refreshRecentItems(ContentType.LIVE_TV) } returns recent
            coEvery { repo.getCatchupDays(any()) } returns if (source == B) mapOf("1" to 3) else emptyMap()
            // Each source answers for the channels asked of it, with a programme named after it, on now.
            coEvery { repo.getGuideForItemsInWindow(any(), any(), any()) } answers {
                val now = System.currentTimeMillis() / 1000
                val listings =
                    firstArg<List<MediaItem>>().associate { item ->
                        item.id to
                            EpgResponse(listOf(EpgProgram(id = "p${item.id}", title = "On $name", start = "$now", end = "${now + 3600}")))
                    }
                Result.success(GuideData(listings, GuideSource.XMLTV, 1L))
            }
        }

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns prefs
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        every { container.providerRepository } returns providers
        val sources =
            listOf(
                ProviderEntity(id = A, name = "Alpha", url = "", username = ""),
                ProviderEntity(id = B, name = "Beta", url = "", username = ""),
            )
        coEvery { providers.profileSourcesList(any()) } returns sources
        every { providers.profileSourceSet(any()) } returns MutableStateFlow(sources)
        // The guide opened on Alpha (Search the guide's TV Guide button: the first such source).
        coEvery { container.getMediaRepository(A) } returns repoA
        coEvery { container.getStoredDataRepository(B) } returns repoB
    }

    @After
    fun tearDown() {
        store.clear()
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                created.forEach {
                    it.viewModelScope.coroutineContext.job
                        .join()
                }
            }
        }
        unmockkAll()
        Dispatchers.resetMain()
    }

    private val store = ViewModelStore()
    private val created = mutableListOf<EpgViewModel>()

    private fun guide(categoryId: String): EpgViewModel =
        ViewModelProvider
            .create(store, EpgViewModelFactory(context, categoryId, A))["guide${created.size}", EpgViewModel::class]
            .also { created += it }

    private fun EpgViewModel.ready(): EpgViewModel.UiState.Ready =
        runBlocking { withTimeout(TIMEOUT_MS) { uiState.first { it is EpgViewModel.UiState.Ready } as EpgViewModel.UiState.Ready } }

    @Test
    fun `the Recent guide lists every source's channels, each with its own source's listings`() {
        val state = guide(CategoryViewModel.RECENT_CATEGORY_ID).ready()

        assertEquals(setOf(A to "1", B to "1", B to "7"), state.channelRows.map { it.channel.providerId to it.channel.id }.toSet())
        state.channelRows.forEach { row ->
            val expected = if (row.channel.providerId == A) "On Alpha" else "On Beta"
            assertEquals(listOf(expected), row.programs.map { it.title })
        }
        assertEquals(3, state.listedCount)
        // Catch-up days by row: Beta's channel 1 keeps an archive, Alpha's channel 1 doesn't.
        assertEquals(mapOf("$B/1" to 3), state.catchupDays)
        assertEquals(mapOf(A to "Alpha", B to "Beta"), state.sourceNames)
    }

    @Test
    fun `opening a row opens it on that row's own source`() {
        val rows = guide(CategoryViewModel.RECENT_CATEGORY_ID).ready().channelRows

        rows.forEach { row ->
            val route = guideChannelRoute(row.channel, guideSource = A)
            assertEquals(row.channel.providerId, route.providerId)
            assertEquals(row.channel.id, route.initialStreamId)
        }
        assertEquals(setOf(A, B), rows.map { guideChannelRoute(it.channel, guideSource = A).providerId }.toSet())
    }

    @Test
    fun `a row's favourite is toggled on its own source`() {
        val viewModel = guide(CategoryViewModel.RECENT_CATEGORY_ID)
        val betaRow =
            viewModel
                .ready()
                .channelRows
                .first { it.channel.providerId == B && it.channel.id == "1" }

        viewModel.toggleFavoriteChannel(betaRow.channel)

        verify { repoB.addFavorite("1", any(), any(), ContentType.LIVE_TV) }
        verify(exactly = 0) { repoA.addFavorite(any(), any(), any(), any()) }
    }

    @Test
    fun `a category's guide stays on its own source`() {
        coEvery { repoA.getItems("c1", ContentType.LIVE_TV) } returns Result.success(listOf(channel("1", 0L)))

        val state = guide("c1").ready()

        assertEquals(listOf(A to "1"), state.channelRows.map { it.channel.providerId to it.channel.id })
        assertEquals(emptyMap<Long, String>(), state.sourceNames)
    }

    private companion object {
        const val A = 1L
        const val B = 2L
        const val TIMEOUT_MS = 5_000L
    }
}
