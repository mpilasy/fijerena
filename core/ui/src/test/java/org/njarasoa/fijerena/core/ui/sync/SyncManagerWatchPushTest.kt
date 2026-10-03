package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot
import org.njarasoa.fijerena.core.ui.sync.SyncManager.PendingPush

/**
 * R-18: a position save every 10 s while playing used to push every ~13 s. Watch progress alone is
 * now pushed at most once a minute while something plays, and promptly once it pauses or stops;
 * any other change keeps the 3 s debounce. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-18.
 */
class SyncManagerWatchPushTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler = dispatcher.scheduler
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val engine = mockk<SyncEngine>()
    private val nowPlaying = MutableStateFlow<NowPlayingSnapshot?>(PLAYING)
    private var pending = PendingPush.WATCH_ONLY

    /** Virtual times of the sync passes run after the foreground's catch-up pass. */
    private val passes = mutableListOf<Long>()
    private lateinit var manager: SyncManager

    @Before
    fun setup() {
        every { store.link } returns null
        every { engine.isLinked } returns true
        coEvery { engine.syncNow(any()) } answers {
            passes += scheduler.currentTime
            SyncEngine.Outcome(0, 0, 0, false)
        }
        manager =
            SyncManager(
                mockk<Application>(relaxed = true),
                scope,
                store,
                engine,
                mockk<SyncApi>(relaxed = true),
                nowPlaying = nowPlaying,
                pendingPush = { pending },
            )
        manager.watchPlayback()
        manager.onForeground()
        scheduler.runCurrent()
        passes.clear()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun advanceTo(ms: Long) {
        scheduler.advanceTimeBy(ms - scheduler.currentTime)
        scheduler.runCurrent()
    }

    /** A position save every 10 s from [from] until [until], as the player does. */
    private fun saveProgress(
        from: Long,
        until: Long,
    ) {
        for (at in from until until step 10_000L) {
            advanceTo(at)
            manager.onLocalChange()
            scheduler.runCurrent()
        }
    }

    @Test
    fun `progress saved while playing is pushed once a minute`() {
        saveProgress(from = 0, until = 130_000)
        advanceTo(130_000)

        assertEquals(listOf(60_000L, 120_000L), passes)
    }

    @Test
    fun `any other change is pushed after the usual debounce`() {
        saveProgress(from = 0, until = 30_000)
        pending = PendingPush.OTHER
        manager.onLocalChange()
        advanceTo(25_000)

        assertEquals(listOf(23_000L), passes)
        advanceTo(90_000)
        assertEquals("the progress went with it", listOf(23_000L), passes)
    }

    @Test
    fun `pausing pushes the held progress promptly`() {
        saveProgress(from = 0, until = 30_000)
        nowPlaying.value = PLAYING.copy(paused = true)
        scheduler.runCurrent()
        advanceTo(90_000)

        assertEquals(listOf(23_000L), passes)
    }

    @Test
    fun `stopping pushes the held progress promptly, and the final save too`() {
        saveProgress(from = 0, until = 30_000)
        nowPlaying.value = null
        scheduler.runCurrent()
        // The teardown save lands after the state change: still the short debounce.
        advanceTo(21_000)
        manager.onLocalChange()
        advanceTo(90_000)

        assertEquals(listOf(24_000L), passes)
    }

    @Test
    fun `progress written with nothing playing is pushed after the usual debounce`() {
        nowPlaying.value = null
        advanceTo(5_000)
        passes.clear()
        manager.onLocalChange()
        advanceTo(70_000)

        assertEquals(listOf(8_000L), passes)
    }

    private companion object {
        val PLAYING = NowPlayingSnapshot(title = "A film", paused = false)
    }
}
