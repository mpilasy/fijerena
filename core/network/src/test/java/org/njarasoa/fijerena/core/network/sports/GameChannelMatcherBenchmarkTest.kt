package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.FakeGuide
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.broadcaster
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.game
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.team
import kotlin.random.Random

/**
 * The matcher's time on a large channel list (60,000 live channels, as a big Xtream source has),
 * for 10 games. Runs only when the environment variable `SPORTS_BENCHMARK` is set:
 * `SPORTS_BENCHMARK=1 ./gradlew :core:network:testDebugUnitTest --tests '*GameChannelMatcherBenchmarkTest*'`.
 */
class GameChannelMatcherBenchmarkTest {
    private val clubs =
        listOf(
            "Arsenal",
            "Chelsea",
            "Liverpool",
            "Everton",
            "Tottenham",
            "Man City",
            "Man Utd",
            "Newcastle",
            "Aston Villa",
            "Brighton",
            "Fulham",
            "Brentford",
            "Wolves",
            "Crystal Palace",
            "Hull City",
            "Leeds",
            "Real Madrid",
            "Barcelona",
            "Atletico Madrid",
            "Sevilla",
            "Valencia",
            "Bayern München",
            "Dortmund",
            "Leipzig",
            "Leverkusen",
            "Frankfurt",
            "Juventus",
            "Inter",
            "AC Milan",
            "Napoli",
            "Roma",
            "Lazio",
            "PSG",
            "Marseille",
            "Lyon",
            "Monaco",
            "Ajax",
            "PSV",
            "Feyenoord",
            "AZ Alkmaar",
            "Benfica",
            "Porto",
            "Sporting",
            "Celtic",
            "Rangers",
        )
    private val countries = listOf("UK", "US", "DE", "FR", "ES", "IT", "NL", "PT", "AU", "CA", "BR", "DK", "SE", "NO")
    private val networks =
        listOf("Sky Sports", "BT Sport", "TNT Sports", "ESPN", "beIN Sports", "Canal+ Sport", "DAZN", "Viaplay", "SuperSport")

    private fun channels(count: Int): List<LiveChannel> {
        val random = Random(42)
        return (1..count).map { i ->
            val country = countries[random.nextInt(countries.size)]
            val name =
                when (i % 10) {
                    0, 1 -> {
                        val a = clubs[random.nextInt(clubs.size)]
                        val b = clubs[random.nextInt(clubs.size)]
                        val day = 1 + random.nextInt(28)
                        "$country (STAN ${random.nextInt(100)}) | $a v $b (2026-11-${"%02d".format(day)} 15:00:00)"
                    }

                    2 -> {
                        "$country: ${networks[random.nextInt(networks.size)]} ${random.nextInt(10)} HD"
                    }

                    else -> {
                        "$country: Channel ${random.nextInt(100_000)} ${listOf("HD", "FHD", "4K", "")[random.nextInt(4)]}"
                    }
                }
            LiveChannel(1L, i, name, "ch$i.$country", categoryId = "c${i % 500}")
        }
    }

    private fun games(): List<SportsGame> {
        val pairs =
            listOf(
                team("Liverpool", "LIV", shortName = "Liverpool") to team("Arsenal", "ARS", shortName = "Arsenal"),
                team("Bayern Munich", "FCB", "Bayern München", shortName = "Bayern") to
                    team("Borussia Dortmund", "DOR", shortName = "Dortmund"),
                team("AFC Ajax", "AJA", "Ajax Amsterdam", shortName = "Ajax") to team("AZ Alkmaar", "AZ", shortName = "AZ"),
                team("Real Madrid", "RMA") to team("FC Barcelona", "BAR", shortName = "Barcelona"),
                team("Juventus", "JUV") to team("Internazionale", "INT", shortName = "Inter"),
                team("Paris Saint-Germain", "PSG") to team("Olympique de Marseille", "MAR", shortName = "Marseille"),
                team("Chelsea", "CHE") to team("Tottenham Hotspur", "TOT", shortName = "Tottenham"),
                team("Benfica", "BEN") to team("FC Porto", "POR", shortName = "Porto"),
                team("Celtic", "CEL") to team("Rangers", "RAN"),
                team("Napoli", "NAP") to team("AS Roma", "ROM", shortName = "Roma"),
            )
        return pairs.mapIndexed { i, (home, away) ->
            game("2026-11-0${1 + i % 7}T15:00:00Z", home, away, "Soccer League", broadcaster("ESPN", "US"))
        }
    }

    @Test
    fun `60k channels, 10 games`() =
        runBlocking {
            assumeTrue(System.getenv("SPORTS_BENCHMARK") != null)
            val games = games()
            val shared = channels(60_000)
            repeat(4) { round ->
                // Rounds 0 and 1: a new list, as each screen read the channels again before; rounds 2 and 3: the
                // same list in a new matcher, as the screens get it now from SportsChannels.
                val list = if (round < 2) channels(60_000) else shared
                val matcher = GameChannelMatcher(FakeGuide(emptyList()))
                val firstStart = System.nanoTime()
                matcher.match(games.first(), list)
                val firstMs = (System.nanoTime() - firstStart) / 1_000_000
                val restStart = System.nanoTime()
                var found = 0
                for (game in games.drop(1)) found += matcher.match(game, list).size
                val restMs = (System.nanoTime() - restStart) / 1_000_000
                println("BENCHMARK round $round: first game ${firstMs}ms, next ${games.size - 1} games ${restMs}ms, found $found")
            }
        }
}
