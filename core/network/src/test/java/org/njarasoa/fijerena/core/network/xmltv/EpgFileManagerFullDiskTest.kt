package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.FullDiskGuide
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A guide ingest that fills the disk part of the way through (a real SQLITE_FULL from
 * `max_page_count`): the live guide keeps its rows, the source keeps a storage-low error and the
 * validators of its last good ingest, and staging is empty at once (the low storage plan, Phase 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgFileManagerFullDiskTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = EpgSourceEntity(id = 1, url = "http://example.invalid/guide.xml", etag = "\"v1\"", lastChannels = 1)
    private val sourceDao = mockk<EpgSourceDao>(relaxed = true)
    private lateinit var db: EpgIndexDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).allowMainThreadQueries().build()
        mockkObject(EpgIndexDatabase.Companion)
        every { EpgIndexDatabase.getInstance(any()) } returns db
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    /** Puts the old guide (3 programmes) live, writes the new one to a file, then fills the disk. */
    private suspend fun fullDiskIngest(
        newChannel: String,
        useStaging: Boolean,
    ): Pair<EpgFileManager.IngestOutcome, File> {
        val indexer = EpgIndexer.getInstance(context)
        indexer.ingestFromStream(FullDiskGuide.xml("ch.1", "Old", 3).byteInputStream(), sourceId = source.id)
        val file = File(context.cacheDir, "full_disk_guide.xml").apply { writeText(FullDiskGuide.xml(newChannel, "New", 5000)) }
        FullDiskGuide.capPages(db, extraPages = 20)
        val downloaded = EpgFileManager.DownloadedSource(source, "Guide", file, downloadedBytes = file.length())
        val outcome =
            EpgFileManager
                .getInstance(context)
                .ingestDownloadedSource(downloaded, sourceDao, indexer, ConcurrentHashMap(), batchSize = 500, useStaging = useStaging) {}
        return outcome to file
    }

    private fun liveTitles(channelId: String): List<String> {
        val sql = "SELECT title FROM epg_programme WHERE channel_id = '$channelId' AND source_id = ${source.id} ORDER BY start_epoch"
        return db.openHelper.writableDatabase.query(sql).use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() }?.getString(0) }.toList()
        }
    }

    private fun assertStorageLowAndNotIngested(outcome: EpgFileManager.IngestOutcome) {
        assertEquals(outcome.stats.error, ErrorKind.STORAGE_LOW, StoredError.decode(outcome.stats.error!!).kind)
        assertNull(outcome.pending)
        coVerify { sourceDao.markError(source.id, match { StoredError.decode(it).kind == ErrorKind.STORAGE_LOW }, any()) }
        // Only markIngested writes the validators: they stay those of the last good ingest, so
        // the next refresh downloads and ingests the source again.
        coVerify(exactly = 0) {
            sourceDao.markIngested(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a staged ingest that fills the disk keeps the live guide and empties staging at once`() =
        runBlocking {
            val (outcome, file) = fullDiskIngest("ch.1", useStaging = true)

            assertStorageLowAndNotIngested(outcome)
            assertEquals(listOf("Old 0", "Old 1", "Old 2"), liveTitles("ch.1"))
            assertEquals(0, FullDiskGuide.count(db, "SELECT COUNT(*) FROM epg_programme_staging"))
            assertEquals(0, FullDiskGuide.count(db, "SELECT COUNT(*) FROM epg_channel_staging"))
            assertFalse(file.exists())
        }

    @Test
    fun `a direct ingest that fills the disk keeps the old rows and the old validators`() =
        runBlocking {
            val (outcome, file) = fullDiskIngest("ch.2", useStaging = false)

            assertStorageLowAndNotIngested(outcome)
            // The old rows that the new guide did not rewrite stay: the rows not rewritten are
            // deleted only after a full ingest.
            assertEquals(listOf("Old 0", "Old 1", "Old 2"), liveTitles("ch.1"))
            assertFalse(file.exists())
        }
}
