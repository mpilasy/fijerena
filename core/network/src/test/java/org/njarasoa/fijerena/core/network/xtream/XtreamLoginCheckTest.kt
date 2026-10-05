package org.njarasoa.fijerena.core.network.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.XtreamUserInfo

/** docs/plans/20261005_shared-logins-plan.md → Phase 1. */
class XtreamLoginCheckTest {
    private fun info(
        auth: Int = 1,
        status: String = "Active",
        expDate: String? = "1801609200",
        activeCons: String? = "1",
        maxConnections: String? = "1",
    ) = XtreamUserInfo(
        username = "u",
        password = "p",
        auth = auth,
        status = status,
        expDate = expDate,
        activeCons = activeCons,
        maxConnections = maxConnections,
    )

    @Test
    fun `an active login reads its expiry and connections`() {
        val status = XtreamLoginCheck.statusOf(info())
        assertTrue(status.active)
        assertEquals(1801609200L, status.expiresAtSec)
        assertEquals(1, status.activeCons)
        assertEquals(1, status.maxConnections)
    }

    @Test
    fun `expired, banned or refused logins aren't active`() {
        assertFalse(XtreamLoginCheck.statusOf(info(status = "Expired")).active)
        assertFalse(XtreamLoginCheck.statusOf(info(status = "Banned")).active)
        assertFalse(XtreamLoginCheck.statusOf(info(auth = 0)).active)
    }

    @Test
    fun `no expiry date, zero or garbage, means unlimited`() {
        assertNull(XtreamLoginCheck.statusOf(info(expDate = null)).expiresAtSec)
        assertNull(XtreamLoginCheck.statusOf(info(expDate = "0")).expiresAtSec)
        assertNull(XtreamLoginCheck.statusOf(info(expDate = "never")).expiresAtSec)
    }

    @Test
    fun `missing connection counts read as unknown`() {
        val status = XtreamLoginCheck.statusOf(info(activeCons = null, maxConnections = ""))
        assertNull(status.activeCons)
        assertNull(status.maxConnections)
    }

    @Test
    fun `same catalogue when the main login's first ids are all there`() {
        assertTrue(XtreamLoginCheck.sharesCatalogue((1..50).toList(), (1..100).toSet()))
    }

    @Test
    fun `only the first twenty ids are compared`() {
        assertTrue(XtreamLoginCheck.sharesCatalogue((1..50).toList(), (1..20).toSet()))
    }

    @Test
    fun `a missing id means a different catalogue`() {
        assertFalse(XtreamLoginCheck.sharesCatalogue(listOf(1, 2, 3), setOf(1, 3)))
    }

    @Test
    fun `an empty category on the main login matches`() {
        assertTrue(XtreamLoginCheck.sharesCatalogue(emptyList(), emptySet()))
    }
}
