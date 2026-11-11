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
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.model.EngineStream
import org.njarasoa.fijerena.core.ui.di.AppContainer

/** The source hears of a stop even when the local history write at the stop fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamLoaderStopTest {
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
        every { repository.getProviderId() } returns 1L
        every { repository.recentItems(any()) } returns MutableStateFlow(emptyList())
        coEvery { repository.getItems(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.getPlaybackPositionSuspend(any(), any()) } returns null
        coEvery { repository.resolvePlayableStream(any(), any(), any(), any()) } returns
            Result.success(PlayableStream(uri = "http://x/a.ts"))
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed history write still reports the stop to the source`() =
        runBlocking {
            every {
                repository.savePlaybackPosition(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws IllegalStateException("disk full")
            val loader =
                StreamLoaderViewModel(
                    context,
                    "a",
                    "Malcolm X",
                    "c1",
                    ContentType.MOVIES,
                    providerId = 1L,
                    engine = MutableStateFlow(EngineStream()),
                )
            withTimeout(5_000) { loader.state.first { it is StreamLoaderViewModel.StreamState.Success } }

            loader.stopPlaybackAwaited(position = 600_000L, duration = 6_000_000L)

            coVerify { repository.onPlaybackStopped("a", 600_000L, 6_000_000L) }
        }
}
