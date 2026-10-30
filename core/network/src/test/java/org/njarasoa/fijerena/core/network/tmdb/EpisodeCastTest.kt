package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The cast of an episode and the episodes of an actor's show credit
 * (docs/plans/20261030_episode-cast-plan.md, Phase 1): TMDB's payloads as they come (Law & Order:
 * SVU, Robin Williams in S9E17 and Mariska Hargitay as a regular), the lines of the appearances
 * list and where each one opens the show.
 */
class EpisodeCastTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a season's episodes keep their guest stars, in billing order`() {
        val season =
            json.decodeFromString<TmdbSeasonResponse>(
                """
                {"season_number":9,"episodes":[{"episode_number":17,"season_number":9,"name":"Authority",
                  "crew":[{"id":1,"name":"A Director","job":"Director"}],
                  "guest_stars":[
                    {"character":"Dwight Lomax","credit_id":"c1","order":580,"adult":false,"gender":2,"id":66580,
                     "known_for_department":"Acting","name":"Scott Adsit","profile_path":"/hh.jpg"},
                    {"character":"Merritt Rook","credit_id":"c2","order":500,"id":2157,"name":"Robin Williams","profile_path":null},
                    {"character":"Nobody","order":1,"id":null,"name":"No Id"}
                  ]}]}
                """.trimIndent(),
            )

        val guests = episodeGuests(season.episodes.single())

        assertEquals(
            listOf(
                CastMember("2157", "Robin Williams", "Merritt Rook", null),
                CastMember("66580", "Scott Adsit", "Dwight Lomax", "https://image.tmdb.org/t/p/w185/hh.jpg"),
            ),
            guests,
        )
    }

    @Test
    fun `an episode without guest stars has none, and no episode neither`() {
        val episode = json.decodeFromString<TmdbEpisode>("""{"episode_number":1}""")

        assertEquals(emptyList<CastMember>(), episodeGuests(episode))
        assertEquals(emptyList<CastMember>(), episodeGuests(null))
    }

    @Test
    fun `an episode's cast is its guest stars, then the show's cast, each once, without crew`() {
        val guest = CastMember("2157", "Robin Williams", "Merritt Rook", null)
        val regular = CastMember("6240", "Mariska Hargitay", "Olivia Benson", null)
        val show =
            TitleCredits(
                cast = listOf(regular, guest.copy(character = "Other")),
                crew = listOf(CrewLine(CrewRole.CREATOR, listOf(CrewPerson("1", "Dick Wolf")))),
            )

        val credits = episodeCredits(listOf(guest), show)

        assertEquals(TitleCredits(cast = listOf(guest, regular), crew = emptyList()), credits)
        assertNull(episodeCredits(emptyList(), null))
        assertNull(episodeCredits(emptyList(), TitleCredits(emptyList(), show.crew)))
    }

    @Test
    fun `a person's show credit carries its credit id`() {
        val person =
            json.decodeFromString<TmdbPerson>(
                """
                {"id":2157,"combined_credits":{"cast":[{"id":2734,"media_type":"tv","name":"Law & Order: Special Victims Unit",
                  "character":"Merritt Rook","episode_count":1,"credit_id":"5257438d760ee36aaa0d7e85"}]}}
                """.trimIndent(),
            )

        val role = personRoles(person, crew = false).getValue("tv" to 2734)

        assertEquals(PersonRole(character = "Merritt Rook", episodeCount = 1, creditId = "5257438d760ee36aaa0d7e85"), role)
    }

    @Test
    fun `a guest credit names its episodes, a regular credit only its seasons`() {
        val guest = json.decodeFromString<TmdbCredit>(GUEST_CREDIT)
        val regular = json.decodeFromString<TmdbCredit>(REGULAR_CREDIT)

        assertEquals(listOf(TmdbCreditEpisode(9, 17, "Authority")), guest.media.episodes)
        assertEquals(emptyList<TmdbCreditEpisode>(), regular.media.episodes)
        assertEquals(listOf(0, 13, 2, 1), regular.media.seasons.map { it.seasonNumber })
    }

    @Test
    fun `a guest role lists its episodes in air order, the ones the source lacks marked`() {
        val credit =
            TmdbCredit(
                media =
                    TmdbCreditMedia(
                        episodes =
                            listOf(
                                TmdbCreditEpisode(10, 2, "Later"),
                                TmdbCreditEpisode(9, 17, "Authority"),
                                TmdbCreditEpisode(9, 17, "Authority"),
                                TmdbCreditEpisode(null, 3, "No season"),
                            ),
                        seasons = listOf(TmdbCreditSeason(9, 19)),
                    ),
            )

        val lines = showAppearances(credit, mapOf((9 to 17) to "e917"))

        assertEquals(
            listOf(
                ShowAppearance(9, 17, "Authority", "e917", inSource = true),
                ShowAppearance(10, 2, "Later", null, inSource = false),
            ),
            lines,
        )
    }

    @Test
    fun `a regular role lists its seasons, the specials last, the ones the source lacks marked`() {
        val credit = json.decodeFromString<TmdbCredit>(REGULAR_CREDIT)

        val lines = showAppearances(credit, mapOf((1 to 1) to "e11", (2 to 1) to "e21"))

        assertEquals(
            listOf(
                ShowAppearance(1, inSource = true),
                ShowAppearance(2, inSource = true),
                ShowAppearance(13, inSource = false),
                ShowAppearance(0, inSource = false),
            ),
            lines,
        )
    }

    @Test
    fun `each line opens the episode, else its season, else the show, with the note for what the source lacks`() {
        val seasons = setOf(9)
        val have = ShowAppearance(9, 17, "Authority", "e917")
        val lackEpisode = ShowAppearance(9, 18, "Next", null, inSource = false)
        val lackSeason = ShowAppearance(10, 2, "Later", null, inSource = false)

        assertEquals(ShowTarget(episodeId = "e917", season = 9), showTarget(have, seasons))
        assertEquals(ShowTarget(season = 9, missing = lackEpisode), showTarget(lackEpisode, seasons))
        assertEquals(ShowTarget(season = null, missing = lackSeason), showTarget(lackSeason, seasons))
        assertEquals(ShowTarget(season = 9), showTarget(ShowAppearance(9), seasons))
        assertEquals(ShowTarget(missing = ShowAppearance(3, inSource = false)), showTarget(ShowAppearance(3, inSource = false), seasons))
    }

    @Test
    fun `a credit is read once and kept with the person, until the person is read again`() =
        runTest {
            val dir: File = Files.createTempDirectory("credits").toFile()
            val creditCalls = mutableListOf<String>()
            var now = 1_000L

            fun people() =
                TmdbPeople(
                    dir,
                    fetchPerson = { TmdbPerson(id = it) },
                    now = { now },
                    fetchCredit = { id ->
                        creditCalls += id
                        json.decodeFromString<TmdbCredit>(GUEST_CREDIT)
                    },
                )

            people().credit(2157, "c1")
            // Kept on disk with the person: a new instance asks TMDB nothing.
            assertEquals(listOf(TmdbCreditEpisode(9, 17, "Authority")), people().credit(2157, "c1").media.episodes)
            assertEquals(listOf("c1"), creditCalls)

            now += 7 * 24 * 60 * 60 * 1000L
            people().credit(2157, "c1")
            assertEquals(listOf("c1", "c1"), creditCalls)

            people().clear()
            people().credit(2157, "c1")
            assertEquals(listOf("c1", "c1", "c1"), creditCalls)
        }

    private companion object {
        // credit/5257438d760ee36aaa0d7e85, cut down: Robin Williams in SVU.
        const val GUEST_CREDIT =
            """
            {"credit_type":"cast","department":"Acting","job":"Actor","id":"5257438d760ee36aaa0d7e85",
             "media":{"id":2734,"name":"Law & Order: Special Victims Unit","character":"Merritt Rook",
               "episodes":[{"id":214412,"name":"Authority","air_date":"2008-04-29","episode_number":17,
                 "season_number":9,"show_id":2734,"still_path":"/l.jpg"}],
               "seasons":[{"id":8882,"name":"Season 9","season_number":9,"show_id":2734,"episode_count":19}]}}
            """

        // credit/5431f1ec0e0a2646490047cf, cut down: Mariska Hargitay in SVU, 602 episodes.
        const val REGULAR_CREDIT =
            """
            {"credit_type":"cast","id":"5431f1ec0e0a2646490047cf",
             "media":{"id":2734,"character":"Olivia Benson","episodes":[],
               "seasons":[{"season_number":0,"name":"Specials","episode_count":6},
                 {"season_number":13,"episode_count":23},{"season_number":2,"episode_count":21},
                 {"season_number":1,"episode_count":22}]}}
            """
    }
}
