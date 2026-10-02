package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot

/** See docs/plans/20261001_live-sync-now-playing-plan.md → Phase 1 and 2. */
class NowPlayingSyncTest {
    private val key = SyncKey(SyncKind.SHARED, "", SyncKind.NOW_PLAYING, "device-1")
    private val episode = NowPlayingSnapshot(title = "Pilot", showTitle = "The King of Queens", episodeLabel = "S1:E12", positionMs = 60_000, durationMs = 1_320_000)

    @Test
    fun `the payload survives encoding, sealing and opening`() {
        val crypto = AccountKeyCrypto(AccountKeyCrypto.newAccountKey())
        val payload = SyncPayloads.NowPlaying.of(episode, "Kid", sentAt = 1_000)
        val record = SyncRecord(key, hlc = 5, payload = SyncPayloads.encode(payload))

        val received = SyncCodec.decode(SyncCodec.encode(record, crypto), crypto)!!

        assertEquals(record, received)
        assertEquals(payload, SyncPayloads.decode<SyncPayloads.NowPlaying>(received.payload))
    }

    @Test
    fun `the session id travels, and an older sender's payload has none`() {
        assertEquals("s-1", SyncPayloads.NowPlaying.of(episode.copy(sessionId = "s-1"), "Kid", 1).sessionId)
        assertEquals(null, SyncPayloads.decode<SyncPayloads.NowPlaying>("""{"state":"playing","sentAt":1}""").sessionId)
    }

    @Test
    fun `a snapshot becomes playing or paused, nothing becomes stopped`() {
        assertEquals(SyncPayloads.NowPlaying.PLAYING, SyncPayloads.NowPlaying.of(episode, "Kid", 1).state)
        assertEquals(SyncPayloads.NowPlaying.PAUSED, SyncPayloads.NowPlaying.of(episode.copy(paused = true), "Kid", 1).state)
        assertEquals(SyncPayloads.NowPlaying.STOPPED, SyncPayloads.NowPlaying.of(null, "Kid", 1).state)
    }

    @Test
    fun `a heartbeat's updatedAt always increases, whatever the clock says`() =
        runBlocking {
            val outbox = VolatileRecords()
            // A clock that stands still, then goes back: each send must still be newer than the last.
            val ticks = ArrayDeque(listOf(100L, 100L, 90L, 200L))
            val sent = mutableListOf<Long>()
            repeat(4) { i ->
                outbox.put(key, """{"state":"playing","sentAt":$i}""")
                val outgoing = outbox.take { ticks.removeFirst() }.single()
                sent += outgoing.record.hlc
                outgoing.markSent()
            }
            assertEquals(listOf(100L, 101L, 102L, 200L), sent)
        }

    @Test
    fun `a record stays waiting until sent, and an older push never clears a newer state`() =
        runBlocking {
            val outbox = VolatileRecords()
            outbox.put(key, "first")
            val failedPush = outbox.take { 1 }
            assertEquals("first", outbox.take { 2 }.single().record.payload) // not sent: still there

            outbox.put(key, "second")
            failedPush.single().markSent()
            assertEquals("second", outbox.take { 3 }.single().record.payload)

            outbox.take { 4 }.single().markSent()
            assertTrue(outbox.take { 5 }.isEmpty())
        }

    @Test
    fun `playing or paused is current for three minutes by both clocks`() {
        val now = 10_000_000L
        fun entry(
            state: String,
            sentAt: Long,
            receivedAt: Long = now,
        ) = NowPlayingStore.Entry(SyncPayloads.NowPlaying(state = state, sentAt = sentAt), hlc = 1, receivedAt = receivedAt)

        assertTrue(entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now - 60_000).isCurrent(now))
        assertTrue(entry(SyncPayloads.NowPlaying.PAUSED, sentAt = now - NowPlayingStore.STALE_AFTER_MS).isCurrent(now))
        // Three missed heartbeats: a TV switched off at the wall.
        assertFalse(entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now - NowPlayingStore.STALE_AFTER_MS - 1).isCurrent(now))
        // An old record arriving now (a catch-up pull).
        assertFalse(entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now - 3_600_000).isCurrent(now))
        // A sender whose clock runs an hour ahead, quiet since.
        assertFalse(entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now + 3_600_000, receivedAt = now - NowPlayingStore.STALE_AFTER_MS - 1).isCurrent(now))
        assertFalse(entry(SyncPayloads.NowPlaying.STOPPED, sentAt = now).isCurrent(now))
        // A sender whose clock is 10 min slow: shown while the server saw it recently...
        val slow = entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now - 600_000)
        assertTrue(slow.isCurrent(now, lastSeen = now - 10_000))
        // ...hidden when the server has not (an old record from a long-off device).
        assertFalse(slow.isCurrent(now, lastSeen = now - NowPlayingStore.STALE_AFTER_MS - 1))
        assertFalse(slow.isCurrent(now, lastSeen = null))
        // Nothing received for 3 min is hidden whatever the server saw.
        val quiet = entry(SyncPayloads.NowPlaying.PLAYING, sentAt = now - 600_000, receivedAt = now - NowPlayingStore.STALE_AFTER_MS - 1)
        assertFalse(quiet.isCurrent(now, lastSeen = now))
    }

    @Test
    fun `the store keeps a device's newest record`() {
        fun entry(
            hlc: Long,
            title: String,
        ) = NowPlayingStore.Entry(SyncPayloads.NowPlaying(state = SyncPayloads.NowPlaying.PLAYING, title = title, sentAt = 0), hlc, receivedAt = 0)
        NowPlayingStore.clear()
        NowPlayingStore.receive("tv", entry(2, "newer"))
        NowPlayingStore.receive("tv", entry(1, "older"))
        assertEquals("newer", NowPlayingStore.devices.value.getValue("tv").nowPlaying.title)
        NowPlayingStore.clear()
    }
}
