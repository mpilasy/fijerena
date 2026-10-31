package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The game reminders' storage against a real in-memory `xtream_v2.db` with the sync triggers
 * installed: rows by game, team and profile, and nothing queued for live sync. See the sports
 * teams plan, Phase 7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameReminderDaoTest {
    private lateinit var db: XtreamDatabase
    private lateinit var dao: GameReminderDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = XtreamSyncTriggers.install(db)
                    },
                ).build()
        dao = db.gameReminderDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(
        gameId: String,
        start: Long = 1_000,
        profileId: String = "p1",
        teamKey: String? = null,
    ) = GameReminderEntity(
        profileId = profileId,
        source = "espn",
        gameId = gameId,
        startEpoch = start,
        homeName = "Arsenal",
        awayName = "Chelsea",
        competitionName = "Premier League",
        teamKey = teamKey,
        lookupTeam = "{}",
        createdAt = 1,
    )

    @Test
    fun `a reminder is stored, read and deleted`() =
        runBlocking {
            dao.upsert(row("g1"))
            assertEquals("Chelsea", dao.get("p1", "espn", "g1")?.awayName)
            dao.delete("p1", "espn", "g1")
            assertNull(dao.get("p1", "espn", "g1"))
        }

    @Test
    fun `reminders are not queued for live sync`() =
        runBlocking {
            dao.upsert(row("g1"))
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_version").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }

    @Test
    fun `games, muted games and team rows are told apart`() =
        runBlocking {
            dao.upsert(row("g1", start = 1_000, teamKey = "espn:soccer/359"))
            dao.upsert(row("g2", start = -2_000, teamKey = "espn:soccer/359"))
            dao.upsert(row(GameReminderEntity.teamRuleGameId("soccer/359"), start = 0, teamKey = "espn:soccer/359"))

            assertEquals(listOf("g1"), dao.allGames().map { it.gameId })
            assertEquals(listOf("g2"), dao.allMuted().map { it.gameId })
            assertEquals(listOf("team:soccer/359"), dao.allTeamRules().map { it.gameId })
            assertTrue(dao.allTeamRules().single().isTeamRule)
            assertEquals(listOf("g1"), dao.forGames(listOf("g1", "g2", "g9")).map { it.gameId })
            assertEquals(3, dao.observeAll("p1").first().size)
        }

    @Test
    fun `a team's rows go together and other reminders stay`() =
        runBlocking {
            dao.upsert(row("g1", teamKey = "espn:soccer/359"))
            dao.upsert(row(GameReminderEntity.teamRuleGameId("soccer/359"), start = 0, teamKey = "espn:soccer/359"))
            dao.upsert(row("g2"))

            assertEquals(2, dao.forTeam("p1", "espn:soccer/359").size)
            dao.deleteTeam("p1", "espn:soccer/359")
            assertEquals(listOf("g2"), dao.observeAll("p1").first().map { it.gameId })
        }

    @Test
    fun `deleting a profile deletes only its reminders`() =
        runBlocking {
            dao.upsert(row("g1", profileId = "p1"))
            dao.upsert(row("g1", profileId = "p2"))
            dao.deleteProfile("p1")
            assertNull(dao.get("p1", "espn", "g1"))
            assertEquals(1_000L, dao.get("p2", "espn", "g1")?.startEpoch)
        }

    @Test
    fun `a moved game's start is updated`() =
        runBlocking {
            dao.upsert(row("g1"))
            dao.updateStart("p1", "espn", "g1", 5_000)
            assertEquals(5_000L, dao.get("p1", "espn", "g1")?.startEpoch)
        }
}
