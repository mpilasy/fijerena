package org.njarasoa.fijerena.core.ui.deviceinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.UiText

class CoreInfoTest {
    @Test
    fun `mode text drops trailing zeros from the refresh rate`() {
        assertEquals("3840×2160 @ 60 Hz", modeText(3840, 2160, 60f))
        assertEquals("1920×1080 @ 23.98 Hz", modeText(1920, 1080, 23.976f))
        assertEquals("1920×1080 @ 59.94 Hz", modeText(1920, 1080, 59.94f))
        assertEquals("1920×1080 @ 50 Hz", modeText(1920, 1080, 50.0001f))
    }

    @Test
    fun `hdr types are named once each, unknown ones by number`() {
        assertEquals(listOf("HDR10", "HLG", "Dolby Vision", "HDR10+", "HDR type 9"), hdrTypeNames(intArrayOf(2, 3, 1, 4, 2, 9)))
        assertTrue(hdrTypeNames(intArrayOf()).isEmpty())
    }

    @Test
    fun `database size includes its wal shm and journal files and skips orphans`() {
        val sizes =
            databaseSizes(
                mapOf(
                    "xtream_v2.db" to 1_000L,
                    "xtream_v2.db-wal" to 200L,
                    "xtream_v2.db-shm" to 30L,
                    "providers.db" to 50L,
                    "providers.db-journal" to 5L,
                    "gone.db-wal" to 7L,
                ),
            )
        assertEquals(mapOf("providers.db" to 55L, "xtream_v2.db" to 1_230L), sizes)
        assertEquals(listOf("providers.db", "xtream_v2.db"), sizes.keys.toList())
    }

    @Test
    fun `free of total formats both sizes`() {
        val text = freeOfTotal(1_073_741_824L, 3L * 1_073_741_824L) as UiText.StringResource
        assertEquals(R.string.device_info_free_of_format, text.resId)
        assertEquals(listOf("1.0 GB", "3.0 GB"), text.args.toList())
    }

    @Test
    fun `yes no maps null to missing`() {
        assertEquals(R.string.device_info_yes, (yesNo(true) as UiText.StringResource).resId)
        assertEquals(R.string.device_info_no, (yesNo(false) as UiText.StringResource).resId)
        assertEquals(R.string.device_info_missing, (yesNo(null) as UiText.StringResource).resId)
    }
}
