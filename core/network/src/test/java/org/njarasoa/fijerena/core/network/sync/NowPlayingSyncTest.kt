package org.njarasoa.fijerena.core.network.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot

/** See docs/plans/20261001_live-sync-now-playing-plan.md → Phase 1. */
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
}
