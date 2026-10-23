package org.njarasoa.fijerena.core.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.WatchHistoryFixtures
import org.njarasoa.fijerena.core.player.domain.EpisodeId
import org.njarasoa.fijerena.core.player.domain.SeriesId

/**
 * Watch history is JSON in SharedPreferences, written by every earlier version of the app. Typing
 * the ids must not change a byte of that: a value class serializes as the string it wraps, and if
 * that ever stopped being true every device would silently lose its history.
 */
class WatchedItemSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun readsIdsWrittenBeforeTheyHadTypes() {
        val item = json.decodeFromString<WatchedItem>(WatchHistoryFixtures.Stored.LAW_AND_ORDER)

        assertEquals(EpisodeId("242136"), item.episodeId)
        assertEquals(SeriesId("4080"), item.seriesId)
        assertEquals("242136", item.itemId)
    }

    @Test
    fun writesThemBackAsPlainStrings() {
        val encoded = json.encodeToString(json.decodeFromString<WatchedItem>(WatchHistoryFixtures.Stored.LAW_AND_ORDER))

        assertTrue("""expected a bare string for seriesId, got: $encoded""", encoded.contains(""""seriesId":"4080""""))
        assertTrue("""expected a bare string for episodeId, got: $encoded""", encoded.contains(""""episodeId":"242136""""))
    }

    @Test
    fun aRowWithNoIdsAtAllStillReads() {
        // The shape six rows on the test phone are in: written by a session too short to record
        // what was playing.
        val item = json.decodeFromString<WatchedItem>(WatchHistoryFixtures.Stored.ANONYMOUS)

        assertEquals(null, item.episodeId)
        assertEquals(null, item.seriesId)
    }
}
