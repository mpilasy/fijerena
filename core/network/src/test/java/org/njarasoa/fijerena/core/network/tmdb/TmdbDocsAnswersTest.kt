package org.njarasoa.fijerena.core.network.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture

/**
 * The TMDB models on answers in the shape of the public API reference
 * (https://developer.themoviedb.org/reference, the docs' own examples: movie 550, show 1399,
 * person 287, collection 10), read with [TmdbJson] as [TmdbApiService] reads them. The
 * `var-<endpoint>-docs.json` files are written by hand from the reference (2026-11-11), trimmed to
 * 3 items a list; no key or recording is needed (PD-Q3 (a)). `var-movie-empty-fields.json` is a film
 * that is not out yet: no poster, an empty release date, a runtime of 0.
 */
class TmdbDocsAnswersTest {
    private inline fun <reified T> read(name: String): T = TmdbJson.decodeFromString<T>(fixture("tmdb/$name"))

    @Test
    fun `a film's details read with its credits, videos and facts`() {
        val film = read<TmdbRecommendation>("var-movie-docs.json")

        assertEquals("Fight Club", film.displayTitle)
        assertEquals(1999, film.year)
        assertEquals(139, film.runtime)
        assertEquals(listOf("Drama", "Thriller"), film.genres.map { it.name })
        assertNull(film.belongsToCollection)
        assertEquals(listOf("US"), film.facts.countries)
        assertEquals(listOf("Regency Enterprises", "Fox 2000 Pictures"), film.facts.studios)
        assertEquals("Mischief. Mayhem. Soap.", film.facts.tagline)
        // A film's "Released" says nothing.
        assertNull(film.facts.status)
        assertEquals(listOf("Edward Norton", "Brad Pitt", "Helena Bonham Carter"), film.anyCredits?.cast?.map { it.name })
        assertEquals(
            "Director",
            film.credits
                ?.crew
                ?.first()
                ?.job,
        )
        assertEquals("trailerKey01", TmdbApiService.bestTrailerKey(film.videos!!.results, "en"))
    }

    @Test
    fun `a show's details read with its whole cast, creators and networks`() {
        val show = read<TmdbRecommendation>("var-tv-docs.json")

        assertEquals("Game of Thrones", show.displayTitle)
        assertEquals(2011, show.year)
        assertEquals(listOf("Peter Dinklage", "Emilia Clarke"), show.anyCredits?.cast?.map { it.name })
        assertEquals(
            67,
            show.anyCredits
                ?.cast
                ?.first()
                ?.totalEpisodeCount,
        )
        assertEquals(
            "Tyrion Lannister",
            show.anyCredits
                ?.cast
                ?.first()
                ?.roles
                ?.single()
                ?.character,
        )
        assertEquals(listOf("HBO"), show.facts.studios)
        assertEquals(listOf("David Benioff", "D. B. Weiss"), show.facts.creators)
        assertEquals("Ended", show.facts.status)
        assertEquals(8, show.facts.seasonCount)
        assertEquals(73, show.facts.episodeCount)
        assertNull(TmdbApiService.bestTrailerKey(show.videos!!.results, "en"))
    }

    @Test
    fun `a season reads every episode with its guest stars`() {
        val season = read<TmdbSeasonResponse>("var-tv_season-docs.json")

        assertEquals(1, season.seasonNumber)
        assertEquals(listOf(1, 2, 3), season.episodes.map { it.episodeNumber })
        assertEquals(listOf(62, 56, null), season.episodes.map { it.runtime })
        assertEquals(
            listOf("Will", "Ser Waymar Royce"),
            season.episodes
                .first()
                .guestStars
                .map { it.character },
        )
        assertNull(season.episodes.last().stillPath)
    }

    @Test
    fun `a multi search reads films, shows and people`() {
        val results = read<TmdbRecommendationsResponse>("var-search_multi-docs.json")

        assertEquals(listOf("movie", "tv", "person"), results.results.map { it.mediaType })
        assertEquals(listOf("Fight Club", "Game of Thrones", "Brad Pitt"), results.results.map { it.displayTitle })
        assertEquals(listOf(1999, 2011, null), results.results.map { it.year })
        assertEquals(
            listOf(550),
            results.results
                .last()
                .knownFor
                .map { it.id },
        )
        assertEquals(1, results.totalPages)
    }

    @Test
    fun `release dates and content ratings read per country`() {
        val dates = read<TmdbReleaseDatesResponse>("var-movie_release_dates-docs.json")
        val ratings = read<TmdbContentRatingsResponse>("var-tv_content_ratings-docs.json")

        assertEquals(listOf("GB", "US"), dates.results.map { it.country })
        assertEquals(
            listOf("", "R"),
            dates.results
                .last()
                .releaseDates
                .map { it.certification },
        )
        assertEquals(
            listOf(1, 3),
            dates.results
                .last()
                .releaseDates
                .map { it.type },
        )
        assertEquals("TV-MA", ratings.results.single { it.country == "US" }.rating)
    }

    @Test
    fun `images give the best logo in the language and the best backdrop without text`() {
        val images = read<TmdbImagesResponse>("var-movie_images-docs.json")

        assertEquals(3, images.logos.size)
        assertEquals("https://image.tmdb.org/t/p/w500/logoEnglishWide.png", TmdbApiService.bestLogoUrl(images.logos, "en"))
        assertEquals("https://image.tmdb.org/t/p/w1280/backdropPlain.jpg", TmdbApiService.bestBackdropUrl(images.backdrops))
    }

    @Test
    fun `a person reads with films and shows in front of and behind the camera`() {
        val person = read<TmdbPerson>("var-person-docs.json")

        assertEquals("Brad Pitt", person.name)
        assertEquals("1963-12-18", person.birthday)
        assertNull(person.deathday)
        val credits = person.combinedCredits!!
        assertEquals(listOf("Fight Club", "Friends"), credits.cast.map { it.toRecommendation().displayTitle })
        assertEquals(listOf("movie", "tv"), credits.cast.map { it.mediaType })
        assertEquals(1, credits.cast.last().episodeCount)
        assertEquals("Producer", credits.crew.single().job)
    }

    @Test
    fun `a collection reads its films in order`() {
        val collection = read<TmdbCollectionResponse>("var-collection-docs.json")

        assertEquals("Star Wars Collection", collection.name)
        assertEquals(listOf(11, 1891, 140607), collection.parts.map { it.id })
        assertEquals(listOf(1977, 1980, 2015), collection.parts.map { it.year })
    }

    @Test
    fun `a film that is not out yet reads with no year, poster or tagline`() {
        val film = read<TmdbRecommendation>("var-movie-empty-fields.json")

        assertEquals("Film 1", film.displayTitle)
        assertNull(film.year)
        assertNull(TmdbApiService.posterUrl(film.posterPath))
        assertNull(film.facts.tagline)
        assertNull(film.facts.originalTitle)
        assertEquals("In Production", film.facts.status)
        assertEquals(emptyList<String>(), film.anyCredits?.cast?.map { it.name })
    }
}
