package org.njarasoa.fijerena.core.network.xmltv

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer

/**
 * G-11 (docs/plans/20261003_ux-overhaul-plan.md → III.H): a source must never read "ingested",
 * validators included, while the live guide holds none of its rows. Two halves: on the staging
 * path the stats are written only after the swap commits, and an empty index never lets a 304 or
 * a hash match skip the download.
 */
class EpgFileManagerChangeDetectionTest {
    private val source =
        EpgSourceEntity(
            id = 7,
            url = "http://example.invalid/guide.xml",
            providerId = 1,
            lastIngestedAtMs = System.currentTimeMillis(),
            lastContentSha256 = "abc",
            etag = "\"v1\"",
            lastModifiedHeader = "Wed, 01 Oct 2026 08:00:00 GMT",
        )

    private val record =
        EpgFileManager.IngestRecord(
            sourceId = source.id,
            timestamp = 1_000L,
            channels = 10,
            programmes = 200,
            downloadBytes = 4096,
            contentSha256 = "abc",
            etag = source.etag,
            lastModifiedHeader = source.lastModifiedHeader,
        )

    @Test
    fun swapFailureLeavesSourceUnmarked() =
        runTest {
            val indexer = mockk<EpgIndexer>()
            val dao = mockk<EpgSourceDao>(relaxed = true)
            coEvery { indexer.swapAndRebuildFts(any()) } throws IllegalStateException("disk full")

            try {
                EpgFileManager.swapThenMarkIngested(indexer, dao, listOf(source.id), mapOf(source.id to record))
                fail("expected the swap failure to propagate")
            } catch (e: IllegalStateException) {
                assertEquals("disk full", e.message)
            }

            coVerify(exactly = 0) {
                dao.markIngested(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun swapSuccessMarksSyncedSourcesAfterwards() =
        runTest {
            val indexer = mockk<EpgIndexer>()
            val dao = mockk<EpgSourceDao>(relaxed = true)
            // swapAndRebuildFts has an inferred Int return (its last expression is a Log call).
            coEvery { indexer.swapAndRebuildFts(any()) } returns 0

            EpgFileManager.swapThenMarkIngested(indexer, dao, listOf(source.id), mapOf(source.id to record))

            coVerifyOrder {
                indexer.swapAndRebuildFts(listOf(source.id))
                dao.markIngested(
                    id = source.id,
                    timestamp = 1_000L,
                    channels = 10,
                    programmes = 200,
                    downloadBytes = 4096,
                    ingestMethod = "DOWNLOADED",
                    ingestionDurationMs = 0,
                    downloadDurationMs = 0,
                    contentSha256 = "abc",
                    etag = source.etag,
                    lastModifiedHeader = source.lastModifiedHeader,
                )
            }
        }

    @Test
    fun sourceNotInSwapIsNotMarked() =
        runTest {
            val indexer = mockk<EpgIndexer>()
            val dao = mockk<EpgSourceDao>(relaxed = true)
            // swapAndRebuildFts has an inferred Int return (its last expression is a Log call).
            coEvery { indexer.swapAndRebuildFts(any()) } returns 0

            EpgFileManager.swapThenMarkIngested(indexer, dao, emptyList(), mapOf(source.id to record))

            coVerify(exactly = 0) {
                dao.markIngested(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun emptyIndexSendsNoValidators() {
        val request = EpgFileManager.buildDownloadRequest(source, indexHasRows = false)

        assertNull(request.header("If-None-Match"))
        assertNull(request.header("If-Modified-Since"))
        assertEquals(source.url, request.url.toString())
    }

    @Test
    fun populatedIndexSendsValidators() {
        val request = EpgFileManager.buildDownloadRequest(source, indexHasRows = true)

        assertEquals(source.etag, request.header("If-None-Match"))
        assertEquals(source.lastModifiedHeader, request.header("If-Modified-Since"))
    }

    @Test
    fun hashMatchSkipsOnlyWithRowsInIndex() {
        assertTrue(EpgFileManager.canSkipIngest(source, "abc", indexHasRows = true))
        assertFalse(EpgFileManager.canSkipIngest(source, "abc", indexHasRows = false))
        assertFalse(EpgFileManager.canSkipIngest(source, "other", indexHasRows = true))
        assertFalse(EpgFileManager.canSkipIngest(source, null, indexHasRows = true))
    }

    @Test
    fun hashMatchDoesNotSkipPastStalenessWindow() {
        val stale = source.copy(lastIngestedAtMs = System.currentTimeMillis() - 25 * 3600 * 1000L)

        assertFalse(EpgFileManager.canSkipIngest(stale, "abc", indexHasRows = true))
    }
}
