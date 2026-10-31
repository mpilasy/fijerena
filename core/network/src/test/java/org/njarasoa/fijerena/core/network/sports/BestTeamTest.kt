package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The team a Teams search puts first, on ESPN's saved answers (2026-10-31). */
class BestTeamTest {
    private fun best(
        query: String,
        fixture: String,
    ): SportsTeam? = BestTeam.of(query, EspnParser.teamSearch(sportsFixture(fixture)))

    @Test
    fun `bayern - the men's club, not the women's team or Bayern Munich II`() {
        assertEquals("soccer/132", best("bayern", "espn_search_team_bayern.json")?.id)
    }

    @Test
    fun `ajax - Ajax Amsterdam's men, not the cricket club named Ajax or Jong Ajax`() {
        assertEquals("soccer/139", best("ajax", "espn_search_team_ajax.json")?.id)
    }

    @Test
    fun `arsenal - the Premier League club, not the women's team`() {
        assertEquals("soccer/359", best("arsenal", "espn_search_team.json")?.id)
    }

    @Test
    fun `chiefs - Kansas City by its other name, before Kaizer Chiefs`() {
        assertEquals("football/nfl/12", best("chiefs", "espn_search_team_chiefs.json")?.id)
    }

    @Test
    fun `alabama - football and men's basketball share the name, ESPN's first wins`() {
        val team = best("alabama", "espn_search_team_alabama.json")
        assertEquals("football/college-football/333", team?.id)
        assertEquals("football", team?.sport)
    }

    @Test
    fun `a query that names a women's or reserve team puts those first`() {
        assertEquals("soccer/20103", best("bayern women", "espn_search_team_bayern.json")?.id)
        assertEquals("soccer/21630", best("bayern ii", "espn_search_team_bayern.json")?.id)
    }

    @Test
    fun `a name match comes before ESPN's order, and no team gives none`() {
        val teams = EspnParser.teamSearch(sportsFixture("espn_search_team_chiefs.json"))
        assertEquals("soccer/7081", BestTeam.of("kaizer", teams)?.id)
        assertNull(BestTeam.of("bayern", emptyList()))
    }
}
