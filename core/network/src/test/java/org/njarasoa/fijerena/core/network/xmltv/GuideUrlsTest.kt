package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Two guide source URLs are one guide when [GuideUrls.matchKey] matches (OG-Q5). */
class GuideUrlsTest {
    private fun same(
        a: String,
        b: String,
    ) = assertEquals(GuideUrls.matchKey(a), GuideUrls.matchKey(b))

    private fun different(
        a: String,
        b: String,
    ) = assertNotEquals(GuideUrls.matchKey(a), GuideUrls.matchKey(b))

    @Test
    fun `scheme and host case, a default port, a trailing slash and spaces do not count`() {
        same("http://EPG.Example.com/guide.xml", "http://epg.example.com/guide.xml")
        same("HTTPS://epg.example.com/guide.xml", "https://epg.example.com/guide.xml")
        same("http://epg.example.com:80/guide.xml", "http://epg.example.com/guide.xml")
        same("https://epg.example.com:443/guide.xml", "https://epg.example.com/guide.xml")
        same("http://epg.example.com/epg/", "http://epg.example.com/epg")
        same("  http://epg.example.com/guide.xml ", "http://epg.example.com/guide.xml")
    }

    @Test
    fun `the path, the query, another port or scheme are another guide`() {
        different("http://epg.example.com/Guide.xml", "http://epg.example.com/guide.xml")
        different("http://epg.example.com/guide.xml?c=us", "http://epg.example.com/guide.xml?c=uk")
        different("http://epg.example.com:8080/guide.xml", "http://epg.example.com/guide.xml")
        different("http://epg.example.com/guide.xml", "https://epg.example.com/guide.xml")
    }

    @Test
    fun `a URL it cannot read is its trimmed text`() {
        assertEquals("not a url", GuideUrls.matchKey(" not a url "))
    }
}
