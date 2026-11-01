package org.njarasoa.fijerena.core.ui.sports

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsLeague
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam

class GameLogoTeamsTest {
    private fun team(id: String) = SportsTeam(SportsSource.ESPN, id, "Team $id", sport = "soccer")

    private val league = SportsLeague(SportsSource.ESPN, "eng.1", "Premier League", "soccer")
    private val game = SportsGame(SportsSource.ESPN, "g", 0, home = team("1"), away = team("2"), competition = league)

    @Test
    fun `a game card shows both teams, home first`() {
        assertEquals(listOf("1", "2"), gameLogoTeams(game, null).map { it.id })
    }

    @Test
    fun `a team page's card shows the opponent alone`() {
        assertEquals(listOf("2"), gameLogoTeams(game, team("1")).map { it.id })
        assertEquals(listOf("1"), gameLogoTeams(game, team("2")).map { it.id })
    }

    @Test
    fun `a game without an away team shows its one team`() {
        assertEquals(listOf("1"), gameLogoTeams(game.copy(away = null), null).map { it.id })
    }
}
