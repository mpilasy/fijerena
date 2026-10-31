package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

class SeasonTableTest {
    private val tableJson =
        """
        {
          "version": 1,
          "updated": "2026-10-31",
          "regions": {"INDIAN_OCEAN": ["MG", "MU"]},
          "competitions": [
            {
              "key": "eng.1",
              "name": "Premier League",
              "sport": "soccer",
              "ids": {"espn": "soccer/eng.1", "fifa": "2000000000"},
              "names": ["English Premier League", "EPL"],
              "sources": ["https://example.org"]
            },
            {
              "key": "esp.1",
              "name": "LaLiga",
              "sport": "soccer",
              "ids": {"espn": "soccer/esp.1"},
              "names": ["La Liga", "LaLiga EA Sports"]
            }
          ],
          "rights": [
            {"competition": "eng.1", "season": "2025-26", "from": "2025-08-01", "to": "2026-06-30", "countries": ["GB"], "broadcasters": ["Old Channel"]},
            {"competition": "eng.1", "season": "2026-27", "from": "2026-08-01", "to": "2027-06-30", "countries": ["GB"], "broadcasters": ["Sky Sports", "TNT Sports"]},
            {"competition": "eng.1", "season": "2026-27", "from": "2026-08-01", "to": "2027-06-30", "countries": ["mg", "INDIAN_OCEAN"], "broadcasters": ["Canal+ Sport", "Canal+ Sport"]},
            {"competition": "esp.1", "season": "2026-27", "from": "2026-08-01", "to": "2027-06-30", "countries": ["ES"], "broadcasters": ["Movistar Plus+", "DAZN"]},
            {"competition": "esp.1", "season": "2026-27", "from": "2026-08-01", "to": "2027-06-30", "countries": ["WORLD"], "broadcasters": ["LaLiga+"]}
          ]
        }
        """.trimIndent()

    private var reads = 0

    private fun table() =
        SeasonTable(
            readJson = {
                reads++
                tableJson
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

    private fun epoch(date: String): Long =
        LocalDate
            .parse(date)
            .atTime(15, 0)
            .toEpochSecond(ZoneOffset.UTC)

    private fun league(
        source: SportsSource,
        id: String,
        name: String,
    ) = SportsLeague(source, id, name, sport = "soccer")

    @Test
    fun `finds the competition by source id and returns every country in the period`() =
        runTest {
            val result = table().broadcasters(league(SportsSource.ESPN, "soccer/eng.1", "Whatever"), epoch("2026-10-31"))

            assertEquals(
                listOf(
                    Broadcaster("Sky Sports", "GB", from = BroadcasterOrigin.SEASON_TABLE),
                    Broadcaster("TNT Sports", "GB", from = BroadcasterOrigin.SEASON_TABLE),
                    Broadcaster("Canal+ Sport", "MG", from = BroadcasterOrigin.SEASON_TABLE),
                    Broadcaster("Canal+ Sport", "MU", from = BroadcasterOrigin.SEASON_TABLE),
                ),
                result,
            )
        }

    @Test
    fun `an id without the sport path finds the same competition`() =
        runTest {
            val result = table().broadcasters(league(SportsSource.ESPN, "eng.1", "Whatever"), epoch("2026-10-31"))

            assertEquals(4, result.size)
        }

    @Test
    fun `an id of another source does not match`() =
        runTest {
            val result = table().broadcasters(league(SportsSource.UEFA, "eng.1", "Whatever"), epoch("2026-10-31"))

            assertTrue(result.isEmpty())
        }

    @Test
    fun `finds the competition by name without case or accents, and a worldwide service has no country`() =
        runTest {
            val result = table().broadcasters(league(SportsSource.FIFA, "999", "LALIGA ÉA Sports"), epoch("2026-12-01"))

            assertEquals(
                listOf("ES" to "Movistar Plus+", "ES" to "DAZN", null to "LaLiga+"),
                result.map { it.country to it.name },
            )
        }

    @Test
    fun `the date picks the season`() =
        runTest {
            val table = table()
            val league = league(SportsSource.ESPN, "soccer/eng.1", "Premier League")

            assertEquals(listOf("Old Channel"), table.broadcasters(league, epoch("2026-05-24")).map { it.name })
            assertTrue(table.broadcasters(league, epoch("2026-07-15")).isEmpty())
            assertEquals(4, table.broadcasters(league, epoch("2027-06-30")).size)
        }

    @Test
    fun `an unknown competition has no broadcasters`() =
        runTest {
            val result = table().broadcasters(league(SportsSource.ESPN, "soccer/xyz.1", "Nowhere League"), epoch("2026-10-31"))

            assertTrue(result.isEmpty())
        }

    @Test
    fun `the game form uses the game's competition and date`() =
        runTest {
            val team = SportsTeam(SportsSource.ESPN, "359", "Arsenal", sport = "soccer")
            val game =
                SportsGame(
                    source = SportsSource.ESPN,
                    id = "1",
                    startEpochSec = epoch("2026-11-07"),
                    home = team,
                    away = team.copy(id = "363", name = "Chelsea"),
                    competition = league(SportsSource.ESPN, "soccer/eng.1", "Premier League"),
                )

            assertEquals(4, table().broadcasters(game).size)
        }

    @Test
    fun `the asset is read once`() =
        runTest {
            val table = table()
            val league = league(SportsSource.ESPN, "soccer/eng.1", "Premier League")

            table.broadcasters(league, epoch("2026-10-31"))
            table.broadcasters(league, epoch("2026-11-30"))

            assertEquals(1, reads)
        }

    @Test
    fun `the shipped asset parses and knows the Premier League`() =
        runTest {
            val asset = File("src/main/assets/${SeasonTable.ASSET}")
            val table = SeasonTable({ asset.readText() }, Dispatchers.Unconfined)

            val result = table.broadcasters(league(SportsSource.ESPN, "soccer/eng.1", "Premier League"), epoch("2026-10-31"))

            assertTrue(result.any { it.country == "GB" })
        }
}
