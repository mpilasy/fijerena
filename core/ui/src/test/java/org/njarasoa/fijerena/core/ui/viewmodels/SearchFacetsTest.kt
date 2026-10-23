package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaMetadata
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.ui.viewmodels.SearchViewModel.SearchResult
import java.util.Locale

/** Search's Decade and Genre filter (the search decade genre filter plan, Phase 1). */
class SearchFacetsTest {
    @Test
    fun `genre text splits into genres, the same genre in any case once`() {
        assertEquals(listOf("Drama", "Crime", "Thriller"), splitGenres("drama, Crime / Thriller | DRAMA"))
        assertEquals(listOf("Action", "Adventure"), splitGenres("Action & Adventure"))
        assertEquals(emptyList<String>(), splitGenres(null))
        assertEquals(emptyList<String>(), splitGenres(" , "))
    }

    @Test
    fun `the year is the item's, else its release date's, else the one in its name`() {
        assertEquals(1999, item("X", year = 1999, releaseDate = "2001-01-01").searchYear())
        assertEquals(2001, item("X (1980)", releaseDate = "2001-05-02").searchYear())
        assertEquals(2021, item("EN - Dune (2021)").searchYear())
        assertEquals(null, item("Dune").searchYear())
        assertEquals(1980, item("X (1980)", releaseDate = "0000-00-00").searchYear())
    }

    @Test
    fun `a result matches any chosen decade and any chosen genre`() {
        val drama90s = result("1", 1994, "Drama", "Crime")
        val choice = SearchFacetChoice(decades = setOf(1990, 2000), genres = setOf("crime", "comedy"))

        assertTrue(drama90s.matches(choice))
        assertFalse(result("2", 1984, "Crime").matches(choice))
        assertFalse(result("3", 1995, "Drama").matches(choice))
        // Missing data never matches a chosen section.
        assertFalse(result("4", null, "Crime").matches(choice))
        assertFalse(result("5", 1995).matches(choice))
        assertTrue(result("6", null).matches(SearchFacetChoice()))
    }

    @Test
    fun `a TMDB title matches by year and never under a genre choice`() {
        assertTrue(tmdb(1994).matches(SearchFacetChoice(decades = setOf(1990))))
        assertFalse(tmdb(1994).matches(SearchFacetChoice(genres = setOf("drama"))))
        assertFalse(tmdb(null).matches(SearchFacetChoice(decades = setOf(1990))))
    }

    @Test
    fun `each section counts what the other section's choice leaves`() {
        val results =
            listOf(
                result("1", 1994, "Drama"),
                result("2", 1998, "Comedy"),
                result("3", 2004, "Drama", "Crime"),
                result("4", null, "drama"),
            )

        val none = searchFacets(results, listOf(tmdb(1971)), SearchFacetChoice())
        assertEquals(listOf(2000 to 1, 1990 to 2, 1970 to 1), none.decades.map { it.value to it.count })
        assertEquals(listOf("Drama" to 3, "Comedy" to 1, "Crime" to 1), none.genres.map { it.label to it.count })

        val chosen = searchFacets(results, emptyList(), SearchFacetChoice(decades = setOf(1990), genres = setOf("crime")))
        // Decades under Crime: only 2000; 1990 stays listed (chosen) with nothing.
        assertEquals(listOf(2000 to 1, 1990 to 0), chosen.decades.map { it.value to it.count })
        // Genres in the 1990s: Drama and Comedy; Crime stays listed (chosen) with nothing.
        assertEquals(listOf("Comedy" to 1, "Drama" to 1, "Crime" to 0), chosen.genres.map { it.label to it.count })
    }

    @Test
    fun `a new search drops the decades and genres it has none of`() {
        val choice = SearchFacetChoice(decades = setOf(1990, 1960), genres = setOf("drama", "western"))

        val effective = effectiveFacetChoice(choice, listOf(result("1", 1994, "Drama")), listOf(tmdb(1965)))

        assertEquals(SearchFacetChoice(decades = setOf(1990, 1960), genres = setOf("drama")), effective)
    }

