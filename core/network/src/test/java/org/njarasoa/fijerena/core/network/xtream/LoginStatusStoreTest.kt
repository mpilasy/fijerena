package org.njarasoa.fijerena.core.network.xtream

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A login's last panel answer is kept per source and username, unlimited fields included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginStatusStoreTest {
    private val store = LoginStatusStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun `a saved answer reads back as it was`() {
        val status = XtreamLoginCheck.Status(true, "Active", expiresAtSec = 1_801_000_000L, activeCons = 0, maxConnections = 1)

        store.put(9L, "user2", status, checkedAtMs = 1_760_000_000_000L)

        assertEquals(LoginStatusStore.Saved(status, 1_760_000_000_000L), store.get(9L, "user2"))
    }

    @Test
    fun `an unlimited login keeps no expiry, and another source's login is apart`() {
        val status = XtreamLoginCheck.Status(true, "Active", expiresAtSec = null, activeCons = null, maxConnections = null)

        store.put(9L, "user", status, checkedAtMs = 1L)

        assertEquals(status, store.get(9L, "user")?.status)
        assertNull(store.get(10L, "user"))
    }
}
