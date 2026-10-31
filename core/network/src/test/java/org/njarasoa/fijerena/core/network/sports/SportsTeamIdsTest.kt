package org.njarasoa.fijerena.core.network.sports

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every ESPN NHL and MLB team (saved lists, 2026-10-31) maps to its team in the NHL's and MLB's own lists. */
class SportsTeamIdsTest {
    @Test
    fun `every ESPN NHL team has an NHL tri-code of the same team`() {
        val nhl =
            sportsJson
                .parseToJsonElement(sportsFixture("nhl_standings.json"))
                .jsonObject["standings"]!!
                .jsonArray
                .associate { row ->
                    val team = row.jsonObject
                    team["teamAbbrev"]!!.jsonObject["default"]!!.jsonPrimitive.content to
                        team["teamName"]!!.jsonObject["default"]!!.jsonPrimitive.content
                }
        val espn = EspnParser.leagueTeams(sportsFixture("espn_nhl_teams.json"), "hockey", "nhl")
        assertEquals(32, espn.size)
        for (team in espn) {
            val triCode = SportsTeamIds.nhlTriCode(team)
            assertNotNull(team.name, triCode)
            val nhlName = nhl[triCode]
            assertNotNull("${team.name}: no NHL team $triCode", nhlName)
            assertEquals(TeamNames.normalize(team.name), TeamNames.normalize(nhlName!!))
        }
    }

    @Test
    fun `every ESPN MLB team has the MLB id of the same team`() {
        val mlb =
            sportsJson
                .parseToJsonElement(sportsFixture("mlb_teams.json"))
                .jsonObject["teams"]!!
                .jsonArray
                .associate { it.jsonObject["id"]!!.jsonPrimitive.int to it.jsonObject["name"]!!.jsonPrimitive.content }
        val espn = EspnParser.leagueTeams(sportsFixture("espn_mlb_teams.json"), "baseball", "mlb")
        assertEquals(30, espn.size)
        for (team in espn) {
            val id = SportsTeamIds.mlbTeamId(team)
            assertNotNull(team.name, id)
            assertEquals(team.name, mlb[id])
        }
    }

    @Test
    fun `only ESPN NHL and MLB teams map`() {
        val soccer = EspnParser.teamSearch(sportsFixture("espn_search_team.json")).first()
        assertEquals(null, SportsTeamIds.nhlTriCode(soccer))
        assertEquals(null, SportsTeamIds.mlbTeamId(soccer))
        assertTrue(SportsTeamIds.nhlTriCode("NJ") == "NJD" && SportsTeamIds.nhlTriCode("BOS") == "BOS")
    }
}
