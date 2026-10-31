package org.njarasoa.fijerena.core.network.sports

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity

/** Sets and cancels the alarm of one game reminder. A fake stands in for it in tests. */
interface ReminderAlarms {
    /** Sets (or moves) [reminder]'s alarm to [atMs]. */
    fun schedule(
        reminder: GameReminderEntity,
        atMs: Long,
    )

    fun cancel(reminder: GameReminderEntity)

    /** Turns the reschedule receiver (boot, app update, clock change) on while any reminder is set. */
    fun setRescheduleReceiverOn(on: Boolean) {}
}

/**
 * [ReminderAlarms] on `AlarmManager`: `setExactAndAllowWhileIdle` when the app may set exact
 * alarms, else a 10-minute window that rings 10 to 20 minutes before the game
 * ([ReminderSchedule.alarm]). The app may set exact alarms on every supported Android version
 * (see the `core:network` manifest), unless the user turns off "Alarms & reminders" on Android 12
 * or 12L. One alarm per reminder, told apart by its intent's data URI. The alarm intent carries the
 * game's names and start, so [GameReminderReceiver] can ring without the database (crash-loop safe
 * mode).
 */
class AndroidReminderAlarms(
    context: Context,
) : ReminderAlarms {
    private val app = context.applicationContext
    private val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(
        reminder: GameReminderEntity,
        atMs: Long,
    ) {
        val pending = pendingIntent(reminder, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val alarm = ReminderSchedule.alarm(atMs, reminder.startEpoch, System.currentTimeMillis(), canScheduleExact(alarmManager))
        when (alarm) {
            is ReminderSchedule.Alarm.Exact -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMs, pending)
            }

            is ReminderSchedule.Alarm.Window -> {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, alarm.startMs, alarm.lengthMs, pending)
            }

            is ReminderSchedule.Alarm.Now -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMs, pending)
            }
        }
    }

    override fun cancel(reminder: GameReminderEntity) {
        pendingIntent(reminder, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun setRescheduleReceiverOn(on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = ComponentName(app, GameReminderRescheduleReceiver::class.java)
        if (app.packageManager.getComponentEnabledSetting(component) != state) {
            app.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    private fun pendingIntent(
        reminder: GameReminderEntity,
        flag: Int,
    ): PendingIntent? = PendingIntent.getBroadcast(app, 0, alarmIntent(app, reminder), flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        /** Whether alarms ring on time: exact alarms are allowed. */
        fun canScheduleExact(alarmManager: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        internal fun alarmIntent(
            context: Context,
            reminder: GameReminderEntity,
        ): Intent =
            Intent(context, GameReminderReceiver::class.java)
                .setAction(GameReminderReceiver.ACTION_RING)
                .setData(
                    Uri
                        .Builder()
                        .scheme("fijerena-reminder")
                        .authority("game")
                        .appendPath(reminder.profileId)
                        .appendPath(reminder.source)
                        .appendPath(reminder.gameId)
                        .build(),
                ).putExtra(GameReminderReceiver.EXTRA_PROFILE_ID, reminder.profileId)
                .putExtra(GameReminderReceiver.EXTRA_SOURCE, reminder.source)
                .putExtra(GameReminderReceiver.EXTRA_GAME_ID, reminder.gameId)
                .putExtra(GameReminderReceiver.EXTRA_HOME, reminder.homeName)
                .putExtra(GameReminderReceiver.EXTRA_AWAY, reminder.awayName)
                .putExtra(GameReminderReceiver.EXTRA_COMPETITION, reminder.competitionName)
                .putExtra(GameReminderReceiver.EXTRA_START, reminder.startEpoch)
    }
}
