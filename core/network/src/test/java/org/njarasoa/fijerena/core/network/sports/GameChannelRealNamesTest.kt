package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.FakeGuide
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.broadcaster
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.channel
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.epoch
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.game
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.team

/**
 * Channel names copied from a real Xtream source (about 50 channels that carried one game each, on
 * 2026-10-31), matched by name (step 3 of the matcher). The teams are named as ESPN names them.
 */
class GameChannelRealNamesTest {
    private val liverpool = team("Liverpool", "LIV")
    private val arsenal = team("Arsenal", "ARS")
    private val bayern = team("Bayern Munich", "Bayern", "MUN")
    private val dortmund = team("Borussia Dortmund", "Dortmund", "DOR")
    private val mainz = team("Mainz", "MAI")
    private val cologne = team("FC Cologne", "Cologne", "KOE")
    private val atletico = team("Atlético Madrid", "Atlético", "ATM")
    private val napoli = team("Napoli", "NAP")
    private val manCity = team("Manchester City", "Man City", "MNC")
    private val fulham = team("Fulham", "FUL")

    private fun nfl(
        name: String,
        location: String,
        nickname: String,
        abbreviation: String,
    ) = team(name, location, nickname, abbreviation, sport = "football")

    private val chiefs = nfl("Kansas City Chiefs", "Kansas City", "Chiefs", "KC")
    private val broncos = nfl("Denver Broncos", "Denver", "Broncos", "DEN")
    private val jets = nfl("New York Jets", "New York", "Jets", "NYJ")
    private val cardinals = nfl("Arizona Cardinals", "Arizona", "Cardinals", "ARI")
    private val falcons = nfl("Atlanta Falcons", "Atlanta", "Falcons", "ATL")

    private val liverpoolArsenal = game("2026-11-01T15:30:00Z", liverpool, arsenal, "English Premier League")
    private val bayernDortmund = game("2026-10-31T17:30:00Z", bayern, dortmund, "German Bundesliga")

    private suspend fun matched(
        game: SportsGame,
        names: List<String>,
    ): List<String> =
        GameChannelMatcher(FakeGuide(emptyList()))
            .match(
                game,
                names.mapIndexed {
                    i,
                    n,
                    ->
                    channel(i + 1, n)
                },
            ).map { it.stream.name }

    private val liverpoolNames =
        listOf(
            "US (Peacock 022) |  Liverpool v. Arsenal (2026-11-01 10:30:00)",
            "AU (STAN 31) | Liverpool v Arsenal  Premier League Matchweek 9 2026/2027 (2026-11-02 02:30:50)",
            "AU (STAN 70) | Arsenal v Hull City  Premier League Matchweek 10 2026/2027 (2026-11-08 01:50:50)",
            "AU (STAN 82) | Crystal Palace v Liverpool  Premier League Matchweek 10 2026/2027 (2026-11-09 00:50:50)",
            "UK: EPL ARSENAL",
        )

    private val bayernNames =
        listOf(
            "Bayern München vs Dortmund (Tactical Feed) @ Oct 31 6:20 PM :Viaplay DK  47",
            "Bayern München vs Dortmund @ Oct 31 7:20 PM :Viaplay FI  46",
            "Bundesliga: Bayern Munchen vs Dortmund @ Oct 31 18:20 :TeliaPlay SE  66",
            "Football: Der Klassiker: Bayern München – Dortmund @ Oct 31 18:20 :Tv2Play NO  78",
            "Live | FC Bayern vs. Dortmund | all | 8K EXCLUSIVE | DE: SOCCER PPV 52",
            "NEXT | BUNDESLIGA BAYERN MÜNCHEN - DORTMUND (TACTICAL FEED) | Sat 31 Oct 17:20 GMT (IS) | 8K EXCLUSIVE | IS: VIAPLAY PPV 30",
            "NEXT | BUNDESLIGA BAYERN MÜNCHEN - DORTMUND | Sat 31 Oct 18:20 CET (DK) | 8K EXCLUSIVE | DK: VIAPLAY PPV 43",
            "NEXT | BUNDESLIGA: BAYERN MÜNCHEN - DORTMUND | Sat 31 Oct 18:20 CET (SE) | 8K EXCLUSIVE | SE: PLAY+ PPV 64",
            "Next | Bayern Munich vs. Dortmund | Bundesliga | 2026-10-31 | 17:30 (GMT) | 8K EXCLUSIVE | CA: DAZN PPV 61",
            "Next | FC Bayern vs. Dortmund | all | 31-10-2026 | 17:30 (GMT) | 8K EXCLUSIVE | BR: SOCCER PPV 8",
        )

