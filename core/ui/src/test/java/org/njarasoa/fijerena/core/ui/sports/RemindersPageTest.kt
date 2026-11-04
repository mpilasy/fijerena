package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.GameReminders
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.ReminderTeam
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity

/** [RemindersPage]: the Reminders page's sections from the profile's reminder rows (the Reminders page plan). */
class RemindersPageTest {
    private val arsenal = SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", sport = "soccer")
    private val team = ReminderTeam.of(arsenal).encode()
    private val teamKey = GameReminderEntity.teamKey("espn", "soccer/359")

    private fun game(
        id: String,
        start: Long,
        key: String? = null,
    ) = GameReminderEntity("p1", "espn", id, start, "Arsenal", "Chelsea", "Premier League", key, team, 0)

    private val rule =
        GameReminderEntity(
            "p1",
            "espn",
            GameReminderEntity.teamRuleGameId("soccer/359"),
            0,
            "Arsenal",
            null,
            "Premier League",
            teamKey,
            team,
            0,
        )

    private val programme =
        GameReminderEntity(
            "p1",
            GameReminders.PROGRAMME_SOURCE,
            "1:7:150",
            150,
            "News",
            null,
            "BBC One",
            null,
            ReminderChannel(1, 7, "news", "BBC One").encode(),
            0,
        )

    @Test
    fun `games and programmes together, the soonest first, muted games left out`() {
        val page = RemindersPage.build(listOf(game("late", 300), programme, game("soon", 100), game("muted", -200, teamKey)))

        assertEquals(listOf(100L, 150L, 300L), page.upcoming.map { it.startEpochSec })
        val news = page.upcoming[1] as ReminderItem.Programme
        assertEquals("News", news.title)
        assertEquals("BBC One", news.channel.name)
        val soon = page.upcoming[0] as ReminderItem.Game
        assertEquals("Chelsea", soon.awayName)
        assertEquals(arsenal.id, soon.team?.id)
    }

    @Test
    fun `a team rule shows its next game and how many coming games it has`() {
        val page =
            RemindersPage.build(
                listOf(rule, game("g2", 500, teamKey), game("g1", 400, teamKey), game("own", 100), game("muted", -50, teamKey)),
            )

        val item = page.teams.single()
        assertEquals("Arsenal", item.teamName)
        assertEquals("soccer/359", item.teamId)
        assertEquals(400L, item.nextStartEpochSec)
        assertEquals(2, item.gameCount)
        // A game of the rule is in Upcoming too, marked so Remove mutes it.
        assertTrue((page.upcoming.first { (it as ReminderItem.Game).gameId == "g1" } as ReminderItem.Game).fromTeamRule)
    }

    @Test
    fun `no rows is the empty page`() {
        assertTrue(RemindersPage.build(emptyList()).isEmpty)
    }

    @Test
    fun `a reminder rings at the device's lead before the start, not before now`() {
        val item = RemindersPage.build(listOf(game("g", 10_000))).upcoming.single()

        assertEquals(10_000L - 600, RemindersPage.ringsAtEpochSec(item, leadMinutes = 10, nowSec = 0))
        assertEquals(9_500L, RemindersPage.ringsAtEpochSec(item, leadMinutes = 60, nowSec = 9_500))
    }
}
