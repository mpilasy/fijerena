package org.njarasoa.fijerena.core.network.sports

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [GameReminderNotifications]: the phone's notification has "Manage reminders", which opens the
 * Reminders page one time; the TV's has no such action (the Reminders page plan, RP-Q1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameReminderNotificationsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alert = GameReminderAlert("p1", "espn", "g1", "Arsenal", "Chelsea", "Premier League", 1_800_000_000, null)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // A launcher activity, as each app has: the notification's intents open it.
        val main = ComponentName(app, "org.njarasoa.fijerena.MainActivity")
        val packages = shadowOf(app.packageManager)
        packages.addActivityIfNotPresent(main)
        packages.addIntentFilterForActivity(
            main,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @After
    fun tearDown() {
        GameReminderNotifications.takeOpenReminders()
    }

    @Test
    fun `a tapped Manage reminders opens the page one time`() {
        val intent = GameReminderNotifications.putOpenReminders(Intent(), "p1", 7)
        GameReminderNotifications.handleLaunchIntent(app, intent)
        assertEquals("p1", GameReminderNotifications.takeOpenReminders())
        assertNull(GameReminderNotifications.takeOpenReminders())

        // The extras went with it: the same intent handled again (a recreation) opens nothing.
        GameReminderNotifications.handleLaunchIntent(app, intent)
        assertNull(GameReminderNotifications.openReminders.value)
    }

    @Test
    fun `an intent without the action opens nothing`() {
        GameReminderNotifications.handleLaunchIntent(app, Intent(Intent.ACTION_MAIN))
        assertNull(GameReminderNotifications.openReminders.value)
    }

    @Test
    fun `the phone's notification has Manage reminders`() {
        GameReminderNotifications.post(app, alert)
        val actions = postedActions()
        assertEquals(listOf(app.getString(org.njarasoa.fijerena.core.network.R.string.game_reminder_manage)), actions)
    }

    @Test
    fun `the TV's notification has no action`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        GameReminderNotifications.post(app, alert)
        assertEquals(emptyList<String>(), postedActions())
    }

    private fun postedActions(): List<String> {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = shadowOf(manager).allNotifications.single()
        return notification.actions.orEmpty().map { it.title.toString() }
    }
}
