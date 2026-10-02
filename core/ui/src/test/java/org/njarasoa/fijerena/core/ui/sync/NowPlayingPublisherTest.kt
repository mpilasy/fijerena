package org.njarasoa.fijerena.core.ui.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot
import org.njarasoa.fijerena.core.ui.sync.NowPlayingPublisher.Companion.DEBOUNCE_MS
import org.njarasoa.fijerena.core.ui.sync.NowPlayingPublisher.Companion.HEARTBEAT_MS

/** When the publisher sends — see docs/plans/20261001_live-sync-now-playing-plan.md → Publishing. */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingPublisherTest {
    private fun channel(name: String) = NowPlayingSnapshot(title = name, isLive = true, channelName = name)

    @Test
    fun `zapping through channels sends only the one landed on`() =
        runTest {
            val playing = MutableStateFlow<NowPlayingSnapshot?>(null)
            val sent = mutableListOf<NowPlayingSnapshot?>()
            val job = launch { NowPlayingPublisher.sends(playing).collect { sent += it } }
            runCurrent()
            listOf("BBC One", "BBC Two", "BBC Four").forEach {
                playing.value = channel(it)
                advanceTimeBy(DEBOUNCE_MS / 4)
            }
            advanceTimeBy(DEBOUNCE_MS + 1)
            assertEquals(listOf(channel("BBC Four")), sent)
            job.cancel()
        }

    @Test
    fun `a heartbeat while playing, none once stopped, and stopped sent once`() =
        runTest {
            val playing = MutableStateFlow<NowPlayingSnapshot?>(channel("BBC One"))
            val sent = mutableListOf<NowPlayingSnapshot?>()
            val job = launch { NowPlayingPublisher.sends(playing).collect { sent += it } }
            advanceTimeBy(DEBOUNCE_MS + 1)
            advanceTimeBy(2 * HEARTBEAT_MS)
            assertEquals(3, sent.size)

            playing.value = null
            advanceTimeBy(DEBOUNCE_MS + 1)
            advanceTimeBy(3 * HEARTBEAT_MS)
            assertEquals(listOf(channel("BBC One"), channel("BBC One"), channel("BBC One"), null), sent)
            job.cancel()
        }

    @Test
    fun `a position change alone sends nothing until the heartbeat`() =
        runTest {
            val movie = NowPlayingSnapshot(title = "Malcolm X", positionMs = 0, durationMs = 1_000_000)
            val playing = MutableStateFlow<NowPlayingSnapshot?>(movie)
            val sent = mutableListOf<NowPlayingSnapshot?>()
            val job = launch { NowPlayingPublisher.sends(playing).collect { sent += it } }
            advanceTimeBy(DEBOUNCE_MS + 1)
            playing.value = movie.copy(positionMs = 30_000)
            advanceTimeBy(DEBOUNCE_MS + 1)
            assertEquals(listOf<NowPlayingSnapshot?>(movie), sent)
            advanceTimeBy(HEARTBEAT_MS)
            assertEquals(listOf<NowPlayingSnapshot?>(movie, movie.copy(positionMs = 30_000)), sent)
            job.cancel()
        }

    @Test
    fun `a device that never shared sends nothing, not even stopped`() =
        runTest {
            val playing = MutableStateFlow<NowPlayingSnapshot?>(null)
            val sent = mutableListOf<NowPlayingSnapshot?>()
            val job = launch { NowPlayingPublisher.sends(playing).collect { sent += it } }
            advanceTimeBy(DEBOUNCE_MS + 2 * HEARTBEAT_MS)
            assertEquals(emptyList<NowPlayingSnapshot?>(), sent)
            job.cancel()
        }
}
