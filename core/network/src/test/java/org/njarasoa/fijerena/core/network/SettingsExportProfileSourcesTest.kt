package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedProvider
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSettings

/** A settings file carries whether each source is one of the active profile's, and an import applies it. */
class SettingsExportProfileSourcesTest {
    private fun provider(inProfile: Boolean? = null) =
        ExportedProvider(name = "P", url = "http://p.test", username = "me", type = "XTREAM", inProfile = inProfile)

    @Test
    fun `a source's profile flag is written and read back`() {
        val written = SettingsExportManager.json.encodeToString(ExportedProvider.serializer(), provider(inProfile = false))

        assertEquals(provider(inProfile = false), SettingsExportManager.json.decodeFromString(ExportedProvider.serializer(), written))
    }

    @Test
    fun `no flag is written when the profile had no sources stored`() {
        val written = SettingsExportManager.json.encodeToString(ExportedProvider.serializer(), provider())

        assertFalse("inProfile" in written)
    }

    @Test
    fun `an older file has no flag`() {
        val file = """{"version":5,"providers":[{"name":"P","url":"http://p.test","username":"me","type":"XTREAM"}]}"""

        assertNull(
            SettingsExportManager.json
                .decodeFromString<ExportedSettings>(file)
                .providers
                .single()
                .inProfile,
        )
    }

    @Test
    fun `an import turns the file's sources on and off and keeps the profile's others`() {
        val after =
            SettingsExportManager.importedProfileSources(
                current = setOf("kept", "dropped"),
                changes = mapOf("added" to true, "dropped" to false),
            )

        assertEquals(setOf("kept", "added"), after)
    }

    @Test
    fun `an import into a profile with nothing stored stores the file's on sources`() {
        assertEquals(setOf("a"), SettingsExportManager.importedProfileSources(null, mapOf("a" to true, "b" to false)))
    }
}
