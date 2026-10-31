package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // Real time, not runTest: the UEFA wait is a timeout, and virtual time would end it while the
    // cache reads its files.
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = background.cancel()

    // The saved answer for each URL; an unknown URL fails as a source that is down.
    private val transport =
        SportsTransport { url ->
            synchronized(calls) { calls += url }
            when {
                "common/v3/search" in url -> sportsFixture("espn_search_team.json")
                "search/v2" in url -> sportsFixture("espn_search_league.json")
                "leagues/dropdown" in url -> sportsFixture("espn_soccer_leagues.json")
                "soccer/all/teams/359/schedule" in url -> sportsFixture("espn_schedule_soccer_all.json")
                "hockey/nhl/teams/1/schedule" in url -> sportsFixture("espn_schedule_nhl.json")
                "club-schedule-season/BOS" in url -> sportsFixture("nhl_club_schedule.json")
                "teams/search" in url -> sportsFixture("fifa_teams_search.json")
                "competitions/search" in url -> sportsFixture("fifa_competitions_search.json")
                "worldaquatics.com/fina/competitions?" in url -> sportsFixture("worldaquatics_competitions.json")
                "match.uefa.com" in url -> sportsFixture("uefa_matches.json")
                "broadcaster.uefa.com" in url && "countryCode=FR" in url -> sportsFixture("uefa_broadcasters_fr.json")
                "broadcaster.uefa.com" in url -> """{"data":[],"meta":{"collection":{"totalElements":"0"}}}"""
                else -> throw IOException("down: $url")
            }
        }

    private fun data(
        nowIso: String,
        scope: CoroutineScope,
        season: suspend (SportsGame) -> List<Broadcaster> = { emptyList() },
        enabled: Boolean = true,
    ) = SportsData(
        api = SportsApi(transport),
        cache = SportsCache(folder.root),
        seasonBroadcasters = season,
        enabled = { enabled },
        uefaCountries = { listOf("FR", "GB") },
        background = scope,
        uefaWaitMs = 60_000L,
        now = { epoch(nowIso) * 1000 },
    )

    private val arsenal =
        SportsTeam(SportsSource.ESPN, "soccer/359", "Arsenal", shortName = "ARS", sport = "soccer", league = "soccer/eng.1")
    private val boston =
        SportsTeam(SportsSource.ESPN, "hockey/nhl/1", "Boston Bruins", shortName = "BOS", sport = "hockey", league = "hockey/nhl")

    @Test
    fun `a soccer team's games of every competition, with UEFA's broadcasters for a UEFA game`() =
        runBlocking<Unit> {
            val games = data("2026-11-01T12:00:00Z", background).teamGames(arsenal)
            assertEquals(10, games.size)
            assertEquals("401879246", games.first().id)
            val slavia = games.first { it.id == "401915381" }
            assertEquals(
                listOf("CANAL+ SPORT" to "FR", "Paramount+" to "US"),
                slavia.broadcasters.map { it.name to it.country },
            )
            // A Premier League game has only ESPN's US TV.
            assertTrue(games.first().broadcasters.all { it.country == "US" })
        }

    @Test
    fun `an NHL game takes the NHL's TV over ESPN's, and the season table only for other countries`() =
        runBlocking<Unit> {
            val season: suspend (SportsGame) -> List<Broadcaster> = {
                listOf(
                    Broadcaster("Viaplay", "SE", from = BroadcasterOrigin.SEASON_TABLE),
                    Broadcaster("Somebody", "US", from = BroadcasterOrigin.SEASON_TABLE),
                    Broadcaster("NHL.tv", null, from = BroadcasterOrigin.SEASON_TABLE),
                )
            }
            val games = data("2026-10-31T12:00:00Z", background, season).teamGames(boston)
            assertTrue(games.none { it.state == GameState.FINISHED })
            val chicago = games.first()
            assertEquals("401892614", chicago.id)
            assertEquals(
                listOf("SN1" to "CA", "Viaplay" to "SE", "NESN" to "US", "CHSN" to "US", "NHL.tv" to null),
                chicago.broadcasters.map { it.name to it.country },
            )
        }

    @Test
    fun `search gives ESPN first, then FIFA's teams and leagues ESPN does not have`() =
        runBlocking<Unit> {
            val found = data("2026-11-01T12:00:00Z", background).search("serie")
            assertEquals(SportsSource.ESPN, found.teams.first().source)
            assertTrue(found.teams.any { it.source == SportsSource.FIFA })
            assertEquals(SportsSource.ESPN, found.leagues.first().source)
            val fifa = found.leagues.filter { it.source == SportsSource.FIFA }.map { it.name }
            assertFalse("Serie A (ITA)" in fifa)
            assertTrue("Serie A (BRA)" in fifa)
            assertTrue("Serie A Women (ITA)" in fifa)
        }

    @Test
    fun `with the switch off nothing is called`() =
        runBlocking<Unit> {
            val data = data("2026-11-01T12:00:00Z", background, enabled = false)
            assertTrue(data.search("arsenal").teams.isEmpty())
            assertTrue(data.teamGames(arsenal).isEmpty())
            assertTrue(calls.isEmpty())
        }

    @Test(expected = IOException::class)
    fun `a team whose only source is down fails`() =
        runBlocking<Unit> {
            data("2026-11-01T12:00:00Z", background).teamGames(arsenal.copy(id = "soccer/1"))
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
                    ),
                    listOf(
                        Broadcaster("TSN", "CA", from = BroadcasterOrigin.SEASON_TABLE),
                        Broadcaster("DAZN", null, from = BroadcasterOrigin.SEASON_TABLE),
                    ),
                ),
            )
        assertEquals(listOf("TSN" to "CA", "NESN" to "US", "DAZN" to null), merged.map { it.name to it.country })
        assertEquals(BroadcasterOrigin.ESPN, merged.first().from)
    }
}
