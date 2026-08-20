package org.njarasoa.fijerena.core.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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

    /** Copied from a real device (provider 9), the row that started the whole investigation. */
    private val storedRow =
        """
        {"itemId":"242136","itemName":"EN - Law & Order - S06E18","categoryId":"156",
        "contentType":"TV_SHOWS","timestamp":1787181459349,"playbackPosition":37365,
        "duration":2811558,"isCompleted":false,"episodeId":"242136","episodeExtension":null,
        "seriesId":"4080","seriesName":"EN - Law & Order (1990) (US)","audioTrackIndex":0,
        "subtitleTrackIndex":-1}
        """.trimIndent().replace("\n", "")

    @Test
    fun readsIdsWrittenBeforeTheyHadTypes() {
        val item = json.decodeFromString<WatchedItem>(storedRow)

        assertEquals(EpisodeId("242136"), item.episodeId)
        assertEquals(SeriesId("4080"), item.seriesId)
        assertEquals("242136", item.itemId)
    }

    @Test
    fun writesThemBackAsPlainStrings() {
        val encoded = json.encodeToString(json.decodeFromString<WatchedItem>(storedRow))

        assertTrue("""expected a bare string for seriesId, got: $encoded""", encoded.contains(""""seriesId":"4080""""))
        assertTrue("""expected a bare string for episodeId, got: $encoded""", encoded.contains(""""episodeId":"242136""""))
    }

    @Test
    fun aRowWithNoIdsAtAllStillReads() {
        // The shape six rows on the test phone are in: written by a session too short to record
        // what was playing.
        val legacy =
            """{"itemId":"749050","itemName":"FR - From - S01E01","categoryId":"153","contentType":"TV_SHOWS"}"""

        val item = json.decodeFromString<WatchedItem>(legacy)

        assertEquals(null, item.episodeId)
        assertEquals(null, item.seriesId)
    }
}
