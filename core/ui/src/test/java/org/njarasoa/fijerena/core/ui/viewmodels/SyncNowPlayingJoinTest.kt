package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.NowPlayingStore
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncWire

/** The devices list's join and staleness rule — see docs/plans/20261001_live-sync-now-playing-plan.md → Phase 2.2. */
class SyncNowPlayingJoinTest {
    private val now = 50_000_000L

    private fun device(
        id: String,
        revoked: Boolean = false,
        lastSeen: Long = now,
    ) = SyncWire.Device(id, id, createdAt = 0, lastSeen = lastSeen, revoked = revoked, current = false)

    private fun entry(
        sentAt: Long,
        state: String = SyncPayloads.NowPlaying.PLAYING,
        receivedAt: Long = sentAt,
    ) = NowPlayingStore.Entry(SyncPayloads.NowPlaying(state = state, title = "Malcolm X", sentAt = sentAt), hlc = 1, receivedAt = receivedAt)

    @Test
    fun `only listed devices playing or paused right now are shown`() {
        val devices = listOf(device("tv"), device("shield"), device("phone"), device("old", revoked = true))
        val entries =
            mapOf(
                "tv" to entry(now - 30_000),
                "shield" to entry(now - 30_000, SyncPayloads.NowPlaying.PAUSED),
                "phone" to entry(now - 30_000, SyncPayloads.NowPlaying.STOPPED),
                "old" to entry(now - 30_000),
                "unlisted" to entry(now - 30_000),
            )
        assertEquals(setOf("tv", "shield"), SyncSettingsViewModel.currentNowPlaying(devices, entries, now).keys)
    }

    @Test
    fun `a playing device goes stale after three minutes without a heartbeat`() {
        val devices = listOf(device("tv"))
        val entries = mapOf("tv" to entry(now))
        assertEquals(setOf("tv"), SyncSettingsViewModel.currentNowPlaying(devices, entries, now + NowPlayingStore.STALE_AFTER_MS).keys)
        assertEquals(emptySet<String>(), SyncSettingsViewModel.currentNowPlaying(devices, entries, now + NowPlayingStore.STALE_AFTER_MS + 1).keys)
    }

    @Test
    fun `a slow sender clock shows while the server saw the device, not when it did not`() {
        val devices = listOf(device("fresh", lastSeen = now - 10_000), device("old", lastSeen = now - 3_600_000))
        val entries =
            mapOf(
                "fresh" to entry(now - 600_000, receivedAt = now - 5_000),
                "old" to entry(now - 3_600_000, receivedAt = now - 5_000),
            )
        assertEquals(setOf("fresh"), SyncSettingsViewModel.currentNowPlaying(devices, entries, now).keys)
    }

    @Test
    fun `nothing received for three minutes is hidden whatever the server saw`() {
        val devices = listOf(device("tv"))
        val entries = mapOf("tv" to entry(now - 600_000, receivedAt = now - NowPlayingStore.STALE_AFTER_MS - 1))
        assertEquals(emptySet<String>(), SyncSettingsViewModel.currentNowPlaying(devices, entries, now).keys)
    }
}
