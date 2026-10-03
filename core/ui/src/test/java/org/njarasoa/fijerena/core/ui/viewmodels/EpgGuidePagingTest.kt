package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.GuideSource
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.model.EpgProgram
import java.time.LocalDate

/** GD4: the guide's pages — which to load, the per-(day, page) cache, the rows built from them. */
class EpgGuidePagingTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun channel(i: Int) = MediaItem(id = "ch$i", name = "Channel $i", mediaType = MediaType.LIVE_CHANNEL, categoryId = "cat")

    private fun program(
        channelId: String,
        start: Long,
        end: Long,
    ) = EpgProgram(id = "${channelId}_$start", title = "P $start", start = start.toString(), end = end.toString())

    private fun page(
        listings: Map<String, List<EpgProgram>>,
        source: GuideSource = GuideSource.XMLTV,
        updatedAtMs: Long? = 1L,
        latestEndSec: Long? = listings.values.flatten().maxOfOrNull { it.endTime },
    ) = GuidePage(listings, source, updatedAtMs, latestEndSec)

    // --- pagesToLoad: the paging trigger ---

    @Test
    fun rowsInsideAPageLoadOnlyThatPage() {
        assertEquals(listOf(0), pagesToLoad(first = 0, last = 7, rowCount = 300, pageSize = 30))
        assertEquals(listOf(1), pagesToLoad(first = 30, last = 37, rowCount = 300, pageSize = 30))
    }

    @Test
    fun theNextPageIsFetchedOnceTheRowsComeWithinFiveRowsOfIt() {
        // Row 24 + 5 = 29: still page 0. Row 25 + 5 = 30: the next page is prefetched.
        assertEquals(listOf(0), pagesToLoad(first = 17, last = 24, rowCount = 300, pageSize = 30))
        assertEquals(listOf(0, 1), pagesToLoad(first = 18, last = 25, rowCount = 300, pageSize = 30))
    }

    @Test
    fun rowsAcrossAPageBoundaryLoadBothPages() {
        assertEquals(listOf(0, 1), pagesToLoad(first = 26, last = 33, rowCount = 300, pageSize = 30))
    }

    @Test
    fun theLastPageHasNothingToPrefetch() {
        assertEquals(listOf(9), pagesToLoad(first = 290, last = 299, rowCount = 300, pageSize = 30))
        assertEquals(listOf(2), pagesToLoad(first = 61, last = 64, rowCount = 65, pageSize = 30))
    }

    @Test
    fun noRowsNoPages() {
        assertEquals(emptyList<Int>(), pagesToLoad(first = 0, last = 0, rowCount = 0, pageSize = 30))
    }

    @Test
    fun rowsReportedPastTheEndAreClamped() {
        assertEquals(listOf(1), pagesToLoad(first = 80, last = 90, rowCount = 40, pageSize = 30))
    }

    // --- GuidePager: page boundaries and the cache ---

    @Test
    fun eachPageAsksForItsOwnSliceOfChannels() =
        runTest {
            val asked = mutableListOf<List<String>>()
            val pager = GuidePager(pageSize = 30) { _, items -> page(emptyMap()).also { asked += items.map { it.id } } }
            pager.channels = (0 until 65).map(::channel)

            assertEquals(3, pager.pageCount)
            pager.page(today, 1)
            pager.page(today, 2)

            assertEquals((30 until 60).map { "ch$it" }, asked[0])
            assertEquals((60 until 65).map { "ch$it" }, asked[1])
        }

    @Test
    fun aLoadedPageComesFromTheCache() =
        runTest {
            var loads = 0
            val pager = GuidePager(pageSize = 30) { _, _ -> page(emptyMap()).also { loads++ } }
            pager.channels = (0 until 65).map(::channel)

            val first = pager.page(today, 0)
            val again = pager.page(today, 0)

            assertEquals(1, loads)
            assertTrue(first === again)
            assertTrue(pager.isLoaded(today, 0))
            assertFalse(pager.isLoaded(today, 1))
        }

    @Test
    fun theCacheIsPerDay() =
        runTest {
            var loads = 0
            val pager = GuidePager(pageSize = 30) { _, _ -> page(emptyMap()).also { loads++ } }
            pager.channels = (0 until 10).map(::channel)

            pager.page(today, 0)
            pager.page(today.plusDays(1), 0)
            pager.page(today, 0)

            assertEquals(2, loads)
            assertEquals(1, pager.loadedPages(today))
        }

    @Test
    fun aNewChannelListDropsTheCache() =
        runTest {
            var loads = 0
            val pager = GuidePager(pageSize = 30) { _, _ -> page(emptyMap()).also { loads++ } }
            pager.channels = (0 until 10).map(::channel)
            pager.page(today, 0)

            pager.channels = (0 until 12).map(::channel)
            pager.page(today, 0)

            assertEquals(2, loads)
        }

    @Test
    fun aFailedPageIsNotCachedAndIsAskedForAgain() =
        runTest {
            var loads = 0
            val pager =
                GuidePager(pageSize = 30) { _, _ ->
                    loads++
                    if (loads == 1) error("offline")
                    page(emptyMap())
                }
            pager.channels = (0 until 10).map(::channel)

            runCatching { pager.page(today, 0) }
            assertFalse(pager.isLoaded(today, 0))
            pager.page(today, 0)

            assertEquals(2, loads)
            assertTrue(pager.isLoaded(today, 0))
        }

    @Test
    fun theProbeFindsTheFirstPageWithListings() =
        runTest {
            val pager =
                GuidePager(pageSize = 2) { _, items ->
                    page(if (items.any { it.id == "ch5" }) mapOf("ch5" to listOf(program("ch5", 100, 200))) else emptyMap())
                }
            pager.channels = (0 until 8).map(::channel)

            pager.page(today, 0)
            assertFalse(pager.hasListings(today))
            assertEquals(1, pager.firstUnloaded(today))
            pager.page(today, 1)
            pager.page(today, 2)

            assertTrue(pager.hasListings(today))
            assertEquals(3, pager.firstUnloaded(today))
        }

    // --- assembleGuide: rows and counts from the loaded pages ---

    @Test
    fun rowsOfPagesNotLoadedAreEmptyPlaceholders() {
        val channels = (0 until 5).map(::channel)
        val pages = mapOf(0 to page(mapOf("ch0" to listOf(program("ch0", 100, 200)), "ch1" to emptyList())))

        val guide = assembleGuide(channels, pageSize = 2, pages = pages)

        assertEquals(5, guide.rows.size)
        assertEquals(listOf("ch0", "ch1", "ch2", "ch3", "ch4"), guide.rows.map { it.channel.id })
        assertEquals(1, guide.rows[0].programs.size)
        assertTrue(guide.rows.drop(1).all { it.programs.isEmpty() })
        assertEquals(1, guide.listedCount)
        assertEquals(2, guide.loadedCount)
        assertEquals(2, guide.answered)
    }

    @Test
    fun theStatusSourceIsThePageThatHasListings() {
        val channels = (0 until 4).map(::channel)
        val pages =
            mapOf(
                0 to page(emptyMap(), source = GuideSource.NATIVE, updatedAtMs = null, latestEndSec = null),
                1 to page(mapOf("ch2" to listOf(program("ch2", 100, 300))), updatedAtMs = 42L),
            )

        val guide = assembleGuide(channels, pageSize = 2, pages = pages)

        assertEquals(GuideSource.XMLTV, guide.source)
        assertEquals(42L, guide.updatedAtMs)
        assertEquals(300L, guide.lastListingEndSec)
        assertEquals(4, guide.loadedCount)
    }

    @Test
    fun listingsOutsideTheDayStillCountAsData() {
        // The index knows these channels but has nothing on this day: STALE, not "no data".
        val guide = assembleGuide(listOf(channel(0)), pageSize = 30, pages = mapOf(0 to page(emptyMap(), latestEndSec = 50L)))

        assertEquals(0, guide.listedCount)
        assertTrue(guide.hasAnyListing)
        assertNull(guide.lastListingEndSec)
    }

    @Test
    fun nothingLoadedNothingKnown() {
        val guide = assembleGuide((0 until 3).map(::channel), pageSize = 30, pages = emptyMap())

        assertEquals(0, guide.loadedCount)
        assertNull(guide.source)
        assertFalse(guide.hasAnyListing)
    }

    // --- "Listings end at …" ---

    @Test
    fun listingsEndedWhenNowIsPastTheLastListingOfToday() {
        val dayStart = 1_000_000L
        val dayEnd = dayStart + 86_400
        val lastEnd = dayStart + 8 * 3600 // 8 AM

        assertTrue(guideListingsEnded(lastEnd, nowSec = dayStart + 15 * 3600, dayStartSec = dayStart, dayEndSec = dayEnd))
        assertTrue(guideListingsEnded(lastEnd, nowSec = lastEnd, dayStartSec = dayStart, dayEndSec = dayEnd))
    }

    @Test
    fun listingsNotEndedBeforeTheirEndOrOnAnotherDay() {
        val dayStart = 1_000_000L
        val dayEnd = dayStart + 86_400
        val lastEnd = dayStart + 8 * 3600

        assertFalse(guideListingsEnded(lastEnd, nowSec = lastEnd - 1, dayStartSec = dayStart, dayEndSec = dayEnd))
        // Tomorrow's grid, viewed today: "now" is not on that day.
        assertFalse(guideListingsEnded(lastEnd + 86_400, nowSec = dayStart + 15 * 3600, dayStartSec = dayEnd, dayEndSec = dayEnd + 86_400))
        // Yesterday's grid: now is past its end, but not on it.
        assertFalse(guideListingsEnded(lastEnd, nowSec = dayEnd + 10, dayStartSec = dayStart, dayEndSec = dayEnd))
        assertFalse(guideListingsEnded(null, nowSec = dayStart + 15 * 3600, dayStartSec = dayStart, dayEndSec = dayEnd))
    }
}
