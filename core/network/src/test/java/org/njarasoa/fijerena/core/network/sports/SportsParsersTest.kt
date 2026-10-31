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
    fun `ESPN soccer leagues list names and slugs`() {
        val leagues = EspnParser.soccerLeagues(sportsFixture("espn_soccer_leagues.json"))
        assertEquals(
            SportsLeague(SportsSource.ESPN, "soccer/fifa.world", "FIFA World Cup", "soccer", leagues.first().logoUrl),
            leagues.first(),
        )
        assertTrue(leagues.size >= 30)
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
    fun `FIFA matches, teams and competitions`() {
        val games = FifaParser.matches(sportsFixture("fifa_matches.json"))
        val first = games.first()
        assertEquals("8d6fncz9l51nk9bzppqjh49hw", first.id)
        assertEquals("Cruzeiro", first.home.name)
        assertEquals("Corinthians", first.away?.name)
        assertEquals("CONMEBOL Libertadores Femenina", first.competition.name)
        assertEquals(epoch("2026-11-01T00:00:00Z"), first.startEpochSec)
        assertEquals(GameState.SCHEDULED, first.state)
        assertTrue(first.broadcasters.isEmpty())

        val teams = FifaParser.teams(sportsFixture("fifa_teams_search.json"))
        assertTrue(teams.any { it.id == "9ne49trfop8q6vhb1zlasx016" && it.name == "Cruzeiro" && it.shortName == "CRU" })

        val leagues = FifaParser.competitions(sportsFixture("fifa_competitions_search.json"))
        assertTrue(leagues.any { it.id == "2000000026" && it.name == "Serie A (ITA)" })
        assertTrue(leagues.any { it.id == "2000000078" && it.name == "Serie A (BRA)" })
    }

    @Test
    fun `UEFA matches and one country's broadcasters`() {
        val games = UefaParser.matches(sportsFixture("uefa_matches.json"))
        val slavia = games.first { it.id == "2049621" }
        assertEquals("Slavia Praha", slavia.home.name)
        assertEquals("Arsenal", slavia.away?.name)
        assertEquals("UEFA Champions League", slavia.competition.name)
        assertEquals(epoch("2026-11-04T20:00:00Z"), slavia.startEpochSec)

        val page = UefaParser.broadcasters(sportsFixture("uefa_broadcasters_fr.json"), "FR")
        assertEquals(36, page.total)
        assertEquals(
            listOf(
                Broadcaster(
                    "CANAL+ SPORT",
                    "FR",
                    page.byMatch
                        .getValue("2049621")
                        .first()
                        .logoUrl,
                    BroadcasterOrigin.UEFA,
                ),
            ),
            page.byMatch["2049621"],
        )
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
    fun `World Aquatics competitions and their national team games`() {
        val competitions = WorldAquaticsParser.competitions(sportsFixture("worldaquatics_competitions.json"))
        val worldCup = competitions.first { it.league.id == "5134" }
        assertEquals("Men's Water Polo World Cup 2026, Division 1", worldCup.league.name)
        assertEquals("water-polo", worldCup.league.sport)

        val games = WorldAquaticsParser.schedule(sportsFixture("worldaquatics_schedule.json"), worldCup.league)
        val first = games.first()
        assertEquals("SRB/men", first.home.id)
        assertEquals("Serbia", first.home.name)
        assertEquals("Netherlands", first.away?.name)
        assertEquals(epoch("2026-04-06T10:30:00Z"), first.startEpochSec)
        assertEquals(GameState.FINISHED, first.state)
    }

    @Test
    fun `times with and without seconds or an offset`() {
        assertEquals(epoch("2026-11-01T16:30:00Z"), isoEpochSec("2026-11-01T16:30Z"))
        assertEquals(epoch("2026-04-06T10:30:00Z"), isoEpochSec("2026-04-06T10:30:00"))
        assertNull(isoEpochSec("soon"))
    }
}
