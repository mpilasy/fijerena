package org.njarasoa.fijerena.core.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The tag dialog's state (favourite tags plan, FT-D7). */
class FavoriteTagsDraftTest {
    @Test
    fun `the profile's tags are listed and the favourite's are checked`() {
        val draft = FavoriteTagsDraft.of(profileTags = listOf("Kids", "News"), itemTags = listOf("news"))

        assertEquals(listOf("Kids", "News"), draft.tags)
        assertEquals(listOf("News"), draft.result)
    }

    @Test
    fun `a new name is added checked, and an existing one is checked`() {
        val draft =
            FavoriteTagsDraft
                .of(listOf("News"), emptyList())
                .add("  Weekend ")
                .add("NEWS")
                .add("   ")

        assertEquals(listOf("News", "Weekend"), draft.tags)
        assertEquals(listOf("News", "Weekend"), draft.result)
    }

    @Test
    fun `toggle takes a tag off and puts it back`() {
        val draft = FavoriteTagsDraft.of(listOf("News", "Kids"), listOf("News", "Kids")).toggle("News")

        assertEquals(listOf("Kids"), draft.result)
        assertEquals(listOf("News", "Kids"), draft.toggle("News").result)
    }
}
