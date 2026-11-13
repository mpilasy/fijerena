package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every sports parser on the saved real answers (2026-10-31). */
class SportsParsersTest {
    @Test
    fun `ESPN team search gives ids per sport and league, the abbreviation and the other names`() {
        val teams = EspnParser.teamSearch(sportsFixture("espn_search_team.json"))
        val arsenal = teams.first()
        assertEquals("soccer/359", arsenal.id)
        assertEquals("Arsenal", arsenal.name)
        assertEquals("ARS", arsenal.shortName)
        assertEquals("soccer/eng.1", arsenal.league)
        assertEquals("soccer", arsenal.sport)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/359.png", arsenal.logoUrl)
        assertTrue(teams.any { it.league == "soccer/eng.w.1" })
    }

    @Test
    fun `ESPN league search keeps only leagues, with sport and slug`() {
        val leagues = EspnParser.leagueSearch(sportsFixture("espn_search_league.json"))
        val premier = leagues.first()
        assertEquals("soccer/eng.1", premier.id)
        assertEquals("English Premier League", premier.name)
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500/23.png", premier.logoUrl)
        assertTrue(leagues.any { it.sport == "cricket" })
    }

    @Test
    fun `ESPN league teams carry the league`() {
        val teams = EspnParser.leagueTeams(sportsFixture("espn_nhl_teams.json"), "hockey", "nhl")
        assertEquals(32, teams.size)
        val boston = teams.first { it.shortName == "BOS" }
        assertEquals("hockey/nhl/1", boston.id)
        assertEquals("Boston Bruins", boston.name)
        assertEquals("hockey/nhl", boston.league)
        assertEquals("National Hockey League", boston.leagueName)
        assertTrue("Bruins" in boston.altNames)
    }

    @Test
    fun `ESPN soccer team schedule of every competition names each game's competition and US TV`() {
        val games = EspnParser.schedule(sportsFixture("espn_schedule_soccer_all.json"), "soccer", null)
        assertEquals(10, games.size)
        val first = games.first()
        assertEquals("401879246", first.id)
        assertEquals(epoch("2026-11-01T16:30Z"), first.startEpochSec)
        assertEquals("Liverpool", first.home.name)
        assertEquals("soccer/364", first.home.id)
        assertEquals("Arsenal", first.away?.name)
        assertEquals("soccer/eng.1", first.competition.id)
        assertEquals(GameState.SCHEDULED, first.state)
        assertEquals(listOf("NBCSN", "Tele", "Peacock"), first.broadcasters.map { it.name })
        assertTrue(first.broadcasters.all { it.country == "US" && it.from == BroadcasterOrigin.ESPN })
        val champions = games.first { it.id == "401915381" }
        assertEquals("soccer/uefa.champions", champions.competition.id)
        assertEquals("Slavia Prague", champions.home.name)
    }

    @Test
    fun `ESPN NHL schedule reads finished and coming games`() {
        val games = EspnParser.schedule(sportsFixture("espn_schedule_nhl.json"), "hockey", "nhl")
        assertEquals(GameState.FINISHED, games.first().state)
        val chicago = games.first { it.id == "401892614" }
        assertEquals(GameState.SCHEDULED, chicago.state)
        assertEquals("hockey/nhl/1", chicago.home.id)
        assertEquals("BOS", chicago.home.shortName)
        assertEquals("hockey/nhl", chicago.competition.id)
        assertEquals(listOf("ESPN+", "CHSN", "NESN"), chicago.broadcasters.map { it.name })
    }

    @Test
    fun `ESPN scores, a schedule's object, a scoreboard's string, none for a coming game`() {
        val nhl = EspnParser.schedule(sportsFixture("espn_schedule_nhl.json"), "hockey", "nhl").first { it.id == "401892604" }
        assertEquals("4" to "1", nhl.homeScore to nhl.awayScore)

        val board = EspnParser.scoreboard(sportsFixture("espn_scoreboard_soccer.json"), "soccer", "eng.1").first { it.id == "401879246" }
        assertEquals("0" to "0", board.homeScore to board.awayScore)

        val coming = EspnParser.schedule(sportsFixture("espn_schedule_soccer_all.json"), "soccer", null).first()
        assertEquals(null to null, coming.homeScore to coming.awayScore)
    }

