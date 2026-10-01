package org.njarasoa.fijerena.core.ui.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class NumberUtilsTest {
    private lateinit var originalLocale: Locale

    @Before
    fun useDecimalCommaLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `byte sizes use a decimal point even in a decimal-comma locale`() {
        assertEquals("1.5 GB", NumberUtils.formatBytes(1_610_612_736))
        assertEquals("2.5 MB", NumberUtils.formatBytes(2_621_440))
        assertEquals("1.5 KB", NumberUtils.formatBytes(1_536))
        assertEquals("512 B", NumberUtils.formatBytes(512))
    }

    @Test
    fun `counts use a decimal point even in a decimal-comma locale`() {
        assertEquals("1.2k", NumberUtils.formatCount(1_234))
        assertEquals("3.5m", NumberUtils.formatCount(3_500_000))
        assertEquals("999", NumberUtils.formatCount(999))
    }
}
