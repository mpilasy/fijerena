package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedProvider
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSettings

/**
 * A settings file carries no active source any more (multi-source plan, Phase 6): none is written,
 * and an older file's `isActive` is read past, never acted on.
 */
class SettingsExportActiveFlagTest {
    @Test
    fun `an exported source has no active flag`() {
        val written =
            SettingsExportManager.json.encodeToString(
                ExportedProvider.serializer(),
                ExportedProvider(name = "P", url = "http://p.test", username = "me", type = "XTREAM"),
            )

        assertFalse("isActive" in written)
    }

    @Test
    fun `an older file's active flag is ignored`() {
        val file =
            """{"version":5,"providers":[{"name":"P","url":"http://p.test","username":"me","type":"XTREAM","isActive":true}]}"""

        val provider =
            SettingsExportManager.json
                .decodeFromString<ExportedSettings>(file)
                .providers
                .single()

        assertEquals(ExportedProvider(name = "P", url = "http://p.test", username = "me", type = "XTREAM"), provider)
    }
}