    @Test
    fun `ESPN game on has its clock, a game that ended or is to come has none`() {
        fun answer(
            state: String,
            detail: String,
        ) = """{"events":[{"id":"9","date":"2026-11-13T18:00Z","league":{"slug":"uefa.nations","name":"UEFA Nations League"},
            "competitions":[{"status":{"displayClock":"45'+2'","type":{"name":"STATUS_HALFTIME","state":"$state","shortDetail":"$detail"}},
            "competitors":[{"homeAway":"home","team":{"id":"2619","displayName":"Moldova"},"score":{"displayValue":"1"}},
            {"homeAway":"away","team":{"id":"483","displayName":"Kazakhstan"},"score":{"displayValue":"1"}}]}]}]}"""

        val on = EspnParser.schedule(answer("in", "HT"), "soccer", null).single()
        assertEquals(GameState.LIVE, on.state)
        assertEquals("HT", on.clock)
        assertEquals("1" to "1", on.homeScore to on.awayScore)
        assertEquals(null, EspnParser.schedule(answer("post", "FT"), "soccer", null).single().clock)
        assertEquals("45'+2'", EspnParser.schedule(answer("in", ""), "soccer", null).single().clock)
    }

    @Test
    fun `ESPN scoreboards take the competition from the answer's league`() {
        val nhl = EspnParser.scoreboard(sportsFixture("espn_scoreboard_nhl.json"), "hockey", "nhl")
        assertTrue(nhl.isNotEmpty())
        val first = nhl.first()
        assertEquals("National Hockey League", first.competition.name)
        assertEquals("Carolina Hurricanes", first.home.name)
        assertEquals("Winnipeg Jets", first.away?.name)
        assertEquals(listOf("NHL Net", "CARNHL"), first.broadcasters.map { it.name })
        val soccer = EspnParser.scoreboard(sportsFixture("espn_scoreboard_soccer.json"), "soccer", "eng.1")
        assertTrue(soccer.all { it.competition.id == "soccer/eng.1" && it.home.id.startsWith("soccer/") })
    }

    @Test
    fun `NHL club schedule gives US and Canadian TV per game`() {
        val games = NhlParser.clubSchedule(sportsFixture("nhl_club_schedule.json"))
        val chicago = games.first { it.id == "2026020217" }
        assertEquals("Boston Bruins", chicago.home.name)
        assertEquals("Chicago Blackhawks", chicago.away?.name)
        assertEquals(GameState.SCHEDULED, chicago.state)
        assertEquals(
            listOf("SN1" to "CA", "NESN" to "US", "CHSN" to "US"),
            chicago.broadcasters.map { it.name to it.country },
        )
        assertEquals(GameState.FINISHED, games.first().state)
    }

    @Test
    fun `MLB schedule keeps TV only, once per name`() {
        val games = MlbParser.schedule(sportsFixture("mlb_schedule.json"))
        val first = games.first()
        assertEquals("822922", first.id)
        assertEquals("Tampa Bay Rays", first.home.name)
        assertEquals(listOf("Rays.TV", "NESN"), first.broadcasters.map { it.name })
        assertEquals(GameState.FINISHED, first.state)
        assertEquals("CA", MlbParser.country("Sportsnet"))
        assertEquals("US", MlbParser.country("NESN"))
    }

    @Test
    fun `times with and without seconds or an offset`() {
        assertEquals(epoch("2026-11-01T16:30:00Z"), isoEpochSec("2026-11-01T16:30Z"))
        assertEquals(epoch("2026-04-06T10:30:00Z"), isoEpochSec("2026-04-06T10:30:00"))
        assertNull(isoEpochSec("soon"))
    }
}
