package org.njarasoa.fijerena.core.ui.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Numbers, sizes and durations follow the app language of the Resources, not the default locale. */
class NumberUtilsTest {
    private val english = Locale.UK
    private val french = Locale.FRANCE

    private lateinit var originalLocale: Locale

    // A stale default locale (the phone language) must change nothing.
    @Before
    fun useAnotherDefaultLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    /** French groups with a narrow or plain no-break space; compare with a plain space. */
    private fun String.spaces() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun `counts are grouped the app language's way`() {
        assertEquals("195,300", NumberUtils.formatCount(195_300, english))
        assertEquals("195 300", NumberUtils.formatCount(195_300, french).spaces())
        assertEquals("999", NumberUtils.formatCount(999, french))
        assertEquals("195 300", NumberUtils.formatCount(resourcesIn(french), 195_300).spaces())
    }

    @Test
    fun `sizes use the decimal mark and units of the app language`() {
        assertEquals("19.3 MB", NumberUtils.formatBytes(resourcesIn(english), 20_237_517).spaces())
        assertEquals("19,3 Mo", NumberUtils.formatBytes(resourcesIn(french), 20_237_517).spaces())
        assertEquals("1,5 Go", NumberUtils.formatBytes(resourcesIn(french), 1_610_612_736).spaces())
        assertEquals("1.5 KB", NumberUtils.formatBytes(resourcesIn(english), 1_536).spaces())
        assertEquals("512 o", NumberUtils.formatBytes(resourcesIn(french), 512).spaces())
    }

    @Test
    fun `durations put a no-break space before the unit`() {
        assertEquals("1,337 ms", NumberUtils.formatDuration(1_337, english))
        assertEquals("1 337 ms", NumberUtils.formatDuration(1_337, french).spaces())
        assertEquals("45 s", NumberUtils.formatDuration(45_000, french))
        assertEquals("2 min 5 s", NumberUtils.formatDuration(125_000, english))
    }
}
