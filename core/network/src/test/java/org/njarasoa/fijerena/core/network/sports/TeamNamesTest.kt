package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamNamesTest {
    private fun team(
        name: String,
        vararg alt: String,
    ) = SportsTeam(SportsSource.ESPN, name, name, altNames = alt.toList(), sport = "soccer")

    private fun game(
        home: String,
        away: String,
        start: Long,
    ) = SportsGame(SportsSource.ESPN, "$home-$away", start, team(home), team(away), SportsLeague(SportsSource.ESPN, "x", "x", "soccer"))

    @Test
    fun `normalize removes accents, club words and numbers`() {
        assertEquals("koln", TeamNames.normalize("1. FC Köln"))
        assertEquals("barcelona", TeamNames.normalize("FC Barcelona"))
        assertEquals("brighton and hove albion", TeamNames.normalize("Brighton & Hove Albion"))
        assertEquals("atletico madrid", TeamNames.normalize("Club Atlético de Madrid"))
        assertEquals("montreal canadiens", TeamNames.normalize("Montréal Canadiens"))
        assertEquals("bodo glimt", TeamNames.normalize("FK Bodø/Glimt"))
    }

    @Test
    fun `same team by any name or by all the words of a name`() {
        assertTrue(TeamNames.sameTeam(team("Shakhtar"), team("FC Shakhtar Donetsk")))
        assertTrue(TeamNames.sameTeam(team("Arsenal"), team("Arsenal London")))
        assertTrue(TeamNames.sameTeam(team("Boston Bruins", "Bruins"), team("Bruins")))
        assertFalse(TeamNames.sameTeam(team("Arsenal"), team("Arsenal Sarandi").copy(name = "Sarandi")))
        assertFalse(TeamNames.sameTeam(team("Liverpool"), team("Everton")))
        // Words under 4 letters are not enough alone.
        assertFalse(TeamNames.sameTeam(team("AEK"), team("AEK Larnaca").copy(name = "Larnaca")))
    }

    @Test
    fun `same game within 2 hours, either way round`() {
        val start = epoch("2026-11-04T20:00:00Z")
        val a = game("Boston Bruins", "Montréal Canadiens", start)
        assertTrue(TeamNames.sameGame(a, game("Boston Bruins", "Montreal Canadiens", start + 3600)))
        assertTrue(TeamNames.sameGame(a, game("Montreal Canadiens", "Boston Bruins", start)))
        assertFalse(TeamNames.sameGame(a, game("Boston Bruins", "Montreal Canadiens", start + 3 * 3600)))
        assertFalse(TeamNames.sameGame(a, game("Boston Bruins", "Toronto Maple Leafs", start)))
    }

    @Test
    fun `ESPN and the NHL name the same games`() {
        val espn = EspnParser.schedule(sportsFixture("espn_schedule_nhl.json"), "hockey", "nhl")
        val nhl = NhlParser.clubSchedule(sportsFixture("nhl_club_schedule.json"))
        val pairs = espn.mapNotNull { game -> nhl.firstOrNull { TeamNames.sameGame(game, it) }?.let { game.id to it.id } }
        assertTrue(("401892614" to "2026020217") in pairs)
        assertTrue(("401892696" to "2026020299") in pairs)
        assertEquals(pairs.size, pairs.map { it.second }.distinct().size)
        assertEquals(6, pairs.size)
    }
}
