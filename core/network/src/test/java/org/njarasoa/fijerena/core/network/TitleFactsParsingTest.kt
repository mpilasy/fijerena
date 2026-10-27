package org.njarasoa.fijerena.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.XtreamMapper.toDomain
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.xtream.manager.backdropText
import org.njarasoa.fijerena.core.player.domain.TitleFacts
import org.njarasoa.fijerena.core.player.model.SeriesInfo
import org.njarasoa.fijerena.core.player.model.VodInfo

/** The facts a details page lists, as TMDB and Xtream send them, and the store's JSON round trip. */
class TitleFactsParsingTest {
    private val json =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a film's facts come from its TMDB details`() {
        val body =
            """
            {"id":194,"title":"Amélie","original_title":"Le Fabuleux Destin d'Amélie Poulain","original_language":"fr",
            "origin_country":["FR"],"production_countries":[{"iso_3166_1":"FR"},{"iso_3166_1":"DE"}],
            "spoken_languages":[{"iso_639_1":"fr"}],"production_companies":[{"name":"Claudie Ossard Productions"}],
            "tagline":"One person can change your life forever.","status":"Released"}
            """.trimIndent()

        val facts = json.decodeFromString<TmdbRecommendation>(body).facts

        assertEquals(listOf("FR"), facts.countries)
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", facts.originalTitle)
        assertEquals("fr", facts.originalLanguage)
        assertEquals(listOf("fr"), facts.spokenLanguages)
        assertEquals(listOf("Claudie Ossard Productions"), facts.studios)
        assertEquals("One person can change your life forever.", facts.tagline)
        assertNull(facts.status)
    }

    @Test
    fun `a show's facts take its networks, creators, status and size`() {
        val body =
            """
            {"id":70523,"name":"Dark","original_name":"Dark","original_language":"de","origin_country":["DE"],
            "networks":[{"name":"Netflix"}],"production_companies":[{"name":"Wiedemann & Berg"}],
            "created_by":[{"name":"Baran bo Odar"},{"name":"Jantje Friese"}],"status":"Ended",
            "number_of_seasons":3,"number_of_episodes":26,"tagline":""}
            """.trimIndent()

        val facts = json.decodeFromString<TmdbRecommendation>(body).facts

        assertEquals(listOf("Netflix"), facts.studios)
        assertEquals(listOf("Baran bo Odar", "Jantje Friese"), facts.creators)
        assertEquals("Ended", facts.status)
        assertEquals(3, facts.seasonCount)
        assertEquals(26, facts.episodeCount)
        assertNull(facts.tagline)
    }

    @Test
    fun `a film with no origin country falls back to its production countries`() {
        val body = """{"id":1,"production_countries":[{"iso_3166_1":"GB"},{"iso_3166_1":"US"}]}"""

        assertEquals(listOf("GB", "US"), json.decodeFromString<TmdbRecommendation>(body).facts.countries)
    }

    @Test
    fun `a series whose panel sends a null backdrop still reads, with its country`() {
        val body =
            """
            {"seasons":[],"info":{"name":"Show","backdrop_path":[null],"tmdb":"0","country":"United States, Canada"},
            "episodes":{"1":[{"id":"10","episode_num":1,"title":"Pilot","container_extension":"mkv","season":1}]}}
            """.trimIndent()

        val detail = json.decodeFromString<SeriesInfo>(body).toDomain("1")

        assertNull(detail.backdropUrl)
        assertEquals(listOf("United States", "Canada"), detail.metadata.facts.countries)
    }

    @Test
    fun `a film's panel country is read`() {
        val body = """{"info":{"name":"Film","country":"France"},"movie_data":{"stream_id":1,"container_extension":"mkv"}}"""

        assertEquals(
            listOf("France"),
            json
                .decodeFromString<VodInfo>(body)
                .toDomain("1")
                .metadata.facts.countries,
        )
    }

    @Test
    fun `null and blank backdrops are not stored`() {
        assertNull(listOf<String?>(null, " ").backdropText())
        assertEquals("a.jpg,b.jpg", listOf(null, "a.jpg", "b.jpg").backdropText())
    }

    @Test
    fun `stored facts read back, and unreadable ones read as none`() {
        val facts = TitleFacts(countries = listOf("FR"), creators = listOf("A"), seasonCount = 2)

        assertEquals(facts, TitleFacts.fromJson(facts.toJson()))
        assertEquals(TitleFacts(), TitleFacts.fromJson("not json"))
        assertEquals(TitleFacts(), TitleFacts.fromJson(null))
    }
}
