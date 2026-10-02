package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel.StopRequest
import org.njarasoa.fijerena.core.ui.viewmodels.SyncSettingsViewModel.StopState

/** The phone's "Stopping…" / "Couldn't reach" row — see docs/plans/20261001_live-sync-now-playing-plan.md → Phase 4.5. */
class SyncRemoteStopStateTest {
    private val sentAt = 1_000_000L
    private val requests = mapOf("tv" to StopRequest("session-1", sentAt))

    private fun playing(sessionId: String) =
        mapOf("tv" to SyncPayloads.NowPlaying(state = SyncPayloads.NowPlaying.PLAYING, sentAt = sentAt, sessionId = sessionId))

    @Test
    fun `stopping while the device still shows that playback, unreachable after the timeout`() {
        assertEquals(mapOf("tv" to StopState.STOPPING), SyncSettingsViewModel.pendingStops(requests, playing("session-1"), sentAt + 1_000))
        assertEquals(
            mapOf("tv" to StopState.STOPPING),
            SyncSettingsViewModel.pendingStops(requests, playing("session-1"), sentAt + SyncSettingsViewModel.STOP_TIMEOUT_MS),
        )
        assertEquals(
            mapOf("tv" to StopState.UNREACHABLE),
            SyncSettingsViewModel.pendingStops(requests, playing("session-1"), sentAt + SyncSettingsViewModel.STOP_TIMEOUT_MS + 1),
        )
    }

    @Test
    fun `done once the device stops, goes stale, or plays another session`() {
        assertEquals(emptyMap<String, StopState>(), SyncSettingsViewModel.pendingStops(requests, emptyMap(), sentAt + 1_000))
        assertEquals(emptyMap<String, StopState>(), SyncSettingsViewModel.pendingStops(requests, playing("session-2"), sentAt + 1_000))
    }

    @Test
    fun `a request is kept only while its device shows the named session`() {
        assertEquals(requests, SyncSettingsViewModel.openStops(requests, playing("session-1")))
        assertEquals(emptyMap<String, StopRequest>(), SyncSettingsViewModel.openStops(requests, emptyMap()))
        assertEquals(emptyMap<String, StopRequest>(), SyncSettingsViewModel.openStops(requests, playing("session-2")))
    }
}
