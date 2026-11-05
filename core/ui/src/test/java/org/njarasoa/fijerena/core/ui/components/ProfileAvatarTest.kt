package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** An avatar shows the initials of a name, or the first 4 letters of one word, in lower case. */
class ProfileAvatarTest {
    @Test
    fun `a name of several words shows their initials, at most 4`() {
        assertEquals(listOf("g", "u"), avatarLetters("Grown Up"))
        assertEquals(listOf("m", "j", "w"), avatarLetters("Mary Jane Watson"))
        assertEquals(listOf("a", "b", "c", "d"), avatarLetters("Ann Bob Cy Dee Eve"))
    }

    @Test
    fun `a name of one word shows its first 4 letters`() {
        assertEquals(listOf("a", "d", "m", "i"), avatarLetters("Admin"))
        assertEquals(listOf("k", "i", "d"), avatarLetters("Kid"))
        assertEquals(listOf("a", "l"), avatarLetters("Al"))
        assertEquals(listOf("é"), avatarLetters(" É "))
    }

    @Test
    fun `punctuation separates words as spaces do`() {
        assertEquals(listOf("g", "u"), avatarLetters("Grown-up"))
        assertEquals(listOf("t", "j"), avatarLetters("tom_jones"))
        assertEquals(listOf("m", "d"), avatarLetters("Mum & Dad"))
    }

    @Test
    fun `an emoji is kept whole, not split in half`() {
        assertEquals(listOf("🍿", "m", "n"), avatarLetters("🍿 Movie night"))
        assertEquals(listOf("👨‍👩‍👧"), avatarLetters("👨‍👩‍👧"))
    }

    @Test
    fun `a blank name has no letters`() {
        assertEquals(emptyList<String>(), avatarLetters("   "))
        assertEquals(emptyList<String>(), avatarLetters("--"))
    }
}

/** The avatar's letters stay inside its real shape: a ray from the centre leaves it at its edge. */
class EdgeDistanceTest {
    private val diagonal = (1 / kotlin.math.sqrt(2.0)).toFloat()

    @Test
    fun `a circle's edge is its radius in every direction`() {
        assertEquals(50f, edgeDistance(1f, 0f, 50f, 50f), 0.01f)
        assertEquals(50f, edgeDistance(diagonal, diagonal, 50f, 50f), 0.01f)
    }

    @Test
    fun `a square's edge reaches its corner`() {
        assertEquals(50f, edgeDistance(1f, 0f, 50f, 0f), 0.01f)
        assertEquals(70.71f, edgeDistance(diagonal, diagonal, 50f, 0f), 0.01f)
    }

    @Test
    fun `a rounded square's corner is cut by its arc`() {
        // Corner radius 20: the arc's centre at (30, 30); along the diagonal the edge is 30√2 + 20.
        assertEquals(62.43f, edgeDistance(diagonal, diagonal, 50f, 20f), 0.01f)
        assertEquals(50f, edgeDistance(1f, 0.2f, 50f, 20f), 0.01f)
    }
}
