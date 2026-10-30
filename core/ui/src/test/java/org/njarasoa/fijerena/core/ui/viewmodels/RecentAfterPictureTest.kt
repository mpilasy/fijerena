package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EngineStream
import org.njarasoa.fijerena.core.player.model.PlaybackState

/**
 * A live channel goes into Recent only after it shows a picture, and the watch delay counts only
 * time with picture (UX audit fixes plan, UX-D3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentAfterPictureTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val recentWrite = RecentWrite(scope, dispatcher)
    private val engine = MutableStateFlow(EngineStream(OLD, PLAYING))
    private var writes = 0
    private val write: suspend () -> Unit = { writes++ }
    private val gate: suspend (Long) -> Boolean = { awaitPicture(engine, NEW, it) { scope.testScheduler.currentTime } }

    @Test
    fun `the delay starts at the first picture, not at the schedule`() {
        recentWrite.schedule(write, DELAY, gate = gate)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.advanceTimeBy(DELAY * 3)
        assertEquals(0, writes)

        engine.value = EngineStream(NEW, PLAYING)
        scope.advanceTimeBy(DELAY - 1)
        assertEquals(0, writes)
        scope.advanceTimeBy(2)
        assertEquals(1, writes)
    }

    @Test
    fun `a channel that never plays never goes into Recent`() {
        recentWrite.schedule(write, DELAY, gate = gate)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.advanceTimeBy(DELAY)
        engine.value = EngineStream(NEW, PlaybackState.Error("offline"))
        scope.advanceTimeBy(DELAY * 10)
        assertEquals(0, writes)
    }

    @Test
    fun `time without a picture does not count`() {
        recentWrite.schedule(write, DELAY, gate = gate)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.runCurrent()
        engine.value = EngineStream(NEW, PLAYING)
        scope.advanceTimeBy(DELAY / 2)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.advanceTimeBy(DELAY * 3)
        assertEquals(0, writes)

        engine.value = EngineStream(NEW, PlaybackState.Playing(1, 0))
        scope.advanceTimeBy(DELAY / 2 + 1)
        assertEquals(1, writes)
    }

    @Test
    fun `the old stream's last Playing under the new URL is not a picture`() {
        recentWrite.schedule(write, 0L, gate = gate)
        scope.runCurrent()
        engine.value = EngineStream(NEW, PLAYING)
        scope.advanceTimeBy(DELAY)
        assertEquals(0, writes)

        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.runCurrent()
        engine.value = EngineStream(NEW, PlaybackState.Playing(1, 0))
        scope.runCurrent()
        assertEquals(1, writes)
    }

    @Test
    fun `a channel already playing when its load ends counts at once`() {
        engine.value = EngineStream(NEW, PLAYING)
        recentWrite.schedule(write, DELAY, gate = gate)
        scope.advanceTimeBy(DELAY + 1)
        assertEquals(1, writes)
    }

    @Test
    fun `the write is dropped when the engine moves to another stream`() {
        recentWrite.schedule(write, DELAY, gate = gate)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.runCurrent()
        engine.value = EngineStream(NEW, PLAYING)
        scope.advanceTimeBy(DELAY / 2)
        engine.value = EngineStream(OTHER, PLAYING)
        scope.runCurrent()
        engine.value = EngineStream(NEW, PLAYING)
        scope.advanceTimeBy(DELAY * 3)
        assertEquals(0, writes)
    }

    @Test
    fun `full screen before the picture writes at the first picture`() {
        recentWrite.schedule(write, DELAY, gate = gate)
        engine.value = EngineStream(NEW, PlaybackState.Buffering)
        scope.runCurrent()

        recentWrite.now()
        scope.advanceTimeBy(DELAY * 3)
        assertEquals(0, writes)

        engine.value = EngineStream(NEW, PLAYING)
        scope.runCurrent()
        assertEquals(1, writes)
    }

    @Test
    fun `the same stream on another login counts`() {
        val gateOnLogin: suspend (Long) -> Boolean = {
            awaitPicture(engine, "http://panel/live/user/pass/7.ts", it) { scope.testScheduler.currentTime }
        }
        recentWrite.schedule(write, 0L, gate = gateOnLogin)
        engine.value = EngineStream("http://panel/live/other/secret/7.ts", PlaybackState.Buffering)
        scope.runCurrent()
        engine.value = EngineStream("http://panel/live/other/secret/7.ts", PLAYING)
        scope.runCurrent()
        assertEquals(1, writes)
    }

    private companion object {
        const val DELAY = 10_000L
        const val OLD = "http://x/old.ts"
        const val NEW = "http://x/new.ts"
        const val OTHER = "http://x/other.ts"
        val PLAYING = PlaybackState.Playing(0, 0)
    }
}