    @Test
    fun `Liverpool v Arsenal on US and AU event channels, not another game of either team or a club channel`() =
        runTest {
            assertEquals(liverpoolNames.take(2).sorted(), matched(liverpoolArsenal, liverpoolNames))
        }

    @Test
    fun `Bayern v Dortmund in every name form and time zone`() =
        runTest {
            assertEquals(bayernNames.sorted(), matched(bayernDortmund, bayernNames))
        }

    @Test
    fun `a name with one of our teams and another opponent or another date is not the game`() =
        runTest {
            val bayernDortmundCa = bayernNames[8]
            assertEquals(
                emptyList<String>(),
                matched(game("2026-11-07T14:30:00Z", mainz, bayern, "German Bundesliga"), listOf(bayernDortmundCa)),
            )
            assertEquals(
                emptyList<String>(),
                matched(game("2026-11-20T19:30:00Z", cologne, bayern, "German Bundesliga"), listOf(bayernDortmundCa)),
            )
            assertEquals(
                emptyList<String>(),
                matched(
                    game("2026-11-24T20:00:00Z", napoli, manCity, "UEFA Champions League"),
                    listOf("UK: RB Leipzig v Manchester City | Nov 5 20:00"),
                ),
            )
            assertEquals(
                emptyList<String>(),
                matched(
                    game("2026-11-21T15:00:00Z", fulham, manCity, "English Premier League"),
                    listOf("AU (STAN 60) | Manchester City v Brighton  Premier League Matchweek 9 2026/2027 (2026-11-02 03:00:50)"),
                ),
            )
            val chiefsBroncos = "NFL | 11 - 4:25pm Chiefs at Broncos"
            assertEquals(emptyList<String>(), matched(game("2026-11-08T18:00:00Z", jets, chiefs, "NFL"), listOf(chiefsBroncos)))
            assertEquals(emptyList<String>(), matched(game("2026-11-22T21:05:00Z", cardinals, chiefs, "NFL"), listOf(chiefsBroncos)))
            assertEquals(
                emptyList<String>(),
                matched(game("2026-11-15T18:00:00Z", falcons, chiefs, "NFL"), listOf("NFL | 08 - 1pm Falcons at Buccaneers")),
            )
        }

    @Test
    fun `the right game of the same teams still matches`() =
        runTest {
            val atleticoBayern = "AU (STAN 45) | Atlético de Madrid v Bayern München  UEFA Champions League (2026-11-04 07:00:50)"
            assertEquals(
                listOf(atleticoBayern),
                matched(game("2026-11-03T20:00:00Z", atletico, bayern, "UEFA Champions League"), listOf(atleticoBayern)),
            )
            val vix = "MX: VIX 13 | Chiefs at Broncos"
            assertEquals(listOf(vix), matched(game("2026-11-01T21:25:00Z", broncos, chiefs, "NFL"), listOf(vix)))
        }

    @Test
    fun `the same channel name in two categories or sources shows once, the first`() =
        runTest {
            val name = liverpoolNames[1]
            val result =
                GameChannelMatcher(FakeGuide(emptyList())).match(
                    liverpoolArsenal,
                    listOf(channel(1983, name), channel(1086, name, providerId = 2L), channel(5, liverpoolNames[0])),
                )
            assertEquals(
                listOf(liverpoolNames[1] to 1983, liverpoolNames[0] to 5).sortedBy { it.first },
                result.map {
                    it.stream.name to
                        it.stream.streamId
                },
            )
        }

