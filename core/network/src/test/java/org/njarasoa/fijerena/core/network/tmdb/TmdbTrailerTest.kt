package org.njarasoa.fijerena.core.network.tmdb

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTrailerTest {
    private fun video(
        key: String,
        type: String = "Trailer",
        site: String = "YouTube",
        official: Boolean = true,
        language: String = "en",
    ) = TmdbVideo(key = key, site = site, type = type, official = official, language = language)

    @Test
    fun `a trailer comes before a teaser`() {
        assertEquals("t", TmdbApiService.bestTrailerKey(listOf(video("s", type = "Teaser"), video("t")), "en"))
    }

    @Test
    fun `an official trailer comes before a fan one, then the app language`() {
        val videos = listOf(video("fan", official = false, language = "fr"), video("en"), video("fr", language = "fr"))
        assertEquals("fr", TmdbApiService.bestTrailerKey(videos, "fr"))
    }

    @Test
    fun `only YouTube trailers and teasers count`() {
        assertNull(TmdbApiService.bestTrailerKey(listOf(video("v", site = "Vimeo"), video("c", type = "Clip")), "en"))
    }

    @Test
    fun `videos come with the details`() {
        val json = """{"id":1,"videos":{"results":[{"key":"abc","site":"YouTube","type":"Trailer","official":true,"iso_639_1":"en"}]}}"""
        val details = Json { ignoreUnknownKeys = true }.decodeFromString(TmdbRecommendation.serializer(), json)
        assertEquals("abc", TmdbApiService.bestTrailerKey(details.videos!!.results, "en"))
    }

    @Test
    fun `the videos call alone decodes the same way`() {
        val json = """{"id":1,"results":[{"key":"xyz","site":"YouTube","type":"Teaser","official":true,"iso_639_1":"en"}]}"""
        val videos = Json { ignoreUnknownKeys = true }.decodeFromString(TmdbVideos.serializer(), json)
        assertEquals("xyz", TmdbApiService.bestTrailerKey(videos.results, "en"))
    }
}
