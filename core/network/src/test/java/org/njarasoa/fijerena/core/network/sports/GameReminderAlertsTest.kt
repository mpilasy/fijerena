package org.njarasoa.fijerena.core.network.sports

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GameReminderAlerts]: a tapped notification's channel goes through the launch intent once, and
 * a reminder shows as the banner only while a banner host is on screen. See the sports teams plan,
 * Phase 7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameReminderAlertsTest {
    private val channel = ReminderChannel(providerId = 3, streamId = 42, categoryId = "7", name = "Sky Sports 1")
    private val alert = GameReminderAlert("p1", "espn", "g1", "Arsenal", "Chelsea", "Premier League", 1_800_000_000, channel)

    @After
    fun tearDown() {
        GameReminderAlerts.takeTune()
        GameReminderAlerts.dismissBanner(alert)
    }

    @Test
    fun `a tapped notification's channel is tuned once`() {
        val intent = GameReminderAlerts.putTune(Intent(), ReminderTune("p1", channel))
        GameReminderAlerts.handleLaunchIntent(intent)
        assertEquals(ReminderTune("p1", channel), GameReminderAlerts.takeTune())
        assertNull(GameReminderAlerts.takeTune())

        // The extras went with it: the same intent handled again (a recreation) tunes nothing.
        GameReminderAlerts.handleLaunchIntent(intent)
        assertNull(GameReminderAlerts.tune.value)
    }

    @Test
    fun `an intent without a reminder tunes nothing`() {
        GameReminderAlerts.handleLaunchIntent(Intent(Intent.ACTION_MAIN))
        assertNull(GameReminderAlerts.tune.value)
    }

    @Test
    fun `the banner shows only while its host is on screen`() {
        assertFalse(GameReminderAlerts.showBanner(alert))
        GameReminderAlerts.bannerHostStarted()
        try {
            assertTrue(GameReminderAlerts.showBanner(alert))
            assertEquals(alert, GameReminderAlerts.banner.value)
            GameReminderAlerts.dismissBanner(alert)
            assertNull(GameReminderAlerts.banner.value)
        } finally {
            GameReminderAlerts.bannerHostStopped()
        }
        assertFalse(GameReminderAlerts.hasBannerHost)
    }
}