    @Test
    fun `replays are not the game`() =
        runTest {
            val names =
                listOf(
                    "UK| PPV 3: Liverpool v Arsenal (R)",
                    "UK| PPV 4: Liverpool v Arsenal Replay",
                    "UK| PPV 5: Liverpool v Arsenal Re-air",
                    "FR: Liverpool - Arsenal Rediffusion",
                    "DE: Liverpool - Arsenal Wiederholung",
                    "US: Liverpool v Arsenal Delayed",
                    "UK: Liverpool v Arsenal Highlights",
                    "UK: Liverpool v Arsenal Classic",
                    "AU (STAN 31) | Liverpool v Arsenal  Premier League Matchweek 9 2025/2026 (2026-11-02 02:30:50)",
                    "UK| PPV 6: Liverpool v Arsenal",
                )
            assertEquals(listOf("UK| PPV 6: Liverpool v Arsenal"), matched(liverpoolArsenal, names))
        }

    @Test
    fun `date-times in names, with and without a zone`() {
        val start = epoch("2026-10-31T17:30:00Z")
        // No zone: within 15 hours either way.
        assertTrue(ChannelNameDates.agrees("A v B (2026-10-31 08:00:00)", start))
        assertTrue(ChannelNameDates.agrees("A v B (2026-11-01 08:00:00)", start))
        assertFalse(ChannelNameDates.agrees("A v B (2026-11-01 09:00:00)", start))
        assertFalse(ChannelNameDates.agrees("A v B (2026-10-31 02:00:00)", start))
        assertTrue(ChannelNameDates.agrees("A v B 31/10 18:20", start))
        assertTrue(ChannelNameDates.agrees("A v B 10/31 6:20 PM", start))
        // A zone: from 3 hours before to 1 hour after.
        assertTrue(ChannelNameDates.agrees("A v B Sat 31 Oct 15:00 GMT", start))
        assertFalse(ChannelNameDates.agrees("A v B Sat 31 Oct 14:00 GMT", start))
        assertFalse(ChannelNameDates.agrees("A v B Sat 31 Oct 19:00 GMT", start))
        assertTrue(ChannelNameDates.agrees("A v B 2026-10-31 | 13:30 EDT", start))
        assertTrue(ChannelNameDates.agrees("A v B Nov 1 4:30am AEDT", start))
        assertFalse(ChannelNameDates.agrees("A v B Oct 31 18:20 PT", start))
        // A date with no time: within a day.
        assertTrue(ChannelNameDates.agrees("A v B 1er novembre", start))
        assertFalse(ChannelNameDates.agrees("A v B 2 novembre", start))
        // A day name with no date.
        assertTrue(ChannelNameDates.agrees("A v B Sat 18:20", start))
        assertFalse(ChannelNameDates.agrees("A v B Tue", start))
    }

    @Test
    fun `a network's name does not match a longer, different network`() {
        fun matches(
            network: String,
            channelName: String,
        ): Boolean {
            val b = broadcaster(network, "US")
            return BroadcasterChannels.matches(b, BroadcasterChannels.broadcasterTokens(b.name), BroadcasterChannels.parse(channelName))
        }
        assertTrue(matches("CBS", "US: CBS HD"))
        assertTrue(matches("CBS", "US: CBS 2 New York"))
        assertFalse(matches("CBS", "US: CBS Sports Network"))
        assertTrue(matches("ESPN", "US: ESPN 2"))
        assertFalse(matches("ESPN", "US: ESPNews"))
        assertFalse(matches("ESPN", "US: ESPN Deportes"))
        assertFalse(matches("FOX", "US: FOX Sports 1"))
        assertFalse(matches("FOX", "US: FS1"))
        assertFalse(matches("NBC", "US: NBCSN"))
        assertFalse(matches("NBC", "US: NBC Sports Boston"))
        assertTrue(matches("CBS Sports Network", "US: CBS Sports Network HD"))
    }
}
