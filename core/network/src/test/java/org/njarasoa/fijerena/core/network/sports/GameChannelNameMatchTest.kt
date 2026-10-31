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
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.programme
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.team

/**
 * PPV and event channels found by the team names in their own names (step 3 of the matcher), and
 * the dates and times in those names ([ChannelNameDates]). The channel names are of the forms IPTV
 * lists use; the teams are named as ESPN names them.
 */
class GameChannelNameMatchTest {
    private val bayern = team("Bayern Munich", "FCB", shortName = "Bayern")
    private val ajax = team("AFC Ajax", "AJA", shortName = "Ajax")
    private val chiefs = team("Kansas City Chiefs", "Kansas City", "KC", shortName = "Chiefs", sport = "football")
    private val bills = team("Buffalo Bills", "Buffalo", "BUF", shortName = "Bills", sport = "football")

    // Saturday 2026-10-31, 20:30 in Munich.
    private val bayernAjax = game("2026-10-31T19:30:00Z", bayern, ajax, "German Bundesliga")

    private fun List<GameChannel>.summary() = map { "${it.stream.name}: ${it.how}" }

    private suspend fun match(
        game: SportsGame,
        channels: List<LiveChannel>,
        vararg programmes: GuideProgramme,
    ) = GameChannelMatcher(FakeGuide(programmes.toList())).match(game, channels)

    private fun channels(vararg names: String) = names.mapIndexed { i, name -> channel(i + 1, name) }

    @Test
    fun `a PPV channel that names both teams is the game, a club channel or another game is not`() =
        runTest {
            val result =
                match(
                    bayernAjax,
                    channels(
                        "UK| PPV 3: Bayern v Ajax",
                        "PPV | Bundesliga 04: FC Bayern - Ajax (Sat 01/11 20:30)",
                        "DE: FC Bayern TV HD",
                        "NL: Ajax TV",
                        "NL: AJAX1 HD",
                        "UK| PPV 5: Arsenal v Chelsea",
                        "UK| PPV 1",
                        "PPV 7: Bayern v Ajax (12/03)",
                    ),
                )
            assertEquals(
                listOf(
                    "PPV | Bundesliga 04: FC Bayern - Ajax (Sat 01/11 20:30): CHANNEL_NAME",
                    "UK| PPV 3: Bayern v Ajax: CHANNEL_NAME",
                ),
                result.summary(),
            )
        }

    @Test
    fun `one distinctive team name and the competition match, after the channels that name both teams`() =
        runTest {
            val result = match(bayernAjax, channels("PPV: Bundesliga - FC Bayern Live", "UK| PPV 3: Bayern v Ajax", "DE: Bayern 1 Radio"))
            assertEquals(
                listOf("UK| PPV 3: Bayern v Ajax: CHANNEL_NAME", "PPV: Bundesliga - FC Bayern Live: CHANNEL_NAME"),
                result.summary(),
            )
        }

    @Test
    fun `a club's 24-7 channel is the game only when its guide programme names the opponent`() =
        runTest {
            val clubChannel = listOf(channel(1, "DE: FC Bayern TV HD", "FCBayernTV.de"))
            val magazine =
                programme("FCBayernTV.de", "FC Bayern TV", "FC Bayern TV Magazin", "2026-10-31T19:00:00Z", "2026-10-31T20:00:00Z")
            assertEquals(emptyList<String>(), match(bayernAjax, clubChannel, magazine).summary())

            val live =
                programme(
                    "FCBayernTV.de",
                    "FC Bayern TV",
                    "Live: FC Bayern München - Ajax Amsterdam",
                    "2026-10-31T19:15:00Z",
                    "2026-10-31T21:30:00Z",
                )
            assertEquals(listOf("DE: FC Bayern TV HD: GUIDE"), match(bayernAjax, clubChannel, live).summary())
        }

    @Test
    fun `NFL and DAZN event channels, with a zoned time that must agree`() =
        runTest {
            // Sunday 8:15 pm in New York.
            val game = game("2026-11-02T01:15:00Z", chiefs, bills, "NFL")
            val result =
                match(
                    game,
                    channels(
                        "US: NFL 05 | Chiefs @ Bills 20:15",
                        "US: NFL 06 | Chiefs @ Bills 8:15pm ET",
                        "US: NFL 07 | Chiefs @ Bills 1:00pm ET",
                        "ZA: PPV Kaizer Chiefs v Orlando Pirates",
                    ),
                )
            assertEquals(
                listOf("US: NFL 05 | Chiefs @ Bills 20:15: CHANNEL_NAME", "US: NFL 06 | Chiefs @ Bills 8:15pm ET: CHANNEL_NAME"),
                result.summary(),
            )

            val clasico = game("2026-11-01T20:00:00Z", team("Real Madrid", "RMA"), team("FC Barcelona", "BAR"), "Spanish LALIGA")
            assertEquals(
                listOf("DAZN EVENT 2 - Real Madrid vs Barcelona: CHANNEL_NAME"),
                match(clasico, channels("DAZN EVENT 2 - Real Madrid vs Barcelona", "ES: Real Madrid TV")).summary(),
            )
        }

