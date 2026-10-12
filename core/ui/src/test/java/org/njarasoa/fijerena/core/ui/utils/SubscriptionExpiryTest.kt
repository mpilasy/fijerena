package org.njarasoa.fijerena.core.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry.ExpiringSource
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry.Level
import java.time.LocalDateTime
import java.time.ZoneId

class SubscriptionExpiryTest {
    private val zone = ZoneId.of("Indian/Antananarivo")

    // 2026-10-12 10:00 on the device.
    private val now = LocalDateTime.of(2026, 10, 12, 10, 0).atZone(zone).toInstant()

    /** An expiry [days] calendar days from today, at [hour] o'clock on the device. */
    private fun expiryIn(
        days: Long,
        hour: Int = 18,
    ): Long =
        LocalDateTime
            .of(2026, 10, 12, hour, 0)
            .plusDays(days)
            .atZone(zone)
            .toEpochSecond()

    private fun levelIn(days: Long): Level = SubscriptionExpiry.level(expiryIn(days), now, zone)

    @Test
    fun `16 days left is normal`() = assertEquals(Level.NORMAL, levelIn(16))

    @Test
    fun `15 days left is soon`() = assertEquals(Level.SOON, levelIn(15))

    @Test
    fun `8 days left is soon`() = assertEquals(Level.SOON, levelIn(8))

    @Test
    fun `7 days left is urgent`() = assertEquals(Level.URGENT, levelIn(7))

    @Test
    fun `0 days left is urgent`() = assertEquals(Level.URGENT, levelIn(0))

    @Test
    fun `expired is urgent`() = assertEquals(Level.URGENT, levelIn(-3))

    @Test
    fun `no end is normal`() = assertEquals(Level.NORMAL, SubscriptionExpiry.level(null, now, zone))

    @Test
    fun `unlimited or missing exp_date has no end`() {
        assertNull(SubscriptionExpiry.expiresAtSec("Unlimited"))
        assertNull(SubscriptionExpiry.expiresAtSec(null))
        assertNull(SubscriptionExpiry.expiresAtSec(""))
        assertNull(SubscriptionExpiry.expiresAtSec("0"))
        assertEquals(1801609200L, SubscriptionExpiry.expiresAtSec("1801609200"))
    }

    @Test
    fun `days are whole calendar days on the device`() {
        assertEquals(0L, SubscriptionExpiry.daysLeft(expiryIn(0, hour = 23), now, zone))
        assertEquals(0L, SubscriptionExpiry.daysLeft(expiryIn(0, hour = 1), now, zone))
        assertEquals(1L, SubscriptionExpiry.daysLeft(expiryIn(1, hour = 0), now, zone))
        assertEquals(-2L, SubscriptionExpiry.daysLeft(expiryIn(-2), now, zone))
        // The same moment is already the next day in a zone further east.
        val late = LocalDateTime.of(2026, 10, 12, 22, 0).atZone(zone).toInstant()
        assertEquals(0L, SubscriptionExpiry.daysLeft(expiryIn(1, hour = 2), late, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `the banner names sources expiring in 7 days or fewer, or expired`() {
        val sources =
            listOf(
                "unlimited" to null,
                "far" to expiryIn(30),
                "soon" to expiryIn(8),
                "week" to expiryIn(7),
                "today" to expiryIn(0),
                "gone" to expiryIn(-2),
            )
        assertEquals(
            listOf(
                ExpiringSource("week", 7),
                ExpiringSource("today", 0),
                ExpiringSource("gone", -2),
            ),
            SubscriptionExpiry.bannerSources(sources, now, zone),
        )
    }

    @Test
    fun `no banner when nothing expires within a week`() =
        assertEquals(
            emptyList<ExpiringSource>(),
            SubscriptionExpiry.bannerSources(listOf("a" to expiryIn(8), "b" to null), now, zone),
        )
}
