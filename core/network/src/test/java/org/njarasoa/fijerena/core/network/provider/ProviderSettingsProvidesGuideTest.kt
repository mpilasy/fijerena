package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncPayloads

/**
 * "Provides a guide" lives in [ProviderSettings], which is stored, synced and exported whole as
 * JSON. Older payloads lack the fields; older app versions see them as unknown keys. Every reader
 * of that JSON (ProviderRepository, SettingsExportManager, SyncPayloads) is built with
 * `ignoreUnknownKeys = true`, as here.
 */
class ProviderSettingsProvidesGuideTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** [ProviderSettings] as an app version before "Provides a guide" declared it (a subset is enough). */
    @Serializable
    private data class OlderProviderSettings(
        val watchHistorySize: Int = 25,
        val streamOutputFormat: String = "m3u8",
        val playlistType: String = "m3u_plus",
    )

    @Test
    fun `an older payload without the fields decodes to not set`() {
        val settings = json.decodeFromString<ProviderSettings>("""{"watchHistorySize":30,"streamOutputFormat":"ts"}""")
        assertNull(settings.providesGuide)
        assertFalse(settings.providesGuideSetByUser)
        assertEquals(30, settings.watchHistorySize)
        assertEquals(true, settings.providesGuideOn)
    }

    @Test
    fun `a payload with the fields and an unknown key decodes`() {
        val settings =
            json.decodeFromString<ProviderSettings>(
                """{"providesGuide":false,"providesGuideSetByUser":true,"someFutureKey":{"a":1},"playlistType":"simple"}""",
            )
        assertEquals(false, settings.providesGuide)
        assertEquals(true, settings.providesGuideSetByUser)
        assertEquals("simple", settings.playlistType)
    }

    @Test
    fun `an older app version decodes the new fields as unknown keys`() {
        val encoded = json.encodeToString(ProviderSettings(providesGuide = false, providesGuideSetByUser = true, playlistType = "simple"))
        val older = json.decodeFromString<OlderProviderSettings>(encoded)
        assertEquals("simple", older.playlistType)
    }

    @Test
    fun `the sync payload carries the settings untouched`() {
        val settings = json.encodeToString(ProviderSettings(providesGuide = false))
        val provider = SyncPayloads.Provider("Name", "http://h", "u", "XTREAM", "", settings)
        val received = SyncPayloads.json.decodeFromString<SyncPayloads.Provider>(SyncPayloads.json.encodeToString(provider))
        assertEquals(settings, received.providerSettings)
        assertEquals(false, json.decodeFromString<ProviderSettings>(received.providerSettings).providesGuide)
    }
}
