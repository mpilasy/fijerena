package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.ErrorKind
import org.njarasoa.fijerena.core.network.StoredError
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The guide index with too little free space for a write (the low storage plan, Phases 2 and 3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerFullDiskTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: EpgIndexDatabase

    // The same schema in memory on the framework's SQLite, as EpgIndexerDirectIngestTest does.
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

    @Test
    fun `a search index rebuild with not enough space leaves the indexed guide as it was`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(FullDiskGuide.xml("ch.1", "News", 2).byteInputStream(), sourceId = 1)
            indexer.rebuildFtsAndUpdateState()
            val indexed = indexer.state.value
            assertTrue(indexed is EpgIndexState.Indexed)

            mockkObject(EpgIndexer.Companion)
            every { EpgIndexer.ftsRebuildShortfall(any()) } returns StoredError(ErrorKind.STORAGE_LOW, raw = "Need 150 MB, 100 MB free")
            indexer.rebuildFtsAndUpdateState()

            assertSame(indexed, indexer.state.value)
            assertFalse(indexer.isFtsStale())
        }

    // Phase 3: a real SQLITE_FULL in the middle of the rebuild. SQLite rolls the rebuild back.
    @Test
    fun `a search index rebuild that fills the disk leaves the indexed guide as it was`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(FullDiskGuide.xml("ch.1", "News", 2).byteInputStream(), sourceId = 1)
            indexer.rebuildFtsAndUpdateState()
            val indexed = indexer.state.value
            assertTrue(indexed is EpgIndexState.Indexed)

            // A direct ingest: no search index entries until the rebuild at the end.
            indexer.beginBulkIngestion(useStaging = false)
            indexer.ingestFromStream(FullDiskGuide.xml("ch.2", "Long film title", 3000).byteInputStream(), sourceId = 2)
            FullDiskGuide.capPages(db, extraPages = 0)
            indexer.rebuildFtsAndUpdateState()

            assertSame(indexed, indexer.state.value)
            assertTrue(indexer.isFtsStale())
            assertEquals(0, FullDiskGuide.count(db, "SELECT COUNT(*) FROM epg_programme_fts WHERE epg_programme_fts MATCH 'film'"))
            assertEquals(3002, db.epgIndexDao().getProgrammeCount())
        }
}
