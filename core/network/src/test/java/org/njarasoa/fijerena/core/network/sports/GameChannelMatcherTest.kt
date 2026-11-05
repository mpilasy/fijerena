package org.njarasoa.fijerena.core.network.sports

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.FakeGuide
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.broadcaster
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.channel
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.game
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.programme
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.team
import org.njarasoa.fijerena.core.network.sports.GameChannelHow.BROADCASTER_CHANNEL

/** The game matcher on guide samples (see [GameChannelFixtures]). */
class GameChannelMatcherTest {
    private val arsenal = team("Arsenal", "ARS")
    private val chelsea = team("Chelsea", "CHE")
    private val liverpool = team("Liverpool", "LIV")

    private fun List<GameChannel>.summary() = map { "${it.stream.name}: ${it.how}" }

    private suspend fun match(
        game: SportsGame,
        channels: List<LiveChannel>,
        vararg programmes: GuideProgramme,
    ) = GameChannelMatcher(FakeGuide(programmes.toList())).match(game, channels)

    @Test
    fun `Sky's Live PL that starts 90 minutes before kick-off is the game on the broadcaster's channels`() =
        runTest {
            val sky = broadcaster("Sky Sports", "GB")
            val game = game("2026-11-01T16:30:00Z", liverpool, arsenal, "English Premier League", sky)
            val channels =
                listOf(
                    channel(1, "UK: Sky Sports Premier League HD", "SkySp.PL.HD.uk"),
                    channel(2, "UK: Sky Sports Main Event HD", "SkySpMainEvHD.uk"),
                    channel(3, "UK: Sky Sports Football HD", "SkySp.Fball.HD.uk"),
                    channel(4, "UK: Sky News", "SkyNews.uk"),
                    channel(5, "UK: LFC TV HD", "LFCTV.HD.uk"),
                )
            val description = "Mikel Arteta's Premier League champions Arsenal arrive at Anfield for a blockbuster Super Sunday."
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "SkySp.PL.HD.uk",
                        "SkySp PL HD",
                        "Live PL: Liverpool v Arsenal",
                        "2026-11-01T15:00:00Z",
                        "2026-11-01T19:30:00Z",
                        description,
                    ),
                    programme(
                        "SkySpMainEvHD.uk",
                        "SkySpMainEvHD",
                        "Live PL: Liverpool v Arsenal",
                        "2026-11-01T15:00:00Z",
                        "2026-11-01T19:30:00Z",
                        description,
                    ),
                    programme(
                        "SkySp.PL.HD.uk",
                        "SkySp PL HD",
                        "PL: Liverpool v Arsenal Highlights",
                        "2026-11-01T22:00:00Z",
                        "2026-11-01T22:30:00Z",
                    ),
                    programme("LFCTV.HD.uk", "LFCTV HD", "Liverpool v Arsenal: The Match", "2026-11-02T00:00:00Z", "2026-11-02T02:00:00Z"),
                )

            assertEquals(
                listOf(
                    "UK: Sky Sports Main Event HD: GUIDE_ON_BROADCASTER",
                    "UK: Sky Sports Premier League HD: GUIDE_ON_BROADCASTER",
                    "UK: Sky Sports Football HD: BROADCASTER_CHANNEL",
                ),
                result.summary(),
            )
            assertEquals("Live PL: Liverpool v Arsenal", result[0].programme?.title)
            assertEquals(sky, result[0].broadcaster)
        }

    @Test
    fun `highlights and a classic in the window are not the game, the channel stays as the broadcaster's`() =
        runTest {
            val supersport = broadcaster("SuperSport", "ZA")
            val game = game("2026-11-01T16:30:00Z", liverpool, arsenal, "English Premier League", supersport)
            val channels =
                listOf(
                    channel(1, "ZA: SS Premier League HD", "SS.Premier.League.za"),
                    channel(2, "ZA: SuperSport Grandstand HD", "GRANDSTAND.za"),
                )
            val result =
                match(
                    game,
                    channels,
                    // ZA guide times are +0700 in the file; converted to UTC.
                    programme(
                        "GRANDSTAND.za",
                        "GRANDSTAND",
                        "PL 26/27: Liverpool v Arsenal",
                        "2026-11-01T16:20:00Z",
                        "2026-11-01T18:40:00Z",
                    ),
                    programme(
                        "SS.Premier.League.za",
                        "SS Premier League",
                        "PL Classic 15/16: Liverpool v Arsenal",
                        "2026-11-01T16:00:00Z",
                        "2026-11-01T16:30:00Z",
                    ),
                    programme(
                        "SS.Premier.League.za",
                        "SS Premier League",
                        "PL HL 26/27: Liverpool v Arsenal",
                        "2026-11-01T16:30:00Z",
                        "2026-11-01T16:45:00Z",
                    ),
                )

            assertEquals(
                listOf("ZA: SuperSport Grandstand HD: GUIDE_ON_BROADCASTER", "ZA: SS Premier League HD: BROADCASTER_CHANNEL"),
                result.summary(),
            )
        }

    @Test
    fun `Arsenal v Chelsea anywhere in the guide, without broadcaster data`() =
        runTest {
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League")
            val channels =
                listOf(
                    channel(1, "UK: TNT Sports 1 HD", "TNTSports1.uk"),
                    channel(2, "UK: Sky Sports Main Event", "SkySpMainEvHD.uk"),
                    channel(3, "UK: E4 HD", "E4.HD.uk"),
                    channel(4, "UK: Arsenal TV", "ArsenalTV.uk"),
                )
            // Title forms of the task's samples on the channels above; the E4 programme is real.
            val result =
                match(
                    game,
                    channels,
                    programme("TNTSports1.uk", "TNT Sports 1 HD", "Arsenal v Chelsea", "2026-11-07T17:00:00Z", "2026-11-07T20:00:00Z"),
                    programme(
                        "SkySpMainEvHD.uk",
                        "SkySpMainEvHD",
                        "Premier League: Arsenal - Chelsea",
                        "2026-11-07T17:15:00Z",
                        "2026-11-07T19:30:00Z",
                    ),
                    programme(
                        "E4.HD.uk",
                        "E4 HD",
                        "Made in Chelsea",
                        "2026-11-07T17:00:00Z",
                        "2026-11-07T18:00:00Z",
                        "With Sam Prince's antics in question, is Arman a better match for Maali?",
                        "Entertainment",
                    ),
                    programme(
                        "ArsenalTV.uk",
                        "Arsenal TV",
                        "Arsenal: The Invincibles",
                        "2026-11-09T17:30:00Z",
                        "2026-11-09T18:30:00Z",
                        "A documentary.",
                    ),
                    programme(
                        "ArsenalTV.uk",
                        "Arsenal TV",
                        "Arsenal: The Invincibles",
                        "2026-11-07T17:00:00Z",
                        "2026-11-07T18:00:00Z",
                        "A documentary.",
                    ),
                )

            assertEquals(
                listOf("UK: Sky Sports Main Event: GUIDE", "UK: TNT Sports 1 HD: GUIDE"),
                result.summary(),
            )
        }

    @Test
    fun `a highlights compilation that names one team and the competition in its description is not the game`() =
        runTest {
            // The programme is real; the game time is set to start 30 minutes after it.
            val game = game("2026-10-30T19:30:00Z", liverpool, arsenal, "English Premier League")
            val result =
                match(
                    game,
                    listOf(channel(1, "UK: LFC TV HD", "LFCTV.HD.uk")),
                    programme(
                        "LFCTV.HD.uk",
                        "LFCTV HD",
                        "Kop 10: Arsenal at Anfield",
                        "2026-10-30T19:00:00Z",
                        "2026-10-30T19:30:00Z",
                        "Enjoy highlights from ten memorable home wins over Arsenal in the Premier League era.",
                    ),
                )
            assertEquals(emptyList<String>(), result.summary())
        }

    @Test
    fun `Live Ligue 1 PSG OM - two short names together are the game, and PSG matches Paris-SG`() =
        runTest {
            val psg = team("Paris Saint-Germain", "PSG", shortName = "Paris SG")
            val om = team("Marseille", "MAR", shortName = "Marseille")
            val game = game("2026-11-08T19:45:00Z", psg, om, "French Ligue 1")
            val channels = listOf(channel(1, "FR: DAZN 1 HD", "DAZN1.fr"), channel(2, "FR: CNews", "CNews.fr"))
            // The task's sample title.
            val result =
                match(
                    game,
                    channels,
                    programme("DAZN1.fr", "DAZN 1", "Live: Ligue 1 – PSG / OM", "2026-11-08T19:30:00Z", "2026-11-08T22:00:00Z"),
                )
            assertEquals(listOf("FR: DAZN 1 HD: GUIDE"), result.summary())

            val derby = game("2026-11-01T13:45:00Z", team("Paris FC", "PFC"), psg, "Première Ligue", broadcaster("Canal+", "FR"))
            val canal =
                listOf(
                    channel(3, "FR: CANAL+ FOOT FHD", "CANAL+FOOT.fr"),
                    channel(4, "FR: CANAL+ HD", "CANAL+.fr"),
                    channel(5, "FR: CANAL+ CINEMA", "CANAL+CINEMA.fr"),
                )
            val description = "Le grand derby parisien de la 7e journée d'Arkema Première Ligue entre le Paris FC et le PSG."
            val result2 =
                match(
                    derby,
                    canal,
                    programme(
                        "CANAL+FOOT.fr",
                        "CANAL+FOOT",
                        "Paris FC / Paris-SG",
                        "2026-11-01T13:30:00Z",
                        "2026-11-01T15:28:00Z",
                        description,
                        "Sport",
                    ),
                    programme(
                        "CANAL+.fr",
                        "CANAL+",
                        "Paris FC / Paris-SG",
                        "2026-11-01T13:30:00Z",
                        "2026-11-01T15:28:00Z",
                        description,
                        "Sport",
                    ),
                )
            assertEquals(
                listOf("FR: CANAL+ FOOT FHD: GUIDE_ON_BROADCASTER", "FR: CANAL+ HD: GUIDE_ON_BROADCASTER"),
                result2.summary(),
            )
        }

    @Test
    fun `Inter in a talk show title is not the game, AC Milan v Inter Milan is`() =
        runTest {
            val inter = team("Internazionale", "INT", shortName = "Inter Milan")
            val milan = team("AC Milan", "MIL")
            val channels = listOf(channel(1, "FR: CNews", "CNews.fr"), channel(2, "UK: BBC Two HD", "BBC.Two.de"))
            val talkShow =
                programme(
                    "CNews.fr",
                    "CNews",
                    "L'Heure Inter",
                    "2026-10-31T19:00:00Z",
                    "2026-10-31T19:57:00Z",
                    "Un décryptage de l'actualité internationale présenté par Gauthier Le Bret.",
                    "Débat",
                )
            val derby =
                programme(
                    "BBC.Two.de",
                    "BBC Two",
                    "Serie A: AC Milan v Inter Milan",
                    "2026-10-31T19:40:00Z",
                    "2026-10-31T21:50:00Z",
                    null,
                    "Soccer",
                )

            val result = match(game("2026-10-31T19:45:00Z", milan, inter, "Italian Serie A"), channels, talkShow, derby)
            assertEquals(listOf("UK: BBC Two HD: GUIDE"), result.summary())

            // The talk show alone, at the time of another Inter game, matches nothing.
            val other = game("2026-10-31T19:30:00Z", inter, team("Hellas Verona", "VER"), "Italian Serie A")
            assertEquals(emptyList<String>(), match(other, channels, talkShow).summary())
        }

    @Test
    fun `NFL Chiefs at Broncos on Sky Sports+, and the Kaizer Chiefs at the same time are not the game`() =
        runTest {
            val chiefs = team("Kansas City Chiefs", "Kansas City", "KC", shortName = "Chiefs", sport = "football")
            val broncos = team("Denver Broncos", "Denver", "DEN", shortName = "Broncos", sport = "football")
            val game = game("2026-11-01T21:25:00Z", broncos, chiefs, "NFL", broadcaster("Sky Sports", "GB"))
            val channels =
                listOf(
                    channel(1, "UK| Sky Sports+ HD", "SkySp+HD.uk"),
                    channel(2, "UK| Sky Sports NFL", "SkySp.NFL.uk"),
                    channel(3, "ZA: PSL", "PSL.za"),
                )
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "SkySp+HD.uk",
                        "SkySp+HD",
                        "Live NFL Ravens @ Bills",
                        "2026-11-01T17:30:00Z",
                        "2026-11-01T21:15:00Z",
                        "A match-up from week eight of the 2026 NFL season.",
                    ),
                    programme(
                        "SkySp+HD.uk",
                        "SkySp+HD",
                        "Live NFL Chiefs @ Broncos",
                        "2026-11-01T21:15:00Z",
                        "2026-11-02T00:30:00Z",
                        "A match-up from week eight of the 2026 NFL season.",
                    ),
                    // A real South African title, moved to the NFL game's time.
                    programme(
                        "PSL.za",
                        "PSL",
                        "Betway Prem 26/27: Pirates v Chiefs",
                        "2026-11-01T21:00:00Z",
                        "2026-11-01T23:00:00Z",
                        null,
                        null,
                    ),
                )
            assertEquals(
                listOf("UK| Sky Sports+ HD: GUIDE_ON_BROADCASTER", "UK| Sky Sports NFL: BROADCASTER_CHANNEL"),
                result.summary(),
            )
        }

    @Test
    fun `NFL Football Chiefs at Bills names both teams by their nicknames`() =
        runTest {
            val chiefs = team("Kansas City Chiefs", "Kansas City", "KC", shortName = "Chiefs", sport = "football")
            val bills = team("Buffalo Bills", "Buffalo", "BUF", shortName = "Bills", sport = "football")
            val game = game("2026-11-15T18:00:00Z", bills, chiefs, "NFL", broadcaster("CBS", "US"))
            val channels = listOf(channel(1, "US - CBS (WCBS) New York HD", "WCBS.us"), channel(2, "US - CBS Sports Network", "CBSSN.us"))
            // The task's sample title.
            val result =
                match(
                    game,
                    channels,
                    programme("WCBS.us", "WCBS", "NFL Football: Chiefs at Bills", "2026-11-15T18:00:00Z", "2026-11-15T21:30:00Z"),
                )
            // CBS Sports Network is another network: not a channel of CBS.
            assertEquals(
                listOf("US - CBS (WCBS) New York HD: GUIDE_ON_BROADCASTER"),
                result.summary(),
            )
        }

    @Test
    fun `Kaizer Chiefs v Orlando Pirates in the Soweto derby, the replay of an old derby is not it`() =
        runTest {
            val pirates = team("Orlando Pirates", "ORL", shortName = "Orlando Pirates")
            val kaizer = team("Kaizer Chiefs", "Chiefs", "KAI", shortName = "Kaizer Chiefs")
            val game = game("2026-10-31T13:30:00Z", pirates, kaizer, "South African Premiership")
            val channels = listOf(channel(1, "ZA: SuperSport PSL HD", "PSL.za"))
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "PSL.za",
                        "PSL",
                        "Soweto Derby 10/11: Pirates v Chiefs",
                        "2026-10-31T13:00:00Z",
                        "2026-10-31T13:25:00Z",
                        null,
                        null,
                    ),
                    programme(
                        "PSL.za",
                        "PSL",
                        "Betway Prem 26/27: Pirates v Chiefs",
                        "2026-10-31T13:25:00Z",
                        "2026-10-31T15:30:00Z",
                        null,
                        null,
                    ),
                )
            assertEquals(listOf("ZA: SuperSport PSL HD: GUIDE"), result.summary())
            assertEquals("Betway Prem 26/27: Pirates v Chiefs", result[0].programme?.title)
        }

    @Test
    fun `Chicago Bears at Seattle Seahawks on a team channel, its overtime slot is not the game, ESPN channels follow`() =
        runTest {
            val bears = team("Chicago Bears", "Chicago", "CHI", shortName = "Bears", sport = "football")
            val seahawks = team("Seattle Seahawks", "Seattle", "SEA", shortName = "Seahawks", sport = "football")
            val game = game("2026-11-03T01:15:00Z", seahawks, bears, "NFL", broadcaster("ESPN", "US"))
            val channels =
                listOf(
                    channel(1, "US: NFL Chicago Bears", "NFL-ChicagoBears.us"),
                    channel(2, "US: ESPN HD", "ESPN.us"),
                    channel(3, "US: ESPN2", "ESPN2.us"),
                    channel(4, "US: ESPNews", "ESPNews.us"),
                    channel(5, "CA: ESPN Classic", "ESPNClassic.ca"),
                )
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "NFL-ChicagoBears.us",
                        "NFL - Chicago Bears",
                        "Chicago Bears at Seattle Seahawks",
                        "2026-11-03T01:15:00Z",
                        "2026-11-03T04:45:00Z",
                        "Chicago Bears at Seattle Seahawks @ Lumen Field in Seattle on ESPN",
                    ),
                    programme(
                        "NFL-ChicagoBears.us",
                        "NFL - Chicago Bears",
                        "Chicago Bears at Seattle Seahawks possible Overtime",
                        "2026-11-03T04:45:00Z",
                        "2026-11-03T05:00:00Z",
                    ),
                )
            assertEquals(
                listOf("US: NFL Chicago Bears: GUIDE", "US: ESPN HD: BROADCASTER_CHANNEL", "US: ESPN2: BROADCASTER_CHANNEL"),
                result.summary(),
            )
        }

    @Test
    fun `NBA Trail Blazers at Jazz on a team channel, beIN's generic NBA title stays a broadcaster channel`() =
        runTest {
            val jazz = team("Utah Jazz", "Utah", "UTAH", shortName = "Jazz", sport = "basketball")
            val blazers = team("Portland Trail Blazers", "Portland", "POR", shortName = "Trail Blazers", sport = "basketball")
            val game = game("2026-10-31T19:00:00Z", jazz, blazers, "NBA", broadcaster("beIN Sports", "FR"))
            val channels =
                listOf(
                    channel(1, "FR: beIN SPORTS MAX 5 HD", "beIN.SPORTS.MAX.5.fr"),
                    channel(2, "US: NBA Utah Jazz", "NBA-UtahJazz.us"),
                    channel(3, "US: beIN Sports", "beINSports.us"),
                )
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "beIN.SPORTS.MAX.5.fr",
                        "beIN SPORTS MAX 5",
                        "Basket-ball : NBA",
                        "2026-10-31T19:00:00Z",
                        "2026-10-31T21:45:00Z",
                        "Diffusion d'un match de la NBA (National Basketball Association), la prestigieuse ligue américaine de basketball.",
                        "Basket-ball",
                    ),
                    programme(
                        "NBA-UtahJazz.us",
                        "NBA - Utah Jazz",
                        "Portland Trail Blazers at Utah Jazz",
                        "2026-10-31T19:00:00Z",
                        "2026-10-31T22:00:00Z",
                        "Portland Trail Blazers at Utah Jazz @ Delta Center in Salt Lake City on NBA League Pass",
                    ),
                )
            assertEquals(
                listOf("US: NBA Utah Jazz: GUIDE", "FR: beIN SPORTS MAX 5 HD: BROADCASTER_CHANNEL"),
                result.summary(),
            )
        }

    @Test
    fun `Handball THW Kiel v Barca finds Barcelona by its alias`() =
        runTest {
            val kiel = team("THW Kiel", sport = "handball")
            val barca = team("FC Barcelona", sport = "handball")
            val game = game("2026-11-05T17:45:00Z", kiel, barca, "EHF Champions League")
            val channels = listOf(channel(1, "DE: Dyn Sport 1", "Dyn1.de"))
            // The task's sample title.
            val result =
                match(
                    game,
                    channels,
                    programme("Dyn1.de", "Dyn Sport 1", "Handball: THW Kiel – Barça", "2026-11-05T17:30:00Z", "2026-11-05T19:30:00Z"),
                )
            assertEquals(listOf("DE: Dyn Sport 1: GUIDE"), result.summary())
        }

    @Test
    fun `a stream found by the broadcaster and by the guide search shows once, as the broadcaster's`() =
        runTest {
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League", broadcaster("TNT Sports", "GB"))
            val result =
                match(
                    game,
                    listOf(channel(1, "UK: TNT Sports 1 HD", "TNTSports1.uk")),
                    programme(
                        "TNTSports1.uk",
                        "TNT Sports 1 HD",
                        "Live: Arsenal v Chelsea",
                        "2026-11-07T17:00:00Z",
                        "2026-11-07T20:00:00Z",
                    ),
                )
            assertEquals(listOf("UK: TNT Sports 1 HD: GUIDE_ON_BROADCASTER"), result.summary())
        }

    @Test
    fun `a hidden channel is never returned and the guide does not match another channel in its place`() =
        runTest {
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League")
            val channels =
                listOf(
                    channel(1, "UK: TNT Sports 1 HD", "TNTSports1.uk", hidden = true),
                    channel(2, "UK: TNT Sports 1 HD backup", null),
                )
            val result =
                match(
                    game,
                    channels,
                    programme("TNTSports1.uk", "TNT Sports 1 HD", "Arsenal v Chelsea", "2026-11-07T17:00:00Z", "2026-11-07T20:00:00Z"),
                )
            assertEquals(emptyList<String>(), result.summary())
        }

    @Test
    fun `a guide programme is matched on a stream of its own source`() =
        runTest {
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League")
            val channels =
                listOf(channel(1, "TNT Sports 1", "tnt1.uk", providerId = 1L), channel(2, "TNT Sports 1", "tnt1.uk", providerId = 2L))
            val result =
                match(
                    game,
                    channels,
                    programme(
                        "tnt1.uk",
                        "TNT Sports 1",
                        "Arsenal v Chelsea",
                        "2026-11-07T17:00:00Z",
                        "2026-11-07T20:00:00Z",
                        providerId = 2L,
                    ),
                )
            assertEquals(1, result.size)
            assertEquals(2L, result[0].stream.providerId)
        }

    @Test
    fun `nothing found is an empty list, and a broadcaster without a channel is not listed`() =
        runTest {
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League")
            assertEquals(emptyList<GameChannel>(), match(game, emptyList()))

            val canal = broadcaster("Canal+", "FR")
            val dazn = broadcaster("DAZN", "DE")
            val withTv = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League", canal, dazn)
            val result = match(withTv, listOf(channel(1, "FR: CANAL+ FOOT", "CANAL+FOOT.fr")))
            assertEquals(listOf(GameChannel(channel(1, "FR: CANAL+ FOOT", "CANAL+FOOT.fr"), null, canal, BROADCASTER_CHANNEL)), result)
        }

    @Test
    fun `a broadcaster's channels with neither its own name nor a sport in theirs are not listed`() =
        runTest {
            // ESPN gives Hulu for a Bundesliga game in the US; Hulu's channels are reruns of its shows.
            val hulu = broadcaster("Hulu", "US")
            val game = game("2026-11-07T17:30:00Z", arsenal, chelsea, "English Premier League", hulu)
            val channels = (1..8).map { channel(it, "US: HULU ORIGINALS $it", "hulu$it.us") }
            assertEquals(emptyList<GameChannel>(), match(game, channels))
        }

    @Test
    fun `two city names count as the game only next to each other, not another sport's teams of those cities`() =
        runTest {
            val minnesota = team("Minnesota United FC", shortName = "Minnesota")
            val sanJose = team("San Jose Earthquakes", shortName = "San Jose")
            val game = game("2026-11-07T18:00:00Z", minnesota, sanJose, "MLS")
            val channels = listOf(channel(1, "HR: NOVA SPORT", "nova.hr"), channel(2, "US: MLS 1", "mls1.us"))
            val hockey =
                programme("nova.hr", "Nova Sport", "Minnesota Wild – San Jose Sharks", "2026-11-07T16:20:00Z", "2026-11-07T19:00:00Z")
            val soccer = programme("mls1.us", "MLS 1", "Minnesota v San Jose", "2026-11-07T18:00:00Z", "2026-11-07T20:00:00Z")
            assertEquals(listOf("US: MLS 1: GUIDE"), match(game, channels, hockey, soccer).summary())
        }

    @Test
    fun `the guide search asks for every name form of both teams`() =
        runTest {
            val guide = FakeGuide(emptyList())
            val united = team("Manchester United", "MUN", shortName = "Man United")
            GameChannelMatcher(
                guide,
            ).match(game("2026-11-07T17:30:00Z", united, team("Tottenham Hotspur", "TOT"), "Premier League"), emptyList())
            val terms = guide.searched.single()
            listOf("manchester united", "man utd", "man united", "mun", "tottenham", "spurs", "tottenham hotspur").forEach {
                assertTrue("$it in $terms", it in terms)
            }
        }
}
