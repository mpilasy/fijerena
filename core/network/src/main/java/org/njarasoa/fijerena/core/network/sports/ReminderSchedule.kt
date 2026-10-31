package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * When a game reminder rings, and what the alarm does when it fires. Pure, so it is unit tested.
 * See the sports teams plan → Reminders.
 */
object ReminderSchedule {
    /** A reminder rings this long before the game starts. */
    const val LEAD_SEC = 10 * 60L

    /** A game read again whose start differs by less than this has not moved. */
    const val MOVED_SEC = 60L

    /** What the alarm does when it fires. */
    sealed interface Decision {
        /** Ring now, for the game starting at [startEpochSec]. */
        data class Ring(
            val startEpochSec: Long,
        ) : Decision

        /** The game moved: store [startEpochSec] and ring at [triggerAtMs]. */
        data class Reschedule(
            val startEpochSec: Long,
            val triggerAtMs: Long,
        ) : Decision

        /** Do not ring: the game is over, cancelled, postponed or has started. */
        data object Drop : Decision
    }

    /**
     * When the alarm of a game starting at [startEpochSec] rings: [LEAD_SEC] before the start, or
     * at once ([nowMs]) when that is past but the game has not started. Null for a game that has
     * started: it is not scheduled.
     */
    fun triggerAtMs(
        startEpochSec: Long,
        nowMs: Long,
    ): Long? {
        val startMs = startEpochSec * 1000
        if (startMs <= nowMs) return null
        return maxOf(nowMs, (startEpochSec - LEAD_SEC) * 1000)
    }

    /** How the alarm of a reminder is set ([alarm]). */
    sealed interface Alarm {
        /** An exact alarm at [atMs] (`setExactAndAllowWhileIdle`). */
        data class Exact(
            val atMs: Long,
        ) : Alarm

        /** An alarm that rings between [startMs] and [startMs] + [lengthMs] (`setWindow`). */
        data class Window(
            val startMs: Long,
            val lengthMs: Long,
        ) : Alarm

        /** An alarm set for now (`setAndAllowWhileIdle`): Android gives it no delay window. */
        data class Now(
            val atMs: Long,
        ) : Alarm
    }

    /** Without exact alarms, the alarm's window opens this long before the game starts. */
    const val WINDOW_LEAD_MS = 20 * 60_000L

    /** Without exact alarms, the alarm's window is this long: the shortest window Android allows. */
    const val WINDOW_MS = 10 * 60_000L

    /**
     * How the alarm that rings at [triggerAtMs] ([triggerAtMs] of the function above) for a game
     * starting at [startEpochSec] is set at [nowMs]. With [exactAllowed], an exact alarm. Else never
     * a plain inexact alarm, which Android can deliver up to an hour late: a 10-minute window that
     * opens 20 minutes before the start, so the reminder rings 10 to 20 minutes before. When that
     * window has opened already, the alarm is set for now.
     */
    fun alarm(
        triggerAtMs: Long,
        startEpochSec: Long,
        nowMs: Long,
        exactAllowed: Boolean,
    ): Alarm {
        if (exactAllowed) return Alarm.Exact(triggerAtMs)
        val windowStartMs = startEpochSec * 1000 - WINDOW_LEAD_MS
        return if (windowStartMs > nowMs) Alarm.Window(windowStartMs, WINDOW_MS) else Alarm.Now(nowMs)
    }

    /**
     * What the alarm of a reminder stored for [storedStartSec] does at [nowMs], with the game as
     * read again ([reread]; null when it could not be read, then the stored time is trusted).
     */
    fun decide(
        storedStartSec: Long,
        reread: SportsGame?,
        nowMs: Long,
    ): Decision {
        if (reread != null && reread.state in OFF_STATES) return Decision.Drop
        val startSec = reread?.startEpochSec ?: storedStartSec
        val trigger = triggerAtMs(startSec, nowMs)
        val moved = kotlin.math.abs(startSec - storedStartSec) >= MOVED_SEC
        return when {
            // A game that has started is not rung late, except in its first minutes (a device that
            // was asleep): the stored start may be a little early.
            trigger == null && nowMs - startSec * 1000 < LATE_RING_MS && !moved -> Decision.Ring(startSec)

            trigger == null -> Decision.Drop

            moved && trigger > nowMs + EARLY_SLACK_MS -> Decision.Reschedule(startSec, trigger)

            else -> Decision.Ring(startSec)
        }
    }

    // An alarm in Doze can ring a few minutes late: it still rings while the game is this young.
    private const val LATE_RING_MS = 5 * 60_000L

    // An alarm that fires a little early (inexact alarms) still rings: no new alarm for 30 s. An
    // alarm in its window ([alarm], 10 to 20 minutes before the start) rings: the game kept its time.
    private const val EARLY_SLACK_MS = 30_000L

    private val OFF_STATES = setOf(GameState.CANCELLED, GameState.POSTPONED, GameState.FINISHED)
}

/**
 * The team whose schedule has a reminded game (`game_reminder.lookupTeam`): what `SportsData`
 * needs to read that schedule again.
 */
@Serializable
data class ReminderTeam(
    val source: String,
    val id: String,
    val name: String,
    val sport: String,
    val league: String? = null,
) {
    /** The team, or null when its source is not known to this build. */
    fun toTeam(): SportsTeam? =
        SportsSource.byId(source)?.let { SportsTeam(source = it, id = id, name = name, sport = sport, league = league) }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(team: SportsTeam): ReminderTeam = ReminderTeam(team.source.id, team.id, team.name, team.sport, team.league)

        /** [text] read back; null when it is not a stored team. */
        fun decode(text: String): ReminderTeam? =
            try {
                json.decodeFromString(serializer(), text)
            } catch (e: IllegalArgumentException) {
                // cancellation-ok: not a suspend call
                null
            }
    }
}
