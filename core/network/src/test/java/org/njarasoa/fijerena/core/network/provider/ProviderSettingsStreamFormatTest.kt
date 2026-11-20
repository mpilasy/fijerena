package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.api.LiveStreamFormat

/** Auto is a flag of its own, so an older version (synced settings) still reads "ts" or "m3u8". */
class ProviderSettingsStreamFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `settings saved before Auto decode as Auto`() {
        val settings = json.decodeFromString<ProviderSettings>("""{"streamOutputFormat":"ts"}""")
        assertTrue(settings.autoStreamFormat)
        assertEquals(LiveStreamFormat.AUTO, settings.streamFormatChoice)
        assertEquals("ts", settings.streamOutputFormat)
    }

    @Test
    fun `a manual choice turns Auto off and keeps the format`() {
        val settings = ProviderSettings().withStreamFormatChoice("ts")
        assertFalse(settings.autoStreamFormat)
        assertEquals("ts", settings.streamOutputFormat)
        assertEquals("ts", settings.streamFormatChoice)
        assertEquals(
            "ts",
            json.decodeFromString<ProviderSettings>(json.encodeToString(ProviderSettings.serializer(), settings)).streamFormatChoice,
        )
    }

    @Test
    fun `choosing Auto again keeps the stored format for older versions`() {
        val settings = ProviderSettings(streamOutputFormat = "ts", autoStreamFormat = false).withStreamFormatChoice(LiveStreamFormat.AUTO)
        assertTrue(settings.autoStreamFormat)
        assertEquals("ts", settings.streamOutputFormat)
    }
}
