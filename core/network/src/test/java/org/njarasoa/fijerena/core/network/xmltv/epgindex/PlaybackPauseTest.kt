package org.njarasoa.fijerena.core.network.xmltv.epgindex

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** [PlaybackPause]: indexing waits while video plays and for a quiet minute after. */
class PlaybackPauseTest {
    private var now = 0L
    private var playingUntil = 0L

    private fun pause() =
        PlaybackPause(
            isPlaying = { now < playingUntil },
            quietMs = 60_000,
            pollMs = 5_000,
            clock = { now },
            sleep = { now += it },
        )

    @Test
    fun `nothing playing, no wait`() =
        runBlocking {
            assertEquals(0L, pause().await())
        }

    @Test
    fun `while playing it waits, then a quiet minute`() =
        runBlocking {
            playingUntil = 20_000
            val events = mutableListOf<Boolean>()
            val waited = pause().await { events += it }
            // Seen playing last at 15 s (polls every 5 s): free at 75 s.
            assertEquals(75_000L, waited)
            assertEquals(listOf(true, false), events)
        }

    @Test
    fun `once the quiet minute has passed, the next batch does not wait`() =
        runBlocking {
            val pause = pause()
            playingUntil = 1
            pause.await()
            playingUntil = 0
            assertEquals(0L, pause.await())
        }
}
