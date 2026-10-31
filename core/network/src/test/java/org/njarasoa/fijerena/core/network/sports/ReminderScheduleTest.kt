package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ReminderSchedule]: when a reminder rings and what its alarm does. See the sports teams plan → Reminders. */
class ReminderScheduleTest {
    private val start = 1_800_000_000L
    private val startMs = start * 1000

    private fun game(
        startSec: Long = start,
        state: GameState = GameState.SCHEDULED,
    ) = SportsGame(
        source = SportsSource.ESPN,
        id = "1",
        startEpochSec = startSec,
        home = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer"),
        away = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer"),
        competition = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer"),
        state = state,
    )

    @Test
    fun `the alarm rings 10 minutes before the start`() {
        assertEquals(startMs - 600_000, ReminderSchedule.triggerAtMs(start, startMs - 3_600_000))
    }

    @Test
    fun `less than 10 minutes before the start the alarm rings at once`() {
        val now = startMs - 120_000
        assertEquals(now, ReminderSchedule.triggerAtMs(start, now))
    }

    @Test
    fun `a game that has started gets no alarm`() {
        assertNull(ReminderSchedule.triggerAtMs(start, startMs))
        assertNull(ReminderSchedule.triggerAtMs(start, startMs + 60_000))
    }

    @Test
    fun `with exact alarms the alarm is exact at its trigger`() {
        val now = startMs - 3_600_000
        assertEquals(
            ReminderSchedule.Alarm.Exact(startMs - 600_000),
            ReminderSchedule.alarm(startMs - 600_000, start, now, exactAllowed = true),
        )
    }

    @Test
    fun `without exact alarms the alarm rings in a 10-minute window 10 to 20 minutes before the start`() {
        val now = startMs - 3 * 3_600_000
        assertEquals(
            ReminderSchedule.Alarm.Window(startMs - 1_200_000, 600_000),
            ReminderSchedule.alarm(startMs - 600_000, start, now, exactAllowed = false),
        )
    }

    @Test
    fun `without exact alarms an alarm whose window has opened is set for now`() {
        val now = startMs - 900_000
        assertEquals(ReminderSchedule.Alarm.Now(now), ReminderSchedule.alarm(startMs - 600_000, start, now, exactAllowed = false))
        val late = startMs - 120_000
        assertEquals(ReminderSchedule.Alarm.Now(late), ReminderSchedule.alarm(late, start, late, exactAllowed = false))
    }

    @Test
    fun `an alarm that rings early in its window rings`() {
        assertEquals(ReminderSchedule.Decision.Ring(start), ReminderSchedule.decide(start, game(), startMs - 1_200_000))
    }

    @Test
    fun `an alarm whose game kept its time rings`() {
        val decision = ReminderSchedule.decide(start, game(), startMs - 600_000)
        assertEquals(ReminderSchedule.Decision.Ring(start), decision)
    }

    @Test
    fun `an alarm whose game could not be read rings at the stored time`() {
        assertEquals(ReminderSchedule.Decision.Ring(start), ReminderSchedule.decide(start, null, startMs - 600_000))
    }

    @Test
    fun `a game moved later gets a new alarm instead of ringing`() {
        val later = start + 7_200
        val decision = ReminderSchedule.decide(start, game(startSec = later), startMs - 600_000)
        assertEquals(ReminderSchedule.Decision.Reschedule(later, later * 1000 - 600_000), decision)
    }

    @Test
    fun `a game moved earlier inside the 10 minutes rings at once with its new time`() {
        val earlier = start - 300
        val decision = ReminderSchedule.decide(start, game(startSec = earlier), earlier * 1000 - 120_000)
        assertEquals(ReminderSchedule.Decision.Ring(earlier), decision)
    }

    @Test
    fun `a moved game that has already started is dropped`() {
        val earlier = start - 3_600
        assertEquals(ReminderSchedule.Decision.Drop, ReminderSchedule.decide(start, game(startSec = earlier), startMs - 600_000))
    }

    @Test
    fun `a cancelled, postponed or finished game is dropped`() {
        for (state in listOf(GameState.CANCELLED, GameState.POSTPONED, GameState.FINISHED)) {
            assertEquals(ReminderSchedule.Decision.Drop, ReminderSchedule.decide(start, game(state = state), startMs - 600_000))
        }
    }

    @Test
    fun `an alarm a few minutes late still rings, a much later one does not`() {
        assertEquals(ReminderSchedule.Decision.Ring(start), ReminderSchedule.decide(start, game(), startMs + 120_000))
        assertEquals(ReminderSchedule.Decision.Drop, ReminderSchedule.decide(start, game(), startMs + 600_000))
    }

    @Test
    fun `a stored team reads back`() {
        val team = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer", league = "soccer/eng.1")
        val read = ReminderTeam.decode(ReminderTeam.of(team).encode())?.toTeam()
        assertEquals(team, read)
        assertNull(ReminderTeam.decode("not json"))
        assertNull(ReminderTeam("nope", "1", "X", "soccer").toTeam())
    }
}
