package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStyleSpacingTest {
    @Test
    fun `the default Looks keep the base spacing`() {
        assertEquals(1f, ModernStyle.spacing)
        assertEquals(1f, MaterialStyle.spacing)
    }

    @Test
    fun `every Look's density stays near the base`() {
        AllUiStyles.forEach { style ->
            assertTrue("${style.id}: ${style.spacing}", style.spacing in 0.85f..1.15f)
        }
    }

    @Test
    fun `the base steps keep their values`() {
        assertEquals(
            listOf(0.dp, 2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp, 48.dp),
            with(CinemaSpacing.Base) { listOf(none, xxxs, xxs, xs, sm, md, lg, xl, xxl) },
        )
    }
}
