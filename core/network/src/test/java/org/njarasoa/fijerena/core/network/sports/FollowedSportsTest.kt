package org.njarasoa.fijerena.core.network.sports

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.xtream.db.FollowedSportEntity
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FollowedSports] stores teams and leagues per profile and gives them back as the shared models,
 * against a real in-memory `xtream_v2.db`. See the sports teams plan, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowedSportsTest {
    private lateinit var db: XtreamDatabase
    private var clock = 100L
    private lateinit var followed: FollowedSports

    private val arsenal =
        SportsTeam(
            source = SportsSource.ESPN,
            id = "359",
            name = "Arsenal",
            shortName = "ARS",
            altNames = listOf("Gunners", "London"),
            sport = "soccer",
            league = "soccer/eng.1",
            logoUrl = "https://a/359.png",
        )
    private val premierLeague = SportsLeague(SportsSource.ESPN, "soccer/eng.1", "Premier League", "soccer", "https://a/pl.png")

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        followed = FollowedSports(db, now = { clock })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a followed team and league come back as they were given`() =
        runBlocking {
            followed.follow(PROFILE, arsenal)
            clock = 200
            followed.follow(PROFILE, premierLeague)

            val state = followed.observe(PROFILE).first()
            assertEquals(listOf(arsenal), state.teams)
            assertEquals(listOf(premierLeague), state.leagues)
            assertTrue(followed.isFollowed(PROFILE, arsenal))
            assertTrue(followed.isFollowed(PROFILE, premierLeague))
            assertFalse(followed.isFollowed("other", arsenal))
        }

    @Test
    fun `following again refreshes the details and keeps the first time`() =
        runBlocking {
            followed.follow(PROFILE, arsenal)
            clock = 500
            followed.follow(PROFILE, arsenal.copy(logoUrl = "https://a/new.png"))

            val row = db.followedSportDao().get(PROFILE, "TEAM", "espn", "359")!!
            assertEquals(100L, row.createdAt)
            assertEquals("https://a/new.png", row.logoUrl)
        }

    @Test
    fun `unfollowing removes only that item`() =
        runBlocking {
            followed.follow(PROFILE, arsenal)
            followed.follow(PROFILE, premierLeague)

            followed.unfollow(PROFILE, arsenal)

            val state = followed.get(PROFILE)
            assertEquals(emptyList<SportsTeam>(), state.teams)
            assertEquals(listOf(premierLeague), state.leagues)
            assertFalse(followed.isFollowed(PROFILE, arsenal))
        }

    @Test
    fun `a row from a newer build with an unknown source or kind is left out`() =
        runBlocking {
            followed.follow(PROFILE, arsenal)
            val row = db.followedSportDao().get(PROFILE, "TEAM", "espn", "359")!!
            db.followedSportDao().upsert(row.copy(source = "newsource"))
            db.followedSportDao().upsert(row.copy(kind = "PLAYER"))

            val state = followed.get(PROFILE)
            assertEquals(listOf(arsenal), state.teams)
            assertTrue(state.leagues.isEmpty())
            assertEquals(3, db.followedSportDao().getAll(PROFILE).size)
        }

    @Test
    fun `unfollowing a team clears its bell and the reminders it set, bells set by hand on other teams' games stay`() =
        runBlocking {
            val alarms =
                object : ReminderAlarms {
                    val set = HashSet<String>()

                    override fun schedule(
                        reminder: GameReminderEntity,
                        atMs: Long,
                    ) {
                        set += reminder.gameId
                    }

                    override fun cancel(reminder: GameReminderEntity) {
                        set -= reminder.gameId
                    }

                    override fun setRescheduleReceiverOn(on: Boolean) = Unit
                }
            val nowMs = 1_800_000_000_000L
            val chelsea = SportsTeam(SportsSource.ESPN, "363", "Chelsea", sport = "soccer")
            val spurs = SportsTeam(SportsSource.ESPN, "367", "Tottenham Hotspur", sport = "soccer")

            fun game(
                id: String,
                home: SportsTeam,
                away: SportsTeam,
            ) = SportsGame(SportsSource.ESPN, id, nowMs / 1000 + 86_400, home, away, premierLeague)
            val arsenalGames = listOf(game("a1", arsenal, chelsea), game("a2", chelsea, arsenal))
            val reminders =
                GameReminders(db, alarms, { team -> if (team.id == arsenal.id) arsenalGames else emptyList() }, now = { nowMs })
            val withReminders = FollowedSports(db, { clock }) { p, s, id -> reminders.clearForTeam(p, s, id) }

            withReminders.follow(PROFILE, arsenal)
            withReminders.follow(PROFILE, chelsea)
            reminders.setForTeam(PROFILE, arsenal)
            reminders.set(PROFILE, game("c1", chelsea, spurs))
            assertEquals(setOf("a1", "a2", "c1"), alarms.set)

            withReminders.unfollow(PROFILE, arsenal)

            assertEquals(setOf("c1"), alarms.set)
            assertFalse(reminders.isSetForTeam(PROFILE, arsenal))
            assertEquals(setOf("espn/c1"), reminders.observe(PROFILE).first().games)
            // A league is not a team: unfollowing it touches no reminder.
            withReminders.follow(PROFILE, premierLeague)
            withReminders.unfollow(PROFILE, premierLeague)
            assertEquals(setOf("c1"), alarms.set)
        }

    @Test
    fun `nothing followed is empty`() =
        runBlocking {
            assertTrue(followed.get(PROFILE).isEmpty)
            assertEquals(FollowedSports.Followed(), FollowedSports.toFollowed(emptyList<FollowedSportEntity>()))
        }

    private companion object {
        const val PROFILE = "default"
    }
}