    @Test
    fun `a provider code gives a language, a region or both`() {
        assertEquals(ProviderCode("en", null), providerCode("EN - Dune (2021)"))
        assertEquals(ProviderCode("fr", null), providerCode("FR| Amélie"))
        assertEquals(ProviderCode("de", null), providerCode("GER - Das Boot"))
        assertEquals(ProviderCode("el", "GR"), providerCode("GR - Zorba"))
        assertEquals(ProviderCode("fa", "IR"), providerCode("IR - A Separation"))
        assertEquals(ProviderCode("en", "US"), providerCode("US| FLO PPV"))
        assertEquals(ProviderCode("en", "GB"), providerCode("UK| BBC One"))
        assertEquals(ProviderCode(null, "IN"), providerCode("IN - Lagaan"))
        assertEquals(ProviderCode(null, "AF"), providerCode("AF - CINAF TV"))
        assertEquals(ProviderCode(null, "EX"), providerCode("EX - Ljubav"))
        assertEquals(ProviderCode(null, "AS"), providerCode("AS - Murder Game (2026)"))
        assertEquals(ProviderCode(null, "SC"), providerCode("SC-DO - Shades (2025) (ES)"))
        assertEquals(ProviderCode("ar", null), providerCode("AR - Film"))
        assertEquals(ProviderCode("NF", null), providerCode("NF - Dark"))
        assertEquals(null, providerCode("Dune"))
    }

    @Test
    fun `a region is named in the app's language, a custom one by the screen`() {
        assertEquals("Greece", regionLabel("GR", Locale.UK) { null })
        assertEquals("Grèce", regionLabel("GR", Locale.FRANCE) { null })
        assertEquals("Africa", regionLabel("AF", Locale.UK) { if (it == "AF") "Africa" else null })
    }

    @Test
    fun `a language is named in the app's language, a code as it is`() {
        assertEquals("English", languageLabel("en", Locale.UK))
        assertEquals("Anglais", languageLabel("en", Locale.FRANCE))
        assertEquals("Greek", languageLabel("el", Locale.UK))
        assertEquals("NF", languageLabel("NF", Locale.UK))
    }

    @Test
    fun `a result matches any chosen language, and counts follow the other sections`() {
        val results =
            listOf(
                result("1", 1994, "Drama").copy(language = "en"),
                result("2", 1998, "Drama").copy(language = "fr"),
                result("3", 2004, "Drama").copy(language = "en"),
                result("4", 1995, "Drama").copy(region = "IN"),
            )
        assertEquals(listOf("4"), results.filter { it.matches(SearchFacetChoice(regions = setOf("IN"))) }.map { it.itemId })
        val choice = SearchFacetChoice(decades = setOf(1990), languages = setOf("en", "NF"))

        assertEquals(listOf("1"), results.filter { it.matches(choice) }.map { it.itemId })
        assertFalse(tmdb(1994).matches(SearchFacetChoice(languages = setOf("en"))))
        // Languages in the 1990s: English 1, French 1; NF is not in the results, so not listed.
        val facets = searchFacets(results, emptyList(), choice)
        assertEquals(listOf("en" to 1, "fr" to 1), facets.languages.map { it.value to it.count })
        // Decades in English: 2000 and 1990, one each.
        assertEquals(listOf(2000 to 1, 1990 to 1), facets.decades.map { it.value to it.count })
        assertEquals(
            SearchFacetChoice(decades = setOf(1990), languages = setOf("en")),
            effectiveFacetChoice(choice, results, emptyList()),
        )
    }

    private fun item(
        name: String,
        year: Int? = null,
        releaseDate: String? = null,
    ) = MediaItem(
        id = "1",
        name = name,
        mediaType = MediaType.MOVIE,
        categoryId = "c",
        metadata = MediaMetadata(year = year, releaseDate = releaseDate),
    )

    private fun result(
        id: String,
        year: Int?,
        vararg genres: String,
    ) = SearchResult(id, "Title $id", "c", "Cat", ContentType.TV_SHOWS, year = year, genres = genres.toList())

    private fun tmdb(year: Int?) = TmdbTitle(ContentType.MOVIES, "1", "T", null, year, null, null)
}
