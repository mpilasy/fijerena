package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixture
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgChannelEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgProgrammeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.time.Instant

/**
 * The guide index's parse functions ([XmltvParser.parseChannelForIndex],
 * [XmltvParser.parseProgrammeForIndex]) on the hand-written `xmltv/var-xmltv-*` files. Each file
 * shows one case of the XMLTV DTD (https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd) or of a
 * field report, as the file's comment says. The plain JVM gets its XML pull parser from kxml2, the
 * parser that Android ships.
 */
class XmltvVariantsTest {
    private data class Guide(
        val channels: List<EpgChannelEntity>,
        val programmes: List<EpgProgrammeEntity>,
    )

    /** Walks the file as `EpgIndexer.ingestFromStream` does, with no database. */
    private fun read(name: String): Guide {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(fixture("xmltv/$name").byteInputStream(), null)
        val channels = mutableListOf<EpgChannelEntity>()
        val programmes = mutableListOf<EpgProgrammeEntity>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                XmltvParser.parseChannelForIndex(parser)?.let { channels.add(it) }
            } else if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                XmltvParser.parseProgrammeForIndex(parser, timezoneOverrideHours = 0)?.let { programmes.add(it) }
            }
            event = parser.next()
        }
        return Guide(channels, programmes)
    }

    private fun epoch(utc: String) = Instant.parse(utc).epochSecond

    private val expectedTimes =
        listOf(
            Triple("News", epoch("2026-11-11T18:00:00Z"), epoch("2026-11-11T19:00:00Z")),
            Triple("Film", epoch("2026-11-11T19:00:00Z"), epoch("2026-11-11T20:30:00Z")),
            Triple("Music", epoch("2026-11-11T18:00:00Z"), epoch("2026-11-11T19:00:00Z")),
        )

    private fun times(name: String) = read(name).programmes.map { Triple(it.title, it.startEpoch, it.endEpoch) }

    @Test
    fun `14 digits with a space before the zone`() {
        assertEquals(expectedTimes, times("var-xmltv-time-14-digits.xml"))
    }

    @Test
    fun `12 digits without seconds, in local time`() {
        assertEquals(expectedTimes, times("var-xmltv-time-12-digits.xml"))
    }

    @Test
    fun `no space before the zone keeps the zone`() {
        assertEquals(expectedTimes, times("var-xmltv-time-no-space.xml"))
    }

    @Test
    fun `no zone is UTC`() {
        assertEquals(expectedTimes, times("var-xmltv-time-no-zone.xml"))
    }

    @Test
    fun `channels keep their id, first name and icon`() {
        val channels = read("var-xmltv-time-14-digits.xml").channels
        assertEquals(listOf("news.example", "music.example"), channels.map { it.xmltvId })
        assertEquals(listOf("News Channel", "Music Channel"), channels.map { it.displayName })
        assertEquals(listOf("https://img.invalid/news.png", null), channels.map { it.iconUrl })
    }

    @Test
    fun `CDATA and entities read as plain text`() {
        val guide = read("var-xmltv-text.xml")
        val (cartoon, music) = guide.programmes
        assertEquals("Tom & Jerry <Classics>", cartoon.title)
        assertEquals("tom & jerry <classics>", cartoon.titleLowercase)
        assertEquals("A cat chases a mouse. <b>Not</b> markup here.", cartoon.description)
        assertEquals("Cartoon", cartoon.category)
        assertEquals("Rock & Roll <Live> \"Tonight\" '26", music.title)
        assertEquals("Café music — live.", music.description)
        assertNull(music.category)
        assertEquals("Chaîne Info", guide.channels.single().displayName)
    }

    @Test
    fun `a title in two languages keeps the first one`() {
        val news = read("var-xmltv-text.xml").programmes[2]
        assertEquals("Le Journal", news.title)
        assertEquals("Les nouvelles du jour.", news.description)
    }

    @Test
    fun `a programme without stop is kept, with a default length`() {
        val shows = read("var-xmltv-structure.xml").programmes.filter { it.channelId == "news.example" }
        assertEquals(listOf("Morning Show", "Weather", "Late Film"), shows.map { it.title })
        // The index then ends each one at the next start on its channel (EpgIndexer.clipOverlaps).
        val starts = listOf("2026-11-11T18:00:00Z", "2026-11-11T18:30:00Z", "2026-11-11T19:00:00Z").map { epoch(it) }
        assertEquals(starts.map { it + XmltvParser.NO_STOP_LENGTH_SECONDS }, shows.map { it.endEpoch })
    }

    @Test
    fun `a channel without a display name shows its id`() {
        val nameless = read("var-xmltv-structure.xml").channels.single { it.xmltvId == "nameless.example" }
        assertEquals("nameless.example", nameless.displayName)
        assertEquals("https://img.invalid/nameless.png", nameless.iconUrl)
    }

    @Test
    fun `a programme for a channel the file does not list is kept`() {
        val unlisted = read("var-xmltv-structure.xml").programmes.single { it.channelId == "unlisted.example" }
        assertEquals("Unlisted Show", unlisted.title)
    }
}
