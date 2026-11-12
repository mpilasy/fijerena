package org.njarasoa.fijerena.core.network.xtream.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.isDiskFull
import org.njarasoa.fijerena.core.network.xmltv.epgindex.FullDiskGuide
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The catalogue with a disk that fills during a write (a real SQLITE_FULL from `max_page_count`):
 * a batch rolls back whole and is not tried again, and a compact that fails does not fail the sync
 * (the low storage plan, Phase 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogFullDiskTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(context, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = XtreamSyncTriggers.install(db)
                    },
                ).build()
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    private fun streams(ids: IntRange) =
        ids.map {
            XtreamStreamEntity(
                streamId = it,
                providerId = 1L,
                type = XtreamStreamEntity.TYPE_VOD,
                num = it,
                name = "Film number $it with a long name",
                streamType = "movie",
                categoryId = "10",
                description = "A description that takes some room. ".repeat(4),
            )
        }

    private fun streamCount() = FullDiskGuide.count(db, "SELECT COUNT(*) FROM xtream_streams")

    private fun segments() = FullDiskGuide.count(db, "SELECT COUNT(*) FROM xtream_streams_fts_segdir")

    @Test
    fun `a catalogue batch that fills the disk rolls back whole and is not tried again`() {
        db.streamDao().insertKeepingDetailCache(1L, XtreamStreamEntity.TYPE_VOD, streams(1..3))
        FullDiskGuide.capPages(db, extraPages = 5)

        try {
            db.streamDao().insertKeepingDetailCache(1L, XtreamStreamEntity.TYPE_VOD, streams(4..3000))
            fail("expected the batch to fill the disk")
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            assertTrue(e.isDiskFull())
            assertFalse(ProviderSyncRunner.isTransient(e))
        }
        assertEquals(3, streamCount())
    }

    @Test
    fun `a compact that fills the disk does not throw and leaves the index as it was`() =
        runBlocking {
            // Two transactions, two segments of the search index.
            db.streamDao().insertKeepingDetailCache(1L, XtreamStreamEntity.TYPE_VOD, streams(1..1500))
            db.streamDao().insertKeepingDetailCache(1L, XtreamStreamEntity.TYPE_VOD, streams(1501..3000))
            val before = segments()
            assertTrue(before > 1)
            FullDiskGuide.capPages(db, extraPages = 0)
            mockkObject(XtreamDatabase.Companion)
            every { XtreamDatabase.getInstance(any()) } returns db

            CatalogSearchIndex.compact(context)

            assertEquals(before, segments())
            assertEquals(3000, streamCount())
        }
}
