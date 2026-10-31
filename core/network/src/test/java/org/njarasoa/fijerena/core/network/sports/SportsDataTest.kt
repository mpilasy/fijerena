package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** [SportsData] on the saved answers: no network. */
class SportsDataTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val calls = mutableListOf<String>()

    // The saved answer for each URL; an unknown URL fails as a source that is down.
    private val transport =
        SportsTransport { url ->
            synchronized(calls) { calls += url }
            when {
                "common/v3/search" in url && "query=ajax" in url -> sportsFixture("espn_search_team_ajax.json")
                "common/v3/search" in url -> sportsFixture("espn_search_team.json")
                "search/v2" in url && "query=euro&" in url -> sportsFixture("espn_search_league_euro.json")
                "search/v2" in url && "European" in url -> sportsFixture("espn_search_league_european_championship.json")
                "search/v2" in url -> sportsFixture("espn_search_league.json")
                "leagues/dropdown?sport=soccer" in url -> sportsFixture("espn_leagues_soccer.json")
                "soccer/all/teams/359/schedule" in url -> sportsFixture("espn_schedule_soccer_all.json")
                "hockey/nhl/teams/1/schedule" in url -> sportsFixture("espn_schedule_nhl.json")
                "club-schedule-season/BOS" in url -> sportsFixture("nhl_club_schedule.json")
                else -> throw IOException("down: $url")
            }
        }

    private fun data(
        nowIso: String,
        enabled: Boolean = true,
    ) = SportsData(
        api = SportsApi(transport),
        cache = SportsCache(folder.root),
        enabled = { enabled },
        now = { epoch(nowIso) * 1000 },
    )

    private val arsenal =
        SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", shortName = "ARS", sport = "soccer", league = "soccer/eng.1")
    private val boston =
        SportsTeam(SportsSource.ESPN, "hockey/nhl/1", "Boston Bruins", shortName = "BOS", sport = "hockey", league = "hockey/nhl")

    @Test
    fun `a soccer team's games of every competition, with ESPN's US TV`() =
        runBlocking<Unit> {
            val games = data("2026-11-01T12:00:00Z").teamGames(arsenal)
            assertEquals(10, games.size)
            assertEquals("401879246", games.first().id)
            assertTrue(games.flatMap { it.broadcasters }.all { it.country == "US" })
        }

    @Test
    fun `an NHL game takes the NHL's TV over ESPN's`() =
        runBlocking<Unit> {
            val games = data("2026-10-31T12:00:00Z").teamGames(boston)
            assertTrue(games.none { it.state == GameState.FINISHED })
            val chicago = games.first()
            assertEquals("401892614", chicago.id)
            assertEquals(
                listOf("SN1" to "CA", "NESN" to "US", "CHSN" to "US"),
                chicago.broadcasters.map { it.name to it.country },
            )
        }

    @Test
    fun `search gives ESPN's teams and leagues`() =
        runBlocking<Unit> {
            val found = data("2026-11-01T12:00:00Z").search("serie")
            assertTrue(found.teams.isNotEmpty() && found.teams.all { it.source == SportsSource.ESPN })
            assertTrue(found.leagues.isNotEmpty() && found.leagues.all { it.source == SportsSource.ESPN })
        }

    @Test
    fun `a soccer team found by search shows its league, so the men's and the women's team differ`() =
        runBlocking<Unit> {
            // ESPN's answer for "ajax" (2026-10-31): two "Ajax Amsterdam" and two "Jong Ajax", each a men's and a women's team.
            val teams = data("2026-11-01T12:00:00Z").search("ajax").teams
            val ajax = teams.filter { it.name == "Ajax Amsterdam" }
            assertEquals(listOf("soccer/139", "soccer/20796"), ajax.map { it.id })
            assertEquals(listOf("Dutch Eredivisie", "Dutch Vrouwen Eredivisie"), ajax.map { it.leagueName })
            assertEquals(2, teams.count { it.name == "Jong Ajax" && it.leagueName != null })
            assertEquals(teams.size, teams.distinctBy { it.id }.size)
        }

    @Test
    fun `euro finds the European Championship first`() =
        runBlocking<Unit> {
            val leagues = data("2026-11-01T12:00:00Z").search("euro").leagues
            assertEquals("soccer/uefa.euro", leagues.first().id)
            assertEquals(leagues.size, leagues.distinctBy { it.id }.size)
            assertTrue(leagues.any { it.id == "soccer/uefa.europa" })
        }

    @Test
    fun `an NFL game names its league NFL, not the slug`() {
        assertEquals("NFL", EspnParser.leagueNameOfSlug("nfl"))
        assertEquals("NCAA Football", EspnParser.leagueNameOfSlug("college-football"))
        assertEquals("ger.1", EspnParser.leagueNameOfSlug("ger.1"))
    }

    @Test
    fun `with the switch off nothing is called`() =
        runBlocking<Unit> {
            val data = data("2026-11-01T12:00:00Z", enabled = false)
            assertTrue(data.search("arsenal").teams.isEmpty())
            assertTrue(data.teamGames(arsenal).isEmpty())
            assertTrue(calls.isEmpty())
        }

    @Test(expected = IOException::class)
    fun `a team whose only source is down fails`() =
        runBlocking<Unit> {
            data("2026-11-01T12:00:00Z").teamGames(arsenal.copy(id = "soccer/1"))
        }

    @Test
    fun `merge keeps each country from its best source`() {
        val merged =
            SportsData.mergeBroadcasters(
                listOf(
                    listOf(Broadcaster("NESN", "US", from = BroadcasterOrigin.NHL)),
                    listOf(
                        Broadcaster("ESPN+", "US", from = BroadcasterOrigin.ESPN),
                        Broadcaster("TSN", "CA", from = BroadcasterOrigin.ESPN),
                        Broadcaster("NHL.tv", null, from = BroadcasterOrigin.ESPN),
                    ),
                ),
            )
        assertEquals(listOf("TSN" to "CA", "NESN" to "US", "NHL.tv" to null), merged.map { it.name to it.country })
        assertEquals(BroadcasterOrigin.ESPN, merged.first().from)
    }
}
