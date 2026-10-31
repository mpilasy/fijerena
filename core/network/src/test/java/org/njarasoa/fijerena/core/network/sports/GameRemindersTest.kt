package org.njarasoa.fijerena.core.network.sports

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [GameReminders] against a real in-memory `xtream_v2.db`, with fake alarms and a fake schedule:
 * setting and clearing a bell, a team's bell, moved games, and what the alarm does when it fires
 * (the receiver's logic). See the sports teams plan, Phase 7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRemindersTest {
    private class FakeAlarms : ReminderAlarms {
        val set = LinkedHashMap<String, Long>()
        var receiverOn = false

        override fun schedule(
            reminder: GameReminderEntity,
            atMs: Long,
        ) {
            set["${reminder.profileId}/${reminder.gameId}"] = atMs
        }

        override fun cancel(reminder: GameReminderEntity) {
            set.remove("${reminder.profileId}/${reminder.gameId}")
        }

        override fun setRescheduleReceiverOn(on: Boolean) {
            receiverOn = on
        }
    }

    private lateinit var db: XtreamDatabase
    private val alarms = FakeAlarms()
    private val background = TestScope(StandardTestDispatcher())
    private var nowMs = 1_800_000_000_000L
    private var schedule: List<SportsGame> = emptyList()
    private var enabled = true
    private var followed = true
    private lateinit var reminders: GameReminders

    private val arsenal = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer", league = "soccer/eng.1")
    private val chelsea = SportsTeam(SportsSource.ESPN, "soccer/363", "Chelsea", sport = "soccer")
    private val premierLeague = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer")

    private fun game(
        id: String,
        inSec: Long = 86_400,
        state: GameState = GameState.SCHEDULED,
    ) = SportsGame(SportsSource.ESPN, id, nowMs / 1000 + inSec, arsenal, chelsea, premierLeague, state)

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        reminders =
            GameReminders(
                db,
                alarms,
                teamGames = { schedule },
                enabled = { enabled },
                teamFollowed = { _, _, _ -> followed },
                background = background,
                now = { nowMs },
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a bell sets an alarm 10 minutes before the game`() =
        runBlocking {
            val g = game("g1")
            assertTrue(reminders.set(P1, g))
            assertEquals((g.startEpochSec - 600) * 1000, alarms.set["$P1/g1"])
            assertTrue(reminders.isSet(P1, g))
            assertTrue(reminders.observe(P1).first().has(g))
            assertTrue(alarms.receiverOn)
        }

    @Test
    fun `a game that has started or is cancelled gets no reminder`() =
        runBlocking {
            assertFalse(reminders.set(P1, game("g1", inSec = -60)))
            assertFalse(reminders.set(P1, game("g2", state = GameState.CANCELLED)))
            assertTrue(alarms.set.isEmpty())
            assertFalse(alarms.receiverOn)
        }

    @Test
    fun `clearing a bell cancels its alarm`() =
        runBlocking {
            val g = game("g1")
            reminders.set(P1, g)
            reminders.clear(P1, g)
            assertFalse(reminders.isSet(P1, g))
            assertTrue(alarms.set.isEmpty())
            assertFalse(alarms.receiverOn)
        }

    @Test
    fun `a team's bell reminds every coming game and the next ones`() =
        runBlocking {
            schedule = listOf(game("g1"), game("g2", inSec = 172_800))
            reminders.setForTeam(P1, arsenal)
            assertEquals(setOf("$P1/g1", "$P1/g2"), alarms.set.keys)
            assertTrue(reminders.isSetForTeam(P1, arsenal))
            assertTrue(reminders.observe(P1).first().hasTeam(arsenal))

            schedule = schedule + game("g3", inSec = 259_200)
            reminders.refreshTeams()
            assertEquals(setOf("$P1/g1", "$P1/g2", "$P1/g3"), alarms.set.keys)
        }

    @Test
    fun `a game cleared from a team's bell does not come back on refresh`() =
        runBlocking {
            schedule = listOf(game("g1"), game("g2", inSec = 172_800))
            reminders.setForTeam(P1, arsenal)
            reminders.clear(P1, game("g1"))
            reminders.refreshTeams()
            assertEquals(setOf("$P1/g2"), alarms.set.keys)
            assertFalse(reminders.isSet(P1, game("g1")))
        }

    @Test
    fun `clearing a team's bell keeps the games set one by one`() =
        runBlocking {
            reminders.set(P1, game("g9"))
            schedule = listOf(game("g1"))
            reminders.setForTeam(P1, arsenal)
            reminders.clearForTeam(P1, arsenal)
            assertEquals(setOf("$P1/g9"), alarms.set.keys)
            assertFalse(reminders.isSetForTeam(P1, arsenal))
        }

    @Test
    fun `a team unfollowed elsewhere loses its bell on refresh and at its alarms`() =
        runBlocking {
            reminders.set(P1, game("g9"))
            schedule = listOf(game("g1"), game("g2", inSec = 172_800))
            reminders.setForTeam(P1, arsenal)
            // The unfollow arrived by sync, so FollowedSports.unfollow did not run here.
            followed = false
            assertEquals(GameReminders.AlarmOutcome.Dropped, reminders.onAlarm(P1, SportsSource.ESPN.id, "g1"))
            assertFalse(reminders.isSetForTeam(P1, arsenal))
            assertEquals(setOf("$P1/g9"), alarms.set.keys)

            followed = true
            reminders.setForTeam(P1, arsenal)
            followed = false
            reminders.refreshTeams()
            assertFalse(reminders.isSetForTeam(P1, arsenal))
            assertEquals(setOf("$P1/g9"), alarms.set.keys)
        }

    @Test
    fun `nothing is read for a team while the Sports switch is off`() =
        runBlocking {
            enabled = false
            schedule = listOf(game("g1"))
            reminders.setForTeam(P1, arsenal)
            assertTrue(alarms.set.isEmpty())
        }

    @Test
    fun `a schedule read with a moved game moves its alarm`() =
        runBlocking {
            reminders.set(P1, game("g1"))
            val moved = game("g1", inSec = 90_000)
            reminders.moveReminders(listOf(moved))
            assertEquals((moved.startEpochSec - 600) * 1000, alarms.set["$P1/g1"])
        }

    @Test
    fun `rescheduling sets every alarm again and drops games that started`() =
        runBlocking {
            reminders.set(P1, game("g1"))
            reminders.set(P1, game("g2", inSec = 3_600))
            alarms.set.clear()
            nowMs += 3_700_000
            reminders.rescheduleAll()
            assertEquals(setOf("$P1/g1"), alarms.set.keys)
            assertNull(db.gameReminderDao().get(P1, "espn", "g2"))
        }

    @Test
    fun `an alarm whose game kept its time rings once`() =
        runBlocking {
            val g = game("g1", inSec = 600)
            reminders.set(P1, g)
            schedule = listOf(g)
            val outcome = reminders.onAlarm(P1, "espn", "g1")
            assertTrue(outcome is GameReminders.AlarmOutcome.Ring)
            assertEquals(g, (outcome as GameReminders.AlarmOutcome.Ring).game)
            assertNull(db.gameReminderDao().get(P1, "espn", "g1"))
            assertEquals(GameReminders.AlarmOutcome.Dropped, reminders.onAlarm(P1, "espn", "g1"))
        }

    @Test
    fun `an alarm whose game moved later sets a new alarm instead of ringing`() =
        runBlocking {
            reminders.set(P1, game("g1", inSec = 600))
            val moved = game("g1", inSec = 7_800)
            schedule = listOf(moved)
            val outcome = reminders.onAlarm(P1, "espn", "g1")
            assertEquals(GameReminders.AlarmOutcome.Rescheduled((moved.startEpochSec - 600) * 1000), outcome)
            assertEquals(moved.startEpochSec, db.gameReminderDao().get(P1, "espn", "g1")?.startEpoch)
        }

    @Test
    fun `an alarm whose game was postponed is dropped`() =
        runBlocking {
            reminders.set(P1, game("g1", inSec = 600))
            schedule = listOf(game("g1", inSec = 600, state = GameState.POSTPONED))
            assertEquals(GameReminders.AlarmOutcome.Dropped, reminders.onAlarm(P1, "espn", "g1"))
            assertNull(db.gameReminderDao().get(P1, "espn", "g1"))
        }

    @Test
    fun `an alarm whose game can't be read rings at the stored time`() =
        runBlocking {
            reminders.set(P1, game("g1", inSec = 600))
            schedule = emptyList()
            val outcome = reminders.onAlarm(P1, "espn", "g1") as GameReminders.AlarmOutcome.Ring
            assertNull(outcome.game)
            assertEquals("Chelsea", outcome.reminder.awayName)
        }

    @Test
    fun `an alarm rings nothing while the Sports switch is off`() =
        runBlocking {
            reminders.set(P1, game("g1", inSec = 600))
            enabled = false
            assertEquals(GameReminders.AlarmOutcome.Dropped, reminders.onAlarm(P1, "espn", "g1"))
        }

    private companion object {
        const val P1 = "p1"
    }
}
