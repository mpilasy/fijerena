package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class FriendlyErrorTest {
    private val context =
        mockk<Context> {
            every { getString(R.string.error_generic) } returns "generic"
            every { getString(R.string.error_network) } returns "network"
        }

    @Test
    fun executorRejected_mapsToGeneric_notNetwork() {
        // "executor rejected" is OkHttp's InterruptedIOException message when a client's
        // Dispatcher.executorService was already shut down - an internal lifecycle bug, not a
        // connectivity problem. It must not fall into the generic IOException/network bucket,
        // since "check your connection" is actively wrong guidance for it.
        val e = IOException("executor rejected")

        assertEquals("generic", friendlyErrorMessage(e, context))
    }

    @Test
    fun otherIOException_mapsToNetwork() {
        val e = IOException("connection reset")

        assertEquals("network", friendlyErrorMessage(e, context))
    }
}
