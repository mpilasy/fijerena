package org.njarasoa.fijerena.core.ui.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeVideoIdTest {
    @Test
    fun readsTheIdFromEveryYouTubeLinkForm() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            " https://www.youtube.com/watch?v=dQw4w9WgXcQ ",
        ).forEach { assertEquals(it, "dQw4w9WgXcQ", youTubeVideoId(it)) }
    }

    @Test
    fun givesNullForOtherLinks() {
        listOf(
            "https://vimeo.com/123456789",
            "https://www.youtube.com/channel/UC1234567890",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch",
            "not a url",
            "",
        ).forEach { assertNull(it, youTubeVideoId(it)) }
    }

    @Test
    fun trailerTimeIsMinutesAndSeconds() {
        assertEquals("0:00", trailerTime(-3f))
        assertEquals("0:09", trailerTime(9.9f))
        assertEquals("2:05", trailerTime(125f))
    }

    @Test
    fun oEmbedTitleReadsTheTitle() {
        assertEquals("Arrival - Final Trailer", oEmbedTitle("""{"title":"Arrival - Final Trailer","author_name":"Paramount"}"""))
        assertNull(oEmbedTitle("""{"title":" "}"""))
        assertNull(oEmbedTitle("Not Found"))
    }
}
