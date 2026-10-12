package org.njarasoa.fijerena.feature.category

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry.ExpiringSource
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The catalogue banner's line for a source, from its days left (the English strings). */
@RunWith(RobolectricTestRunner::class)
class SourceExpiryBannerTextTest {
    private fun text(daysLeft: Long): String =
        SubscriptionExpiry.bannerText(RuntimeEnvironment.getApplication().resources, ExpiringSource("xstream4k", daysLeft))

    @Test
    fun `expires today`() = assertEquals("xstream4k expires today", text(0))

    @Test
    fun `expires tomorrow`() = assertEquals("xstream4k expires tomorrow", text(1))

    @Test
    fun `expires in N days`() = assertEquals("xstream4k expires in 5 days", text(5))

    @Test
    fun `expired N days ago`() {
        assertEquals("xstream4k expired 2 days ago", text(-2))
        assertEquals("xstream4k expired 1 day ago", text(-1))
    }
}