    @Test
    fun `guide on a broadcaster's channel, then guide anywhere, then channel names, then broadcaster's channels`() =
        runTest {
            val espn = broadcaster("ESPN", "US")
            val game = game("2026-10-31T19:30:00Z", bayern, ajax, "German Bundesliga", espn)
            val result =
                match(
                    game,
                    listOf(
                        channel(1, "UK| PPV 3: Bayern v Ajax"),
                        channel(2, "US: ESPN 2"),
                        channel(3, "UK: Sky Sports Main Event", "SkyME.uk"),
                        channel(4, "US: ESPN HD", "ESPN.us"),
                    ),
                    programme("ESPN.us", "ESPN", "Bundesliga: Bayern Munich vs. Ajax", "2026-10-31T19:30:00Z", "2026-10-31T21:30:00Z"),
                    programme("SkyME.uk", "Sky Sports Main Event", "Live: Bayern v Ajax", "2026-10-31T19:00:00Z", "2026-10-31T21:30:00Z"),
                )
            assertEquals(
                listOf(
                    "US: ESPN HD: GUIDE_ON_BROADCASTER",
                    "UK: Sky Sports Main Event: GUIDE",
                    "UK| PPV 3: Bayern v Ajax: CHANNEL_NAME",
                    "US: ESPN 2: BROADCASTER_CHANNEL",
                ),
                result.summary(),
            )
        }

    @Test
    fun `dates in channel names agree within one day, either day-month order`() {
        val start = epoch("2026-10-31T19:30:00Z")
        assertTrue(ChannelNameDates.agrees("PPV | Bundesliga 04: FC Bayern - Ajax (Sat 01/11 20:30)", start))
        assertTrue(ChannelNameDates.agrees("PPV 2: Bayern v Ajax 10/31", start))
        assertTrue(ChannelNameDates.agrees("PPV 2: Bayern v Ajax 31/10/2026", start))
        assertFalse(ChannelNameDates.agrees("PPV 7: Bayern v Ajax (12/03)", start))
        assertFalse(ChannelNameDates.agrees("PPV 7: Bayern v Ajax 31/10/2025", start))
        assertTrue(ChannelNameDates.agrees("PPV: Bayern v Ajax Oct 31", start))
        assertTrue(ChannelNameDates.agrees("PPV: Bayern v Ajax 1st November", start))
        assertTrue(ChannelNameDates.agrees("PPV : Bayern - Ajax 1er novembre", start))
        assertFalse(ChannelNameDates.agrees("PPV: Bayern v Ajax Nov 8", start))
        assertTrue(ChannelNameDates.agrees("UK| PPV 3: Bayern v Ajax", start))
    }

    @Test
    fun `a day name agrees within one day, and a word of a team name is not a day`() {
        // Wednesday 2026-11-04.
        val start = epoch("2026-11-04T23:00:00Z")
        assertTrue(ChannelNameDates.agrees("PPV: Sun v Sky (Wed)", start))
        assertTrue(ChannelNameDates.agrees("PPV: Sun v Sky (Thu)", start))
        assertFalse(ChannelNameDates.agrees("PPV: Liberty v Sky (Sat)", start))
        assertFalse(ChannelNameDates.agrees("PPV: Sun v Sky", start))
        assertTrue(ChannelNameDates.agrees("PPV: Sun v Sky", start, ignore = setOf("sun", "sky")))
    }

    @Test
    fun `a time counts only with its zone`() {
        val start = epoch("2026-11-02T01:15:00Z")
        assertTrue(ChannelNameDates.agrees("Chiefs @ Bills 8:15pm ET", start))
        assertTrue(ChannelNameDates.agrees("Chiefs @ Bills 7:15 pm CT", start))
        assertTrue(ChannelNameDates.agrees("Chiefs @ Bills 01:15 UK", start))
        assertFalse(ChannelNameDates.agrees("Chiefs @ Bills 1:00pm ET", start))
        assertTrue(ChannelNameDates.agrees("Chiefs @ Bills 13:00", start))
        assertTrue(ChannelNameDates.agrees("UK| PPV 3 UK", start))
    }
}
