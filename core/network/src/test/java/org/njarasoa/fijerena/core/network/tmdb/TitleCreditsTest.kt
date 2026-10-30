package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Cast tab's people from TMDB's credits (docs/plans/20261030_cast-tab-plan.md, Phase 1). */
class TitleCreditsTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a film gives its full cast in billing order with their roles and photos, its directors and its writers`() {
        val cast = (1..12).joinToString(",") { """{"id":$it,"name":"Actor $it","character":"Role $it","profile_path":"/p$it.jpg"}""" }
        val film =
            json.decodeFromString<TmdbRecommendation>(
                """
                {"id":1,"title":"Film","credits":{"cast":[$cast],"crew":[
                  {"id":100,"name":"Dee Rector","job":"Director","department":"Directing"},
                  {"id":101,"name":"Sam Writer","job":"Screenplay","department":"Writing"},
                  {"id":101,"name":"Sam Writer","job":"Story","department":"Writing"},
                  {"id":102,"name":"Nora Novel","job":"Novel","department":"Writing"},
                  {"id":103,"name":"Pat Producer","job":"Producer","department":"Production"}
                ]}}
                """.trimIndent(),
            )

        val credits = titleCredits(film, movie = true)

        // The full cast: the Cast tab shows the first MAX_CAST_CARDS, the rest behind its More card.
        assertEquals((1..12).map { "Actor $it" }, credits.cast.map { it.name })
        assertEquals(CastMember("1", "Actor 1", "Role 1", "https://image.tmdb.org/t/p/w185/p1.jpg"), credits.cast.first())
        assertEquals(
            listOf(
                CrewLine(CrewRole.DIRECTOR, listOf(CrewPerson("100", "Dee Rector"))),
                CrewLine(CrewRole.WRITER, listOf(CrewPerson("101", "Sam Writer"), CrewPerson("102", "Nora Novel"))),
            ),
            credits.crew,
        )
    }

    @Test
    fun `a show gives each actor's role in the most episodes, its creators and its main writers`() {
        val writers =
            (1..7).joinToString(",") {
                """{"id":${200 + it},"name":"Writer $it","department":"Writing","total_episode_count":$it,"jobs":[{"job":"Writer","episode_count":$it}]}"""
            }
        val show =
            json.decodeFromString<TmdbRecommendation>(
                """
                {"id":2,"name":"Show","created_by":[{"id":300,"name":"Cree Ator"}],
                 "aggregate_credits":{"cast":[
                   {"id":1,"name":"Lead","roles":[{"character":"Young Lead","episode_count":2},{"character":"Lead","episode_count":40}],"total_episode_count":42},
                   {"id":2,"name":"Guest","roles":[{"character":"","episode_count":1}],"total_episode_count":1}
                 ],"crew":[$writers,{"id":400,"name":"Dee Rector","department":"Directing","jobs":[{"job":"Director","episode_count":9}]}]}}
                """.trimIndent(),
            )

        val credits = titleCredits(show, movie = false)

        assertEquals(listOf("Lead", null), credits.cast.map { it.character })
        assertNull(credits.cast.first().photoUrl)
        assertEquals(CrewLine(CrewRole.CREATOR, listOf(CrewPerson("300", "Cree Ator"))), credits.crew[0])
        assertEquals(CrewRole.WRITER, credits.crew[1].role)
        assertEquals(listOf("Writer 7", "Writer 6", "Writer 5", "Writer 4", "Writer 3"), credits.crew[1].people.map { it.name })
        // The cast names TmdbTitleViewModel shows come from aggregate_credits for a show.
        assertEquals(listOf("Lead", "Guest"), show.anyCredits?.cast?.map { it.name })
    }

    @Test
    fun `no credits give no lines and no cards`() {
        val credits = titleCredits(TmdbRecommendation(id = 3, title = "Bare"), movie = true)
        assertEquals(TitleCredits(emptyList(), emptyList()), credits)
    }

    @Test
    fun `a More card adds at most a step of actors, never more than are left`() {
        assertEquals(30, castMoreCount(shown = 10, total = 7666))
        assertEquals(2, castMoreCount(shown = 10, total = 12))
        assertEquals(0, castMoreCount(shown = 40, total = 40))
    }
}
