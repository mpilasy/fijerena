package org.njarasoa.fijerena.core.network.xtream.manager

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

/** The panel guide's overlapping programmes end at the next start. See docs/plans/20261012_guide-overlaps-plan.md. */
class GuideOverlapsTest {
    private fun program(
        title: String,
        start: Long,
        end: Long,
    ) = EpgProgram(id = title, title = title, start = "$start", end = "$end")

    private fun ends(epg: EpgResponse) = epg.withOverlapsClipped().listings.associate { it.title to it.endTime }

    @Test
    fun `a stretched live entry ends where the next programme starts`() {
        // beIN Sports 6 (UTC): Eagles 16:30 until the next day 20:00, Italy vs Türkiye 19:30-20:00, Vikings 20:00-23:25.
        val epg =
            EpgResponse(
                listOf(
                    program("Eagles", 1630, 4400),
                    program("Italy", 1930, 2000),
                    program("Vikings", 2000, 2325),
                ),
            )
        assertEquals(mapOf("Eagles" to 1930L, "Italy" to 2000L, "Vikings" to 2325L), ends(epg))
    }

    @Test
    fun `programmes sharing a start both end at the next later one`() {
        val epg = EpgResponse(listOf(program("A", 100, 500), program("B", 100, 400), program("C", 300, 600)))
        assertEquals(mapOf("A" to 300L, "B" to 300L, "C" to 600L), ends(epg))
    }

    @Test
    fun `a schedule without overlaps is left alone`() {
        val epg = EpgResponse(listOf(program("A", 100, 200), program("B", 200, 300), program("C", 400, 900)))
        assertEquals(mapOf("A" to 200L, "B" to 300L, "C" to 900L), ends(epg))
    }
}
