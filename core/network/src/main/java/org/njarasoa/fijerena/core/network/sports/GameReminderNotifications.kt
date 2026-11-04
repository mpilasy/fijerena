package org.njarasoa.fijerena.core.network.sports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.network.R
import java.util.Date

/**
 * The "Reminders" notification of games and guide programmes (both apps; on TV only while the app
 * is not on screen, else the banner shows). Its text is "<home> v <away> starts at <time>" ("<title>
 * starts at <time>" for a programme), then the best channel ("On Sky Sports 1") or the competition. Tapping it opens the app, which tunes the best channel when
 * there is one ([GameReminderAlerts.handleLaunchIntent]).
 *
 * On the phone it also has the action "Manage reminders", which opens the app on the Reminders
 * page ([handleLaunchIntent], [openReminders]). The TV notification has no such action.
 */
object GameReminderNotifications {
    private const val CHANNEL_ID = "game_reminders"
    private const val EXTRA_MANAGE_PROFILE_ID = "org.njarasoa.fijerena.reminder.MANAGE_PROFILE_ID"
    private const val EXTRA_MANAGE_NOTIFICATION_ID = "org.njarasoa.fijerena.reminder.MANAGE_NOTIFICATION_ID"

    // One request code for all "Manage reminders" actions, not the tap's, so the tap's intent keeps its channel.
    private val MANAGE_REQUEST_CODE = "reminders:manage".hashCode()

    private val manageState = MutableStateFlow<String?>(null)

    /** The profile whose Reminders page a tapped "Manage reminders" asks for; the nav host takes it with [takeOpenReminders]. */
    val openReminders: StateFlow<String?> = manageState.asStateFlow()

    /**
     * Whether the app needs to ask for `POST_NOTIFICATIONS` before reminders can show (Android 13
     * and later). The UI asks when the first bell is set.
     */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** The game and start time, as the notification and the TV banner show them. */
    fun title(
        context: Context,
        alert: GameReminderAlert,
    ): String {
        val time = DateFormat.getTimeFormat(context).format(Date(alert.startEpochSec * 1000))
        return if (alert.awayName != null) {
            context.getString(R.string.game_reminder_title_vs, alert.homeName, alert.awayName, time)
        } else {
            context.getString(R.string.game_reminder_title_one, alert.homeName, time)
        }
    }

    /** "On <channel>", or the competition when no channel was found. */
    fun detail(
        context: Context,
        alert: GameReminderAlert,
    ): String = alert.channel?.let { context.getString(R.string.game_reminder_on_channel, it.name) } ?: alert.competitionName

    fun post(
        context: Context,
        alert: GameReminderAlert,
    ) {
        if (needsPermission(context)) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val id = notificationId(alert)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title(context, alert))
                .setContentText(detail(context, alert))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(tapIntent(context, alert, id))
                .apply {
                    manageIntent(context, alert, id)?.let {
                        addAction(android.R.drawable.ic_popup_reminder, context.getString(R.string.game_reminder_manage), it)
                    }
                }.build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // cancellation-ok: not a suspend call. The permission was taken back in between.
            android.util.Log.w("GameReminderNotifications", "Reminder not shown: ${e.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Created, or renamed ("Game reminders" became "Reminders"); the user's importance is kept.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.game_reminder_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    // One notification per game: two profiles' reminders of one game show once.
    private fun notificationId(alert: GameReminderAlert): Int = "reminder:${alert.source}/${alert.gameId}".hashCode()

    /** Opens the app (its launcher activity), asking it to tune the best channel when there is one. */
    private fun tapIntent(
        context: Context,
        alert: GameReminderAlert,
        requestCode: Int,
    ): PendingIntent? {
        val pm = context.packageManager
        val launch =
            (pm.getLaunchIntentForPackage(context.packageName) ?: pm.getLeanbackLaunchIntentForPackage(context.packageName))
                ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        alert.channel?.let { GameReminderAlerts.putTune(launch, ReminderTune(alert.profileId, it)) }
        return PendingIntent.getActivity(context, requestCode, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * "Manage reminders" (the Reminders page plan, RP-Q1): opens the app on the Reminders page of
     * the reminder's profile. Null on TV: there, the banner has the action.
     */
    private fun manageIntent(
        context: Context,
        alert: GameReminderAlert,
        notificationId: Int,
    ): PendingIntent? {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return null
        val launch = pm.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putOpenReminders(launch, alert.profileId, notificationId)
        return PendingIntent.getActivity(
            context,
            MANAGE_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Puts "open the Reminders page of [profileId]" into an intent that opens the app ([handleLaunchIntent] reads it). */
    fun putOpenReminders(
        intent: Intent,
        profileId: String,
        notificationId: Int,
    ): Intent =
        intent
            .putExtra(EXTRA_MANAGE_PROFILE_ID, profileId)
            .putExtra(EXTRA_MANAGE_NOTIFICATION_ID, notificationId)

    /**
     * The phone's MainActivity, in `onCreate` (not on a recreation) and `onNewIntent`: a tapped
     * "Manage reminders" asks for the Reminders page, and its notification is removed (a tapped
     * action does not remove it). The extras are removed, so the same intent never opens the page
     * two times.
     */
    fun handleLaunchIntent(
        context: Context,
        intent: Intent?,
    ) {
        val profileId = intent?.getStringExtra(EXTRA_MANAGE_PROFILE_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_MANAGE_NOTIFICATION_ID, 0)
        intent.removeExtra(EXTRA_MANAGE_PROFILE_ID)
        intent.removeExtra(EXTRA_MANAGE_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(notificationId)
        manageState.value = profileId
    }

    /** The profile whose Reminders page is asked for, taken once: null when another caller took it first. */
    fun takeOpenReminders(): String? {
        while (true) {
            val current = manageState.value ?: return null
            if (manageState.compareAndSet(current, null)) return current
        }
    }
}
