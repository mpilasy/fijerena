package org.njarasoa.fijerena.core.network.xtream.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * bears' guide: Flamengo x Fluminense, 20:45 Amsterdam (18:45 UTC), comes from `get_simple_data_table`
 * as `start_timestamp` 1791751500 = 20:45 UTC, while its `xmltv.php` says `20261011204500 +0200`.
 * See docs/plans/archive/20261012_panel-guide-local-time-plan.md.
 */
class PanelGuideTimesTest {
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val labelled = 1_791_751_500L // 2026-10-11 20:45 "UTC"
    private val real = labelled - 2 * 3600 // 18:45 UTC

    /** [n] programmes on one channel, an hour apart from [firstStart]. */
    private fun schedule(
        firstStart: Long,
        n: Int = 30,
        channel: String = "Globo.pt",
    ) = (0 until n).map { PanelGuideTimes.listing(channel, "Show $it", firstStart + it * 3600L) }

    @Test
    fun `a panel start stamped as UTC reads back as the panel's wall time`() {
        assertEquals(real, PanelGuideTimes.realEpoch(labelled, amsterdam))
    }

    @Test
    fun `the correction follows the zone's own offset, so a winter date shifts by one hour`() {
        val winterLabelled = 1_796_155_200L // 2026-12-01 20:00 "UTC"
        assertEquals(winterLabelled - 3600, PanelGuideTimes.realEpoch(winterLabelled, amsterdam))
    }

    @Test
    fun `corrected shifts every start and end and keeps the rest`() {
        val epg =
            EpgResponse(
                listOf(
                    EpgProgram(id = "1", title = "Flamengo X Fluminense", start = "$labelled", end = "${labelled + 3600}", hasArchive = 0),
                ),
            )
        val program = PanelGuideTimes.corrected(epg, amsterdam).listings.single()
        assertEquals(real, program.startTime)
        assertEquals(real + 3600, program.endTime)
        assertEquals("Flamengo X Fluminense", program.title)
        assertEquals(0, program.hasArchive)
    }

    @Test
    fun `listings two hours late against the xmltv are local time labelled as UTC`() {
        val panel = schedule(labelled)
        val index = schedule(real).toSet()
        assertEquals(true, PanelGuideTimes.labelsLocalAsUtc(panel, index, amsterdam))
    }

    @Test
    fun `listings that already match the xmltv are fine`() {
        val panel = schedule(real)
        assertEquals(false, PanelGuideTimes.labelsLocalAsUtc(panel, panel.toSet(), amsterdam))
    }

    @Test
    fun `channel id and title case and spacing don't matter`() {
        val panel = (0 until 30).map { PanelGuideTimes.listing("GLOBO.PT", " Show $it ", labelled + it * 3600L) }
        assertTrue(PanelGuideTimes.labelsLocalAsUtc(panel, schedule(real).toSet(), amsterdam)!!)
    }

    @Test
    fun `too few matches decide nothing`() {
        val panel = schedule(labelled, n = PanelGuideTimes.MIN_MATCHES - 1)
        assertNull(PanelGuideTimes.labelsLocalAsUtc(panel, schedule(real, n = PanelGuideTimes.MIN_MATCHES - 1).toSet(), amsterdam))
    }

    @Test
    fun `a mix of both readings decides nothing`() {
        val panel = schedule(labelled, n = 25) + schedule(real + 100 * 3600, n = 25, channel = "Rtp3.pt")
        val index = (schedule(real, n = 25) + schedule(real + 100 * 3600, n = 25, channel = "Rtp3.pt")).toSet()
        assertNull(PanelGuideTimes.labelsLocalAsUtc(panel, index, amsterdam))
    }

    @Test
    fun `a gap that isn't the panel's offset decides nothing`() {
        val panel = schedule(labelled)
        val index = schedule(labelled - 3600).toSet()
        assertNull(PanelGuideTimes.labelsLocalAsUtc(panel, index, amsterdam))
    }

    @Test
    fun `a panel at UTC can't tell the readings apart`() {
        val panel = schedule(real)
        assertNull(PanelGuideTimes.labelsLocalAsUtc(panel, panel.toSet(), ZoneOffset.UTC))
    }

    @Test
    fun `different programmes at the same times don't count`() {
        val panel = schedule(labelled)
        val index = (0 until 30).map { PanelGuideTimes.listing("Globo.pt", "Other $it", real + it * 3600L) }.toSet()
        assertFalse(PanelGuideTimes.labelsLocalAsUtc(panel, index, amsterdam) == true)
    }
}
