package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes

/**
 * Game reminders per profile (`game_reminder`, local, not synced): an alarm rings the device's
 * lead time ([leadMinutes], `AppSettings.reminderLeadMinutes`) before a game. See the sports teams plan → Reminders.
 *
 * - [set] / [clear] / [isSet] / [observe]: one game's bell.
 * - [setForTeam] / [clearForTeam] / [isSetForTeam]: "every game of this team". A standing team row
 *   keeps the choice; [refreshTeams] adds the team's new games. It runs on each app start
 *   ([onAppStart]) and when the bell is set, on the team's cached schedule (`SportsData`, 6 hours;
 *   1 hour with a game in the next 2 days), so a new game is added at the next start of the app.
 * - [onGamesRead]: every schedule `SportsData` reads moves the reminders of the games whose
 *   start changed.
 * - [rescheduleAll]: after a reboot, an app update or a clock change, and on each app start.
 * - [onAlarm]: what [GameReminderReceiver] does when an alarm fires. The game is read again
 *   ([reread], from the cache when fresh): a moved game gets a new alarm instead of ringing.
 *
 * A reminder that rang, or whose game is over, is deleted. Writes are serialised by one lock.
 *
 * A guide programme's reminder ([setProgramme]) is a row of [PROGRAMME_SOURCE]: it rings at its
 * stored start, with or without the Sports switch, and tunes its own channel.
 */
