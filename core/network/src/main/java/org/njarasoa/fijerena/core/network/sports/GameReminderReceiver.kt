package org.njarasoa.fijerena.core.network.sports

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProvidersDbGuard
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import org.njarasoa.fijerena.core.player.diagnostics.SafeMode

/**
 * A game reminder's alarm ([AndroidReminderAlarms]). [GameReminders.onAlarm] reads the game
 * again: a moved game gets a new alarm, a cancelled or finished one is dropped, else the reminder
 * rings: the TV banner while the TV app is on screen ([GameReminderAlerts.showBanner]), else the
 * notification. In crash-loop safe mode (or with a `providers.db` from a newer build) nothing is
 * read: the reminder rings from the alarm's own extras, with no channel.
 */
class GameReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_RING) return
        val app = context.applicationContext
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return
        val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: return
        if (SafeMode.isActive || ProvidersDbGuard.isBlocked) {
            alertFromExtras(intent, profileId, source, gameId)?.let { GameReminderNotifications.post(app, it) }
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                ring(app, profileId, source, gameId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RING = "org.njarasoa.fijerena.action.GAME_REMINDER"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_GAME_ID = "gameId"
        const val EXTRA_HOME = "home"
        const val EXTRA_AWAY = "away"
        const val EXTRA_COMPETITION = "competition"
        const val EXTRA_START = "start"

        private val scope by lazy { AppScopes.create("GameReminderReceiver", Dispatchers.IO) }

        private suspend fun ring(
            context: Context,
            profileId: String,
            source: String,
            gameId: String,
        ) {
            val outcome = GameReminders.getInstance(context).onAlarm(profileId, source, gameId)
            if (outcome !is GameReminders.AlarmOutcome.Ring) return
            val reminder = outcome.reminder
            val programme = reminder.source == GameReminders.PROGRAMME_SOURCE
            val game = if (programme) null else outcome.game ?: gameOf(reminder)
            val alert =
                GameReminderAlert(
                    profileId = reminder.profileId,
                    source = reminder.source,
                    gameId = reminder.gameId,
                    homeName = reminder.homeName,
                    awayName = reminder.awayName,
                    competitionName = reminder.competitionName,
                    startEpochSec = reminder.startEpoch,
                    channel =
                        if (programme) {
                            ReminderChannel.decode(reminder.lookupTeam)
                        } else {
                            game?.let { ReminderChannelFinder.find(context, reminder.profileId, it) }
                        },
                )
            if (!GameReminderAlerts.showBanner(alert)) GameReminderNotifications.post(context, alert)
        }

        /**
         * A game made from the stored names when it could not be read again: the guide can still
         * find it by the team names.
         */
        private fun gameOf(reminder: org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity): SportsGame? {
            val team = ReminderTeam.decode(reminder.lookupTeam)?.toTeam() ?: return null

            fun side(name: String) = team.copy(id = "", name = name, shortName = null, altNames = emptyList(), logoUrl = null)
            return SportsGame(
                source = team.source,
                id = reminder.gameId,
                startEpochSec = reminder.startEpoch,
                home = side(reminder.homeName),
                away = reminder.awayName?.let(::side),
                competition = SportsLeague(team.source, team.league.orEmpty(), reminder.competitionName, team.sport),
            )
        }

        internal fun alertFromExtras(
            intent: Intent,
            profileId: String,
            source: String,
            gameId: String,
        ): GameReminderAlert? {
            val home = intent.getStringExtra(EXTRA_HOME) ?: return null
            val start = intent.getLongExtra(EXTRA_START, 0L).takeIf { it > 0 } ?: return null
            return GameReminderAlert(
                profileId = profileId,
                source = source,
                gameId = gameId,
                homeName = home,
                awayName = intent.getStringExtra(EXTRA_AWAY),
                competitionName = intent.getStringExtra(EXTRA_COMPETITION).orEmpty(),
                startEpochSec = start,
                channel = null,
            )
        }
    }
}

/**
 * Sets every reminder's alarm again after a reboot, an app update, a clock or time zone change, or
 * a change of the exact-alarm permission. Enabled only while a reminder is stored
 * ([ReminderAlarms.setRescheduleReceiverOn]), so the app is not started at every boot for nothing.
 * Skipped in crash-loop safe mode and with a `providers.db` from a newer build, as the app's own
 * startup work is.
 */
class GameReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in ACTIONS || SafeMode.isActive || ProvidersDbGuard.isBlocked) return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                GameReminders.getInstance(app).rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            )
        val scope by lazy { AppScopes.create("GameReminderRescheduleReceiver", Dispatchers.IO) }
    }
}
