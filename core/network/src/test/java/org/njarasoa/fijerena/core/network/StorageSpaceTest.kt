package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Storage is low below 5% free, at most 500 MB, as Android counts it for WorkManager. A job of a
 * known size starts only with its need and a 50 MB margin free (the low storage plan, Phase 2).
 */
class StorageSpaceTest {
    private val mb = 1024L * 1024

    @Test
    fun `small storage is low below 5 percent free`() {
        // A 4.3 GB Chromecast HD: low below about 220 MB.
        val total = 4400 * mb
        assertEquals(220 * mb, StorageSpace.lowThresholdBytes(total))
        assertTrue(StorageSpace.isLow(200 * mb, total))
        assertFalse(StorageSpace.isLow(900 * mb, total))
    }

    @Test
    fun `large storage is low below 500 MB free`() {
        val total = 64_000 * mb
        assertEquals(500 * mb, StorageSpace.lowThresholdBytes(total))
        assertTrue(StorageSpace.isLow(499 * mb, total))
        assertFalse(StorageSpace.isLow(600 * mb, total))
    }

    @Test
    fun `the storage error reads back as itself`() {
        val error = StoredError(ErrorKind.STORAGE_LOW, catalogue = true, raw = StorageSpace.rawText(150 * mb))
        assertEquals(error, StoredError.decode(error.encode()))
        assertEquals("Storage low: 150 MB free", error.raw)
    }

    @Test
    fun `a job starts only with its need and the margin free`() {
        val need = 400 * mb
        assertNull(StorageSpace.shortfall(need, FreeSpace { 450 * mb }))
        assertNull(StorageSpace.shortfall(need, FreeSpace { 2000 * mb }))

        val short = StorageSpace.shortfall(need, FreeSpace { 450 * mb - 1 })
        assertEquals(ErrorKind.STORAGE_LOW, short?.kind)
        assertFalse(short!!.catalogue)
        assertEquals("Need 450 MB, 449 MB free", short.raw)
    }

    @Test
    fun `a job with no need still keeps the margin free`() {
        assertNull(StorageSpace.shortfall(0, FreeSpace { 50 * mb }))
        assertEquals("Need 50 MB, 10 MB free", StorageSpace.shortfall(0, FreeSpace { 10 * mb })?.raw)
        assertEquals("Need 50 MB, 10 MB free", StorageSpace.shortfall(-1, FreeSpace { 10 * mb })?.raw)
    }

    @Test
    fun `each writer needs the size the plan gives`() {
        // XMLTV download: Content-Length, else the last download, else 50 MB.
        assertEquals(120 * mb, SpaceNeed.xmltvDownload(120 * mb, 80 * mb))
        assertEquals(80 * mb, SpaceNeed.xmltvDownload(-1, 80 * mb))
        assertEquals(50 * mb, SpaceNeed.xmltvDownload(-1, 0))
        // Guide ingest: staged 1.5 × epg_index.db; direct the uncompressed file, 10 × a .gz file.
        assertEquals(300 * mb, SpaceNeed.stagedIngest(200 * mb))
        assertEquals(30 * mb, SpaceNeed.directIngest(30 * mb, gzip = false))
        assertEquals(300 * mb, SpaceNeed.directIngest(30 * mb, gzip = true))
        // Search index rebuild: 0.5 × epg_index.db.
        assertEquals(100 * mb, SpaceNeed.ftsRebuild(200 * mb))
        // Catalogue sync: 25% of xtream_v2.db, at least 100 MB.
        assertEquals(310 * mb, SpaceNeed.catalogueSync(1240 * mb))
        assertEquals(100 * mb, SpaceNeed.catalogueSync(40 * mb))
        // Remote M3U download: 2 × Content-Length; nothing more without one.
        assertEquals(20 * mb, SpaceNeed.remoteM3uDownload(10 * mb))
        assertEquals(0L, SpaceNeed.remoteM3uDownload(-1))
    }
}
