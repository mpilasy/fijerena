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

    // The played games of every competition are down.
    private var resultsDown = false

    // The NBA scoreboard is down.
    private var scoreboardDown = false

    // The saved answer for each URL; an unknown URL fails as a source that is down.
    private val transport =
        SportsTransport { url ->
            synchronized(calls) { calls += url }
            when {
                "common/v3/search" in url && "query=ajax" in url -> {
                    sportsFixture("espn_search_team_ajax.json")
                }

                "common/v3/search" in url -> {
                    sportsFixture("espn_search_team.json")
                }

                "search/v2" in url && "query=euro&" in url -> {
                    sportsFixture("espn_search_league_euro.json")
                }

                "search/v2" in url && "European" in url -> {
                    sportsFixture("espn_search_league_european_championship.json")
                }

                "search/v2" in url -> {
                    sportsFixture("espn_search_league.json")
                }

                "leagues/dropdown?sport=soccer" in url -> {
                    sportsFixture("espn_leagues_soccer.json")
                }

                "soccer/all/teams/359/schedule" in url && "fixture=false" in url -> {
                    sportsFixture("espn_results_soccer_all.json")
                }

                "soccer/all/teams/359/schedule" in url -> {
                    sportsFixture("espn_schedule_soccer_all.json")
                }

                resultsDown && "fixture=false" in url -> {
                    throw IOException("down: $url")
                }

                "soccer/all/teams/5533/schedule" in url && "fixture=false" in url -> {
                    sportsFixture("espn_results_soccer_madagascar.json")
                }

                "soccer/all/teams/5533/schedule" in url && "fixture=true" in url -> {
                    sportsFixture("espn_schedule_soccer_madagascar.json")
                }

                "soccer/caf.nations_qual/teams/5533/schedule" in url -> {
                    sportsFixture("espn_schedule_caf_nations_qual_madagascar.json")
                }

                "soccer/all/teams/481/schedule" in url && "fixture=false" in url -> {
                    sportsFixture("espn_results_soccer_germany.json")
                }

                "soccer/uefa.nations/teams/481/schedule" in url -> {
                    sportsFixture("espn_schedule_uefa_nations_germany.json")
                }

                "hockey/nhl/teams/1/schedule" in url -> {
                    sportsFixture("espn_schedule_nhl.json")
                }

                "basketball/nba/teams/16/schedule" in url -> {
                    sportsFixture("espn_schedule_nba_live.json")
                }

                !scoreboardDown && "basketball/nba/scoreboard" in url && "dates=20261119" in url -> {
                    sportsFixture("espn_scoreboard_nba_live.json")
                }

                "club-schedule-season/BOS" in url -> {
                    sportsFixture("nhl_club_schedule.json")
                }

                else -> {
                    throw IOException("down: $url")
                }
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

    private val minnesota =
        SportsTeam(SportsSource.ESPN, "basketball/nba/16", "Minnesota Timberwolves", sport = "basketball", league = "basketball/nba")

    @Test
    fun `a game on takes its score and clock from the league's scoreboard, as the team schedule has none`() =
        runBlocking<Unit> {
            val live = data("2026-11-20T02:00:00Z").teamGames(minnesota).first()
            assertEquals(GameState.LIVE, live.state)
            assertEquals("Minnesota Timberwolves", live.home.name)
            assertEquals("30" to "40", live.homeScore to live.awayScore)
            assertEquals("End of 2nd", live.clock)
            assertTrue(calls.any { "basketball/nba/scoreboard" in it && "dates=20261119" in it })
        }

    @Test
    fun `a game on keeps no score when the scoreboard can't be read`() =
        runBlocking<Unit> {
            scoreboardDown = true
            val live = data("2026-11-20T02:00:00Z").teamGames(minnesota).first()
            assertEquals(GameState.LIVE, live.state)
            assertEquals(null to null, live.homeScore to live.awayScore)
            assertEquals("End of 2nd", live.clock)
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
    fun `a soccer team's results come from every competition, a cup too, newest first`() =
        runBlocking<Unit> {
            val results = data("2026-11-02T12:00:00Z").teamResults(arsenal)
            assertEquals(
                listOf("2026-11-01", "2026-10-27", "2026-10-24", "2026-10-21", "2026-10-18", "2026-10-13", "2026-10-10"),
                results.map { utcDay(it.startEpochSec) },
            )
            assertEquals("Liverpool" to "0", results.first().home.name to results.first().homeScore)
            assertEquals("1", results.first().awayScore)
            assertEquals("soccer/eng.league_cup", results[1].competition.id)
            // The played games, and the coming games for their competitions.
            assertEquals(
                listOf("fixture=false", "fixture=true"),
                calls.filter { "soccer/all/teams/359" in it }.map { it.substringAfter('?') }.sorted(),
            )
        }

    private val madagascar = SportsTeam(SportsSource.ESPN, "soccer/5533", "Madagascar", sport = "soccer")
    private val germany = SportsTeam(SportsSource.ESPN, "soccer/481", "Germany", sport = "soccer")

    @Test
    fun `a game that has just ended comes from its competition's list before ESPN adds it to every competition's`() =
        runBlocking<Unit> {
            // ESPN's played games of every competition end on 12 Nov; AFCON Qualifying's list has 17 Nov.
            val results = data("2026-11-17T19:00:00Z").teamResults(madagascar)
            assertEquals(listOf("2026-11-17", "2026-11-12"), results.map { utcDay(it.startEpochSec) })
            assertEquals(listOf("2", "0"), listOf(results.first().homeScore, results.first().awayScore))
            assertEquals(1, calls.count { "soccer/caf.nations_qual/teams/5533" in it })
        }

    @Test
    fun `a competition of the coming games is read when the played games can't be`() =
        runBlocking<Unit> {
            resultsDown = true
            val results = data("2026-11-17T19:00:00Z").teamResults(madagascar)
            assertEquals("2026-11-17", utcDay(results.first().startEpochSec))
        }

    @Test
    fun `the competition of the newest played game is read when no game is coming`() =
        runBlocking<Unit> {
            val results = data("2026-11-17T19:00:00Z").teamResults(germany)
            assertEquals(listOf("2026-11-16", "2026-11-13"), results.take(2).map { utcDay(it.startEpochSec) })
            assertEquals(listOf("Germany", "Netherlands"), listOf(results.first().home.name, results.first().away?.name))
        }

    @Test
    fun `results keep only the last 30 days`() =
        runBlocking<Unit> {
            val results = data("2026-11-15T12:00:00Z").teamResults(arsenal)
            assertEquals(
                listOf("2026-11-01", "2026-10-27", "2026-10-24", "2026-10-21", "2026-10-18"),
                results.map { utcDay(it.startEpochSec) },
            )
        }

    @Test
    fun `another sport's results are the finished games of its season schedule`() =
        runBlocking<Unit> {
            val results = data("2026-10-31T12:00:00Z").teamResults(boston)
            assertEquals(listOf("401892604", "401892586"), results.map { it.id })
            assertTrue(results.all { it.state == GameState.FINISHED && it.homeScore != null && it.awayScore != null })
        }

    @Test
    fun `no results with the switch off or when every read fails`() =
        runBlocking<Unit> {
            assertTrue(data("2026-11-02T12:00:00Z", enabled = false).teamResults(arsenal).isEmpty())
            val unknown = arsenal.copy(id = "soccer/1")
            assertTrue(data("2026-11-02T12:00:00Z").teamResults(unknown).isEmpty())
        }

    private fun utcDay(epochSec: Long) =
        java.time.Instant
            .ofEpochSecond(epochSec)
            .toString()
            .take(10)

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
