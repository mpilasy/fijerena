package org.njarasoa.fijerena.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tag rules of the favourite tags plan, FT-D4. */
class FavoriteTagsTest {
    @Test
    fun `a tag is trimmed and cut to the longest length`() {
        assertEquals("News", FavoriteTags.clean("  News  "))
        assertEquals("a".repeat(FavoriteTags.MAX_LENGTH), FavoriteTags.clean("a".repeat(40)))
        assertNull(FavoriteTags.clean("   "))
    }

    @Test
    fun `names equal without regard to case are one tag, in the existing spelling`() {
        assertEquals(listOf("News", "Kids"), FavoriteTags.normalise(listOf("news", " Kids", "NEWS", ""), existing = listOf("News")))
    }

    @Test
    fun `free text keeps commas and quotes`() {
        assertEquals(listOf("Sat, Sun", "Dad's \"picks\""), FavoriteTags.normalise(listOf("Sat, Sun", "Dad's \"picks\"")))
    }

    @Test
    fun `rename merges into a tag of the new name`() {
        assertEquals(listOf("Sport", "News"), FavoriteTags.rename(listOf("sports", "News", "sport"), from = "Sports", to = "Sport"))
        assertEquals(listOf("News"), FavoriteTags.rename(listOf("News"), from = "Kids", to = "Children"))
    }

    @Test
    fun `remove drops the tag without regard to case`() {
        assertEquals(listOf("Kids"), FavoriteTags.remove(listOf("News", "Kids"), "news"))
    }

    @Test
    fun `tags round-trip through the export, and an older file has none`() {
        val json = Json { ignoreUnknownKeys = true }
        val favorite = SettingsExportManager.ExportedFavorite("7", "BBC One", "c1", "LIVE_TV", tags = listOf("News", "Sat, Sun"))

        val text = json.encodeToString(SettingsExportManager.ExportedFavorite.serializer(), favorite)

        assertEquals(favorite, json.decodeFromString(SettingsExportManager.ExportedFavorite.serializer(), text))
        val old = """{"itemId":"7","itemName":"BBC One","categoryId":"c1","contentType":"LIVE_TV"}"""
        assertEquals(emptyList<String>(), json.decodeFromString(SettingsExportManager.ExportedFavorite.serializer(), old).tags)
    }
}