class GameReminders internal constructor(
    private val db: XtreamDatabase,
    private val alarms: ReminderAlarms,
    private val teamGames: suspend (SportsTeam) -> List<SportsGame>,
    private val enabled: () -> Boolean = { true },
    private val teamFollowed: suspend (profileId: String, source: SportsSource, teamId: String) -> Boolean = { _, _, _ -> true },
    private val background: CoroutineScope = AppScopes.create("GameReminders", Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val leadMinutes: () -> Int = { ReminderSchedule.DEFAULT_LEAD_MINUTES },
) {
    /** What a profile has reminders for: games as "source/gameId", teams as their team keys. */
    data class Reminded(
        val games: Set<String> = emptySet(),
        val teams: Set<String> = emptySet(),
    ) {
        fun has(game: SportsGame): Boolean = key(game.source.id, game.id) in games

        fun hasTeam(team: SportsTeam): Boolean = GameReminderEntity.teamKey(team.source.id, team.id) in teams

        fun hasProgramme(programme: ProgrammeReminder): Boolean = key(PROGRAMME_SOURCE, programme.id) in games
    }

    /** What the alarm did. [Ring] carries the alert to show. */
    sealed interface AlarmOutcome {
        data class Ring(
            val reminder: GameReminderEntity,
            val game: SportsGame?,
        ) : AlarmOutcome

        data class Rescheduled(
            val triggerAtMs: Long,
        ) : AlarmOutcome

        data object Dropped : AlarmOutcome
    }

    private val dao get() = db.gameReminderDao()
    private val lock = Mutex()

    /** [profileId]'s reminders; emits again on every change. */
    fun observe(profileId: String): Flow<Reminded> =
        dao.observeAll(profileId).map { rows ->
            Reminded(
                games = rows.filter { it.startEpoch > 0 }.mapTo(HashSet()) { key(it.source, it.gameId) },
                teams = rows.filter { it.isTeamRule }.mapNotNullTo(HashSet()) { it.teamKey },
            )
        }

    suspend fun isSet(
        profileId: String,
        game: SportsGame,
    ): Boolean = (dao.get(profileId, game.source.id, game.id)?.startEpoch ?: 0) > 0

    /**
     * Sets a reminder for [game]. False, and nothing stored, when the game has started (or is
     * cancelled, postponed or over).
     */
    suspend fun set(
        profileId: String,
        game: SportsGame,
    ): Boolean =
        lock.withLock {
            val reminder = entityOf(profileId, game, teamKey = null, lookup = game.home)
            schedule(reminder)
        }

    /** Sets a reminder for a guide programme. False, and nothing stored, when it has started. */
    suspend fun setProgramme(
        profileId: String,
        programme: ProgrammeReminder,
    ): Boolean =
        lock.withLock {
            schedule(
                GameReminderEntity(
                    profileId = profileId,
                    source = PROGRAMME_SOURCE,
                    gameId = programme.id,
                    startEpoch = programme.startEpochSec,
                    homeName = programme.title,
                    competitionName = programme.channel.name,
                    lookupTeam = programme.channel.encode(),
                    createdAt = now(),
                ),
            )
        }

    /**
     * Clears [game]'s reminder. One that came from a team's bell is kept muted (its start stored
     * negative, no alarm), so the team's next refresh does not bring it back.
     */
    suspend fun clear(
        profileId: String,
        game: SportsGame,
    ) = clear(profileId, game.source, game.id)

    suspend fun clear(
        profileId: String,
        source: SportsSource,
        gameId: String,
    ) = lock.withLock {
        val reminder = dao.get(profileId, source.id, gameId)
        if (reminder != null && reminder.startEpoch > 0) {
            if (reminder.teamKey != null) {
                alarms.cancel(reminder)
                dao.updateStart(profileId, source.id, gameId, -reminder.startEpoch)
            } else {
                remove(reminder)
            }
        }
        updateRescheduleReceiver()
    }

    suspend fun isSetForTeam(
        profileId: String,
        team: SportsTeam,
    ): Boolean = dao.get(profileId, team.source.id, GameReminderEntity.teamRuleGameId(team.id)) != null

    /** Reminders for every coming game of [team], now and as its schedule grows. */
    suspend fun setForTeam(
        profileId: String,
        team: SportsTeam,
    ) {
        lock.withLock {
            dao.upsert(
                GameReminderEntity(
                    profileId = profileId,
                    source = team.source.id,
                    gameId = GameReminderEntity.teamRuleGameId(team.id),
                    startEpoch = 0,
                    homeName = team.name,
                    competitionName = team.leagueName.orEmpty(),
                    teamKey = GameReminderEntity.teamKey(team.source.id, team.id),
                    lookupTeam = ReminderTeam.of(team).encode(),
                    createdAt = now(),
                ),
            )
            updateRescheduleReceiver()
        }
        refreshTeam(profileId, team)
    }

    /** Clears [team]'s standing row and every reminder it set; reminders set game by game stay. */
    suspend fun clearForTeam(
        profileId: String,
        team: SportsTeam,
    ) = clearForTeam(profileId, team.source, team.id)

    /** See [clearForTeam]; for the team [teamId] of [source] (an unfollowed team, [FollowedSports.unfollow]). */
    suspend fun clearForTeam(
        profileId: String,
        source: SportsSource,
        teamId: String,
    ) = lock.withLock {
        val teamKey = GameReminderEntity.teamKey(source.id, teamId)
        dao.forTeam(profileId, teamKey).forEach { if (it.startEpoch > 0) alarms.cancel(it) }
        dao.deleteTeam(profileId, teamKey)
        updateRescheduleReceiver()
    }

    /** Clears a guide programme's reminder ([setProgramme]). */
    suspend fun clearProgramme(
        profileId: String,
        programmeId: String,
    ) = lock.withLock {
        dao.get(profileId, PROGRAMME_SOURCE, programmeId)?.let { remove(it) }
        updateRescheduleReceiver()
    }

    /** Clears every reminder and team rule of [profileId], with their alarms (the Reminders page's Clear All). */
    suspend fun clearAll(profileId: String) =
        lock.withLock {
            dao.getAll(profileId).filter { it.startEpoch > 0 }.forEach { alarms.cancel(it) }
            dao.deleteProfile(profileId)
            updateRescheduleReceiver()
        }

    /** The device rings reminders [minutes] before the start from now on; the alarms already set move. */
    suspend fun setLeadMinutes(
        settings: AppSettings,
        minutes: Int,
    ) {
        settings.reminderLeadMinutes = minutes
        rescheduleAll()
    }

    /** On each app start (behind safe mode): every alarm set again, then the teams' new games. */
    suspend fun onAppStart() {
        rescheduleAll()
        refreshTeams()
    }

    /** Sets every reminder's alarm again; deletes those whose game has started, muted ones too. */
    suspend fun rescheduleAll() =
        lock.withLock {
            val nowMs = now()
            for (reminder in dao.allGames()) {
                val trigger = ReminderSchedule.triggerAtMs(reminder.startEpoch, nowMs, leadMinutes())
                if (trigger == null) remove(reminder) else alarms.schedule(reminder, trigger)
            }
            dao.allMuted().filter { -it.startEpoch * 1000 <= nowMs }.forEach { remove(it) }
            updateRescheduleReceiver()
        }

    /** Adds the new games of every team with a bell, of every profile. Nothing when the Sports switch is off. */
    suspend fun refreshTeams() {
        if (!enabled()) return
        val rules = dao.allTeamRules()
        rules
            .groupBy { it.lookupTeam }
            .forEach { (lookup, rows) ->
                val team = ReminderTeam.decode(lookup)?.toTeam() ?: return@forEach
                // A team bell lives only while the team is followed: an unfollow received by sync
                // does not pass through FollowedSports.unfollow on this device.
                val (followed, unfollowed) = rows.partition { teamFollowed(it.profileId, team.source, team.id) }
                unfollowed.forEach { clearForTeam(it.profileId, team.source, team.id) }
                if (followed.isEmpty()) return@forEach
                val games = readOrEmpty(team)
                followed.forEach { addTeamGames(it.profileId, team, games) }
            }
    }

    private suspend fun refreshTeam(
        profileId: String,
        team: SportsTeam,
    ) {
        if (enabled()) addTeamGames(profileId, team, readOrEmpty(team))
    }

    private suspend fun addTeamGames(
        profileId: String,
        team: SportsTeam,
        games: List<SportsGame>,
    ) = lock.withLock {
        val teamKey = GameReminderEntity.teamKey(team.source.id, team.id)
        // Still wanted: the bell may have been cleared while the schedule was read.
        if (dao.get(profileId, team.source.id, GameReminderEntity.teamRuleGameId(team.id)) == null) return@withLock
        for (game in games) {
            val existing = dao.get(profileId, game.source.id, game.id)
            if (existing == null) {
                schedule(entityOf(profileId, game, teamKey, lookup = team))
            } else if (existing.startEpoch > 0 && existing.startEpoch != game.startEpochSec) {
                move(existing, game.startEpochSec)
            }
        }
    }

    /**
     * Games just read by `SportsData` (any screen, any team or league): the reminders of those whose
     * start changed are moved. Runs in the background.
     */
    fun onGamesRead(games: List<SportsGame>) {
        if (games.isNotEmpty()) background.launch { moveReminders(games) }
    }

    /** [onGamesRead]'s work: moves the reminders of [games] whose start changed. */
    internal suspend fun moveReminders(games: List<SportsGame>) =
        lock.withLock {
            val byKey = games.associateBy { key(it.source.id, it.id) }
            for (reminder in dao.forGames(games.map { it.id }.distinct())) {
                val game = byKey[key(reminder.source, reminder.gameId)] ?: continue
                if (game.startEpochSec != reminder.startEpoch && game.state == GameState.SCHEDULED) move(reminder, game.startEpochSec)
            }
        }

    /**
     * The alarm of [profileId]'s reminder for [source]'s [gameId] fired. The game is read again;
     * the reminder rings ([AlarmOutcome.Ring], and is deleted), moves to the game's new time, or is
     * dropped. A reminder no longer stored (cleared, or its profile deleted) is dropped.
     */
    suspend fun onAlarm(
        profileId: String,
        source: String,
        gameId: String,
    ): AlarmOutcome {
        // A muted reminder's alarm was cancelled; one firing anyway does nothing.
        val reminder = dao.get(profileId, source, gameId)?.takeIf { it.startEpoch > 0 } ?: return AlarmOutcome.Dropped
        if (source == PROGRAMME_SOURCE) {
            // A guide programme is not read again: it rings at its stored start.
            return lock.withLock {
                val decision = ReminderSchedule.decide(reminder.startEpoch, null, now())
                remove(reminder)
                updateRescheduleReceiver()
                if (decision is ReminderSchedule.Decision.Ring) AlarmOutcome.Ring(reminder, null) else AlarmOutcome.Dropped
            }
        }
        if (!enabled()) {
            lock.withLock { remove(reminder) }
            return AlarmOutcome.Dropped
        }
        if (reminder.teamKey != null && !teamOfReminderFollowed(reminder)) {
            ReminderTeam.decode(reminder.lookupTeam)?.toTeam()?.let { clearForTeam(profileId, it.source, it.id) }
            return AlarmOutcome.Dropped
        }
        val game = reread(reminder)
        return lock.withLock {
            when (val decision = ReminderSchedule.decide(reminder.startEpoch, game, now())) {
                is ReminderSchedule.Decision.Ring -> {
                    remove(reminder)
                    AlarmOutcome.Ring(reminder.copy(startEpoch = decision.startEpochSec), game)
                }

                is ReminderSchedule.Decision.Reschedule -> {
                    val moved = reminder.copy(startEpoch = decision.startEpochSec)
                    dao.updateStart(reminder.profileId, reminder.source, reminder.gameId, decision.startEpochSec)
                    alarms.schedule(moved, decision.triggerAtMs)
                    AlarmOutcome.Rescheduled(decision.triggerAtMs)
                }

                ReminderSchedule.Decision.Drop -> {
                    remove(reminder)
                    AlarmOutcome.Dropped
                }
            }.also { updateRescheduleReceiver() }
        }
    }

    /** Whether the team whose bell set [reminder] is still followed by its profile. */
    private suspend fun teamOfReminderFollowed(reminder: GameReminderEntity): Boolean {
        val team = ReminderTeam.decode(reminder.lookupTeam)?.toTeam() ?: return true
        return teamFollowed(reminder.profileId, team.source, team.id)
    }

    /** The game read again from its team's schedule; null when it can't be read in time or is not there. */
    private suspend fun reread(reminder: GameReminderEntity): SportsGame? {
        val team = ReminderTeam.decode(reminder.lookupTeam)?.toTeam() ?: return null
        return withTimeoutOrNull(REREAD_TIMEOUT_MS) { readOrEmpty(team) }
            ?.firstOrNull { it.source.id == reminder.source && it.id == reminder.gameId }
    }

    private suspend fun readOrEmpty(team: SportsTeam): List<SportsGame> =
        try {
            teamGames(team)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reminders: ${team.name}'s games: ${e.message}")
            emptyList()
        }

    // Under the lock.
    private suspend fun schedule(reminder: GameReminderEntity): Boolean {
        val trigger = ReminderSchedule.triggerAtMs(reminder.startEpoch, now(), leadMinutes()) ?: return false
        val createdAt = dao.get(reminder.profileId, reminder.source, reminder.gameId)?.createdAt ?: reminder.createdAt
        val stored = reminder.copy(createdAt = createdAt)
        dao.upsert(stored)
        alarms.schedule(stored, trigger)
        updateRescheduleReceiver()
        return true
    }

    // Under the lock.
    private suspend fun move(
        reminder: GameReminderEntity,
        startEpochSec: Long,
    ) {
        val moved = reminder.copy(startEpoch = startEpochSec)
        val trigger = ReminderSchedule.triggerAtMs(startEpochSec, now(), leadMinutes())
        if (trigger == null) {
            remove(reminder)
        } else {
            dao.updateStart(reminder.profileId, reminder.source, reminder.gameId, startEpochSec)
            alarms.schedule(moved, trigger)
        }
    }

    // Under the lock.
    private suspend fun remove(reminder: GameReminderEntity) {
        if (reminder.startEpoch > 0) alarms.cancel(reminder)
        dao.delete(reminder.profileId, reminder.source, reminder.gameId)
    }

    private suspend fun updateRescheduleReceiver() {
        alarms.setRescheduleReceiverOn(dao.allGames().isNotEmpty() || dao.allTeamRules().isNotEmpty())
    }

    private fun entityOf(
        profileId: String,
        game: SportsGame,
        teamKey: String?,
        lookup: SportsTeam,
    ): GameReminderEntity =
        GameReminderEntity(
            profileId = profileId,
            source = game.source.id,
            gameId = game.id,
            // A game not scheduled any more gets no alarm: schedule() refuses a start of 0.
            startEpoch = if (game.state == GameState.SCHEDULED) game.startEpochSec else 0,
            homeName = game.home.name,
            awayName = game.away?.name,
            competitionName = game.competition.name,
            teamKey = teamKey,
            lookupTeam = ReminderTeam.of(lookup).encode(),
            createdAt = now(),
        )

    companion object {
        private const val TAG = "GameReminders"

        /** The `game_reminder.source` of a guide programme's reminder; its channel is in `lookupTeam`. */
        const val PROGRAMME_SOURCE = "guide"
        private const val REREAD_TIMEOUT_MS = 5_000L

        internal fun key(
            source: String,
            gameId: String,
        ) = "$source/$gameId"

        @Volatile
        private var instance: GameReminders? = null

        fun getInstance(context: Context): GameReminders =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val settings = AppSettings(app)
                    GameReminders(
                        db = XtreamDatabase.getInstance(app),
                        alarms = AndroidReminderAlarms(app),
                        teamGames = { team -> SportsData.getInstance(app).teamGames(team) },
                        enabled = { settings.showSports },
                        teamFollowed = { profileId, source, teamId ->
                            FollowedSports.getInstance(app).isFollowed(profileId, FollowKind.TEAM, source, teamId)
                        },
                        leadMinutes = { settings.reminderLeadMinutes },
                    )
                }.also { instance = it }
            }
    }
}
