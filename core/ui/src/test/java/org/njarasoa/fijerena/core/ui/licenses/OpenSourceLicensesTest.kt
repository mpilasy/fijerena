package org.njarasoa.fijerena.core.ui.licenses

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSourceLicensesTest {
    private val json =
        """
        {
          "libraries": [
            {"uniqueId": "b:lib", "name": "beta", "artifactVersion": "2.0", "licenses": ["MIT"]},
            {"uniqueId": "font:inter", "name": "Inter (font)", "artifactVersion": "", "licenses": ["OFL-1.1-inter", "missing"]},
            {"uniqueId": "a:lib", "name": "Alpha", "licenses": []}
          ],
          "licenses": {
            "MIT": {"name": "MIT License", "url": "https://opensource.org/licenses/MIT", "content": "Permission is hereby granted"},
            "OFL-1.1-inter": {"name": "SIL Open Font License 1.1"}
          }
        }
        """.trimIndent()

    @Test
    fun `entries come by name, each with its version and licences`() {
        val entries = OpenSourceLicenses.parse(json)
        assertEquals(listOf("Alpha", "beta", "Inter (font)"), entries.map { it.name })
        assertEquals("2.0", entries[1].version)
        assertEquals("Permission is hereby granted", entries[1].licenses.single().content)
    }

    @Test
    fun `a blank version is none, an unknown licence is left out, a missing text is blank`() {
        val font = OpenSourceLicenses.parse(json).single { it.id == "font:inter" }
        assertEquals(null, font.version)
        assertEquals(listOf("SIL Open Font License 1.1"), font.licenses.map { it.name })
        assertEquals("", font.licenses.single().content)
    }

    @Test
    fun `an unreadable list gives no entries`() {
        assertEquals(emptyList<OpenSourceEntry>(), OpenSourceLicenses.parse("not json"))
    }

    @Test
    fun `the app's own entry comes first, whatever its name`() {
        val withApp =
            json.replace(
                "\"libraries\": [",
                "\"libraries\": [\n{\"uniqueId\": \"app:fijerena\", \"name\": \"Fijerena\", \"licenses\": []},",
            )
        assertEquals(listOf("Fijerena", "Alpha", "beta", "Inter (font)"), OpenSourceLicenses.parse(withApp).map { it.name })
    }
}
