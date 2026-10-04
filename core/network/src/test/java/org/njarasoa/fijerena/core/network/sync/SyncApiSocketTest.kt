package org.njarasoa.fijerena.core.network.sync

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-26: the app's text `ping` is never checked for a `pong`, so after a Wi-Fi drop or an expired
 * NAT entry (no FIN) sends kept succeeding into the void and live updates silently stopped. The
 * socket client pings at protocol level instead: OkHttp fails a socket whose pong doesn't come
 * back, and `SyncManager` reconnects it (see SyncManagerSocketTest). See
 * docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-26.
 */
class SyncApiSocketTest {
    @Test
    fun `the sync socket pings at protocol level every 30 seconds`() {
        val base = OkHttpClient()
        val api = SyncApi(base)

        assertEquals(30_000, api.socketClient.pingIntervalMillis)
        // Only the socket client: plain HTTP calls on the shared client are left as they were.
        assertEquals(0, base.pingIntervalMillis)
    }

    @Test
    fun `HTTP calls have an overall deadline and the socket has none`() {
        val api = SyncApi(OkHttpClient())

        assertEquals(60_000, api.httpClient.callTimeoutMillis)
        // A call timeout on the socket would close it a minute after it opened.
        assertEquals(0, api.socketClient.callTimeoutMillis)
    }
}
