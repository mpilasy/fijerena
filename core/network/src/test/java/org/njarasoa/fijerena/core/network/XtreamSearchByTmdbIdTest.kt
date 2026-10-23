package org.njarasoa.fijerena.core.network

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.model.XtreamStream

/** A search for a number also finds the titles with that TMDB id, ahead of the name matches. */
class XtreamSearchByTmdbIdTest {
    private val repository = mockk<XtreamRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiService>(relaxed = true)
    private val provider = XtreamMediaProvider(providerId = 9L, repository = repository, tmdb = tmdb)

    private fun movie(
        id: Int,
        name: String,
    ) = XtreamStream(num = id, name = name, streamType = "movie", streamId = id, categoryId = "1")

    @Test
    fun aNumberFindsItsTmdbIdFirstThenNames() =
        runTest {
            val nikita = movie(1, "FR - Nikita (1990)")
            val film1917 = movie(2, "EN - 1917 (2019)")
            coEvery { repository.getAlternateStreams(ContentType.MOVIES, "9322", -1) } returns listOf(nikita)
            coEvery { repository.searchByFts(ContentType.MOVIES, "9322*", false) } returns listOf(film1917, nikita)

            val names = provider.search("9322", ContentType.MOVIES, false)!!.getOrThrow().map { it.name }

            assertEquals(listOf("FR - Nikita (1990)", "EN - 1917 (2019)"), names)
        }

    @Test
    fun wordsAreNotLookedUpAsTmdbIds() =
        runTest {
            provider.search("nikita 1990", ContentType.MOVIES, false)

            coVerify(exactly = 0) { repository.getAlternateStreams(any(), any(), any()) }
        }
}
