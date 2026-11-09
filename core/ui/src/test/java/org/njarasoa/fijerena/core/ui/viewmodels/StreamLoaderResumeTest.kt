package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.model.EngineStream
import org.njarasoa.fijerena.core.ui.di.AppContainer

/** A Send to TV's position wins over the watch row; live has none (the send to TV plan, ST-D9). */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamLoaderResumeTest {
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

    private fun resumePosition(
        contentType: String,
        startPositionMs: Long?,
    ): Long =
        runBlocking {
            val loader =
                StreamLoaderViewModel(
                    context,
                    "a",
                    "Malcolm X",
                    "c1",
                    contentType,
                    providerId = 1L,
                    startPositionMs = startPositionMs,
                    engine = MutableStateFlow(EngineStream()),
                )
            val state =
                withTimeout(5_000) {
                    loader.state.first { it is StreamLoaderViewModel.StreamState.Success } as StreamLoaderViewModel.StreamState.Success
                }
            state.resumePosition
        }

    @Test
    fun `a film starts at the sent position`() {
        assertEquals(2_530_000L, resumePosition(ContentType.MOVIES, 2_530_000L))
    }

    @Test
    fun `a film without a sent position keeps the watch row's rule`() {
        assertEquals(0L, resumePosition(ContentType.MOVIES, null))
    }

    @Test
    fun `a channel has no sent position`() {
        assertEquals(0L, resumePosition(ContentType.LIVE_TV, 9_000L))
    }
}
