package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.SettingsExportManager.Companion.importedRefreshIntervalHours
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedEpgSource
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager.GlobalSettings
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity.Companion.REFRESH_OFF

/** A guide source's own refresh interval in a settings file — see docs/plans/20261003_sources-guide-profiles-plan.md → P5. */
class SettingsExportEpgIntervalTest {
    private fun decode(file: String) = SettingsExportManager.json.decodeFromString<ExportedSettings>(file)

    @Test
    fun `a file with the interval and a field this version doesn't know is read`() {
        val file =
            """{"version":5,"epgSources":[{"url":"u","providerName":"P","refreshIntervalHours":6,"someFutureField":1}],"someFutureSection":{}}"""

        val source = decode(file).epgSources.single()

        assertEquals(6, source.refreshIntervalHours)
        assertEquals(6, importedRefreshIntervalHours(source, GlobalSettings()))
    }

    @Test
    fun `an older file without the interval reads as not set`() {
        val source = decode("""{"version":5,"epgSources":[{"url":"u","providerName":"P"}]}""").epgSources.single()

        assertNull(source.refreshIntervalHours)
    }

    @Test
    fun `an older file's sources take its device-wide switch only when they carry no interval`() {
        val older = ExportedEpgSource(url = "u")
        val own = ExportedEpgSource(url = "u", refreshIntervalHours = 12)

        assertNull(importedRefreshIntervalHours(older, GlobalSettings(epgAutoRefreshEnabled = true)))
        assertEquals(REFRESH_OFF, importedRefreshIntervalHours(older, GlobalSettings(epgAutoRefreshEnabled = false)))
        assertEquals(12, importedRefreshIntervalHours(own, GlobalSettings(epgAutoRefreshEnabled = false)))
        assertEquals(
            REFRESH_OFF,
            importedRefreshIntervalHours(own.copy(refreshIntervalHours = 0), GlobalSettings(epgAutoRefreshEnabled = false)),
        )
    }

    @Test
    fun `the interval is written on export`() {
        val file =
            SettingsExportManager.json.encodeToString(
                ExportedSettings.serializer(),
                ExportedSettings(epgSources = listOf(ExportedEpgSource("u", refreshIntervalHours = 168))),
            )

        assertTrue(file.contains("\"refreshIntervalHours\": 168"))
    }
}
