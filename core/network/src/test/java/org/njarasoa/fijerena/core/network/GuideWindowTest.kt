package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.player.model.EpgResponse

/**
 * GD4: a guide page is the day's window — `end > windowStart AND start < windowEnd`, the predicate
 * of `EpgIndexDao.getProgrammesInWindow` (checked against SQLite with the same rows) applied to the
 * source's own EPG — and "no guide" is decided per source.
 */
class GuideWindowTest {
    private val windowStart = 1_100_000L
    private val windowEnd = 1_186_400L

    private fun program(
        title: String,
        start: Long,
        end: Long,
    ) = EpgProgram(id = title, title = title, start = start.toString(), end = end.toString())

    @Test
    fun programmesStraddlingEitherEdgeAreInTheWindow() {
        val listings =
            listOf(
                program("ends_at_start", windowStart - 3600, windowStart),
                program("straddles_start", windowStart - 2000, windowStart + 1000),
                program("inside", windowStart + 20_000, windowStart + 21_000),
                program("straddles_end", windowEnd - 400, windowEnd + 3600),
                program("starts_at_end", windowEnd, windowEnd + 3600),
                program("covers_all", windowStart - 50_000, windowEnd + 50_000),
            )

        val titles = windowListings(listings, windowStart, windowEnd).map { it.title }

        assertEquals(listOf("straddles_start", "inside", "straddles_end", "covers_all"), titles)
    }

    @Test
    fun aDayWithoutListingsIsEmpty() {
        val yesterday = listOf(program("late_show", windowStart - 7200, windowStart - 3600))

        assertEquals(emptyList<EpgProgram>(), windowListings(yesterday, windowStart, windowEnd))
    }

    @Test
    fun aSourceWithItsOwnEpgHasAGuide() {
        assertTrue(hasGuide(supportsNativeEpg = true, enabledGuideSources = 0))
    }

    @Test
    fun aSourceWithoutNativeEpgNeedsAGuideSourceOfItsOwn() {
        // Another source's indexed guide does not count: only this source's enabled guide sources.
        assertFalse(hasGuide(supportsNativeEpg = false, enabledGuideSources = 0))
        assertTrue(hasGuide(supportsNativeEpg = false, enabledGuideSources = 1))
    }

    @Test
    fun aSourceNotLoadedYetIsNotDeclaredGuideless() {
        assertTrue(hasGuide(supportsNativeEpg = null, enabledGuideSources = 0))
    }

    @Test
    fun archivePastFillsTheHoursBeforeTheIndexFromTheSourcesGuide() {
        val index = mapOf("1" to EpgResponse(listOf(program("index_evening", windowStart + 60_000, windowStart + 63_600))))
        val native =
            mapOf(
                "1" to
                    EpgResponse(
                        listOf(
                            program("day_before", windowStart - 7_200, windowStart - 3_600),
                            program("morning", windowStart + 3_600, windowStart + 7_200),
                            program("native_evening", windowStart + 60_000, windowStart + 63_600),
                        ),
                    ),
                "2" to EpgResponse(listOf(program("only_native", windowStart + 100, windowStart + 3_700))),
            )

        val merged = archivePast(index, native, windowStart, windowEnd)

        assertEquals(listOf("morning", "index_evening"), merged.getValue("1").listings.map { it.title })
        assertEquals(listOf("only_native"), merged.getValue("2").listings.map { it.title })
    }

    @Test
    fun archivePastLeavesChannelsTheSourceSaysNothingAbout() {
        val index = mapOf("1" to EpgResponse(listOf(program("index", windowStart + 100, windowStart + 200))))
        assertEquals(index, archivePast(index, mapOf("2" to EpgResponse(emptyList())), windowStart, windowEnd))
    }
}
