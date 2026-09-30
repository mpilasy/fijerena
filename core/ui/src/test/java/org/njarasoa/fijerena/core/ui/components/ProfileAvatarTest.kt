package org.njarasoa.fijerena.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileAvatarTest {
    @Test
    fun `initial is the first letter, uppercased`() {
        assertEquals("T", initialOf("tahiry"))
        assertEquals("É", initialOf(" élodie"))
    }

    @Test
    fun `an emoji initial is kept whole, not split in half`() {
        assertEquals("🍿", initialOf("🍿 Movie night"))
    }

    @Test
    fun `a blank name has no initial`() {
        assertEquals("", initialOf("   "))
    }
}
