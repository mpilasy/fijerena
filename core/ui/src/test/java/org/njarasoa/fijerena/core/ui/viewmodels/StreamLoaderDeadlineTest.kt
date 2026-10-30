package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.model.EngineStream
import org.njarasoa.fijerena.core.player.model.PlaybackState
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * A load has an overall time limit and ends in Error for its stream (UX audit fixes plan, finding
 * 3); a live channel goes into Recent only once the engine shows its picture (UX-D3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamLoaderDeadlineTest {
    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val container = mockk<AppContainer>(relaxed = true)
    private val repository = mockk<MediaRepository>(relaxed = true)
    private val engine = MutableStateFlow(EngineStream())

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
        every { repository.recentItems(any()) } returns MutableStateFlow(emptyList())
        coEvery { repository.getItems(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getPlaybackPositionSuspend(any(), any()) } returns null
        coEvery { repository.resolvePlayableStream(any(), any(), any(), any()) } answers
            { Result.success(PlayableStream(uri = url(firstArg()))) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `a stream that never resolves ends in Error for that stream`() {
        coEvery { repository.resolvePlayableStream("a", any(), any(), any()) } coAnswers { awaitCancellation() }
        val loader = loader("a")

        val error = awaitState(loader.state) { it is StreamLoaderViewModel.StreamState.Error }

        assertEquals("a", (error as StreamLoaderViewModel.StreamState.Error).streamId)
    }

    @Test
    fun `a repository that never comes ends the next load in Error for its stream`() {
        val never = CompletableDeferred<MediaRepository>()
        coEvery { container.getMediaRepository(any()) } coAnswers { never.await() }
        val loader = loader("a")

        loader.loadStream(channel("b"))

        val error = awaitState(loader.state) { (it as? StreamLoaderViewModel.StreamState.Error)?.streamId == "b" }
        assertEquals("b", (error as StreamLoaderViewModel.StreamState.Error).streamId)
    }

    @Test
    fun `a failed resolve names its stream`() {
        coEvery { repository.resolvePlayableStream("gone", any(), any(), any()) } returns
            Result.failure(NoSuchElementException("Item not found: gone"))
        val loader = loader("gone")

        val error = awaitState(loader.state) { it is StreamLoaderViewModel.StreamState.Error }

        assertEquals("gone", (error as StreamLoaderViewModel.StreamState.Error).streamId)
    }

    @Test
    fun `a live channel goes into Recent only after its picture`() {
        val loader = loader("a")
        awaitState(loader.state) { it is StreamLoaderViewModel.StreamState.Success }
        Thread.sleep(SETTLE_MS)
        verify(exactly = 0) { repository.saveLastPlayedItem(any(), "a", any(), any(), any(), any(), any(), any()) }

        engine.value = EngineStream(url("a"), PlaybackState.Buffering)
        Thread.sleep(SETTLE_MS)
        verify(exactly = 0) { repository.saveLastPlayedItem(any(), "a", any(), any(), any(), any(), any(), any()) }

        engine.value = EngineStream(url("a"), PlaybackState.Playing(0, 0))
        verify(timeout = TIMEOUT_MS) { repository.saveLastPlayedItem(any(), "a", any(), any(), any(), any(), any(), any()) }
    }

    private fun loader(streamId: String): StreamLoaderViewModel =
        StreamLoaderViewModel(
            context,
            streamId,
            "Channel $streamId",
            "c1",
            ContentType.LIVE_TV,
            providerId = SOURCE,
            engine = engine,
            loadTimeoutMs = LOAD_TIMEOUT_TEST_MS,
        )

    private fun channel(id: String) = MediaItem(id = id, name = "Channel $id", mediaType = MediaType.LIVE_CHANNEL, categoryId = "c1")

    private fun url(id: String) = "http://x/$id.ts"

    private fun <T> awaitState(
        flow: StateFlow<T>,
        predicate: (T) -> Boolean,
    ): T = runBlocking { withTimeout(TIMEOUT_MS) { flow.first(predicate) } }

    private companion object {
        const val SOURCE = 7L
        const val TIMEOUT_MS = 5_000L
        const val LOAD_TIMEOUT_TEST_MS = 300L

        // Time for a write that would wrongly land to land.
        const val SETTLE_MS = 300L
    }
}
