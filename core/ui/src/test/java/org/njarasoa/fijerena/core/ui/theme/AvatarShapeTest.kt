package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The avatar is as round as the Look's buttons: pill buttons give a circle, square buttons a square. */
class AvatarShapeTest {
    @Test
    fun `the avatar's corner follows the button's corner`() {
        assertEquals(50, CinemaShapeRoles.avatarCornerPercent(100.dp))
        assertEquals(42, CinemaShapeRoles.avatarCornerPercent(20.dp))
        assertEquals(17, CinemaShapeRoles.avatarCornerPercent(8.dp))
        assertEquals(0, CinemaShapeRoles.avatarCornerPercent(0.dp))
    }
}
