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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The incremental guide index (docs/plans/20261105_pausable-guide-indexing-plan.md): after a
 * refresh of one guide source, the search index finds that source's new programmes and no longer
 * its old ones, the other source's stay found, and SQLite's own check of the index passes. A run
 * killed after its swap continues where it stopped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IncrementalFtsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var index: EpgIndexDatabase
    private lateinit var settings: SettingsDatabase
    private val indexer get() = EpgIndexer.getInstance(context)
    private val sdb get() = index.openHelper.writableDatabase

    @Before
    fun setup() {
        index = Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).allowMainThreadQueries().build()
        settings = Room.inMemoryDatabaseBuilder(context, SettingsDatabase::class.java).allowMainThreadQueries().build()
        mockkObject(EpgIndexDatabase.Companion, SettingsDatabase.Companion)
        every { EpgIndexDatabase.getInstance(any()) } returns index
        every { SettingsDatabase.getInstance(any()) } returns settings
    }

    @After
    fun tearDown() {
        unmockkAll()
        index.close()
        settings.close()
    }

    // One channel, one programme per title, an hour apart, far in the future.
    private fun guide(vararg titles: Pair<String, String>): String =
        """<?xml version="1.0" encoding="UTF-8"?><tv><channel id="a.uk"><display-name>A</display-name></channel>""" +
            titles
                .mapIndexed { i, (title, description) ->
                    val hour = (10 + i).toString().padStart(2, '0')
                    val end = (11 + i).toString().padStart(2, '0')
                    """<programme start="20990101${hour}0000 +0000" stop="20990101${end}0000 +0000" channel="a.uk">""" +
                        "<title>$title</title><desc>$description</desc></programme>"
                }.joinToString("") + "</tv>"

    private suspend fun live(
        sourceId: Long,
        vararg titles: Pair<String, String>,
    ) = indexer.ingestFromStream(guide(*titles).byteInputStream(), sourceId = sourceId)

    private suspend fun staged(
        sourceId: Long,
        vararg titles: Pair<String, String>,
    ) = indexer.ingestFromStream(guide(*titles).byteInputStream(), sourceId = sourceId, useStaging = true)

    private fun found(word: String): List<Long> =
        sdb
            .query(
                "SELECT p.source_id FROM epg_programme p JOIN epg_programme_fts f ON f.docid = p.id " +
                    "WHERE epg_programme_fts MATCH ? ORDER BY p.source_id",
                arrayOf(word),
            ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    // SQLite's own check: throws when the index and the programmes disagree.
    private fun integrityCheck() = sdb.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('integrity-check')")

    private val noPlayback = PlaybackPause(isPlaying = { false })

    @Test
    fun `a refresh indexes the refreshed source's new programmes and keeps the other source's`() =
        runBlocking {
            live(1, "Oldtitle" to "first guide")
            live(2, "Othersource" to "kept as is")
            indexer.rebuildFtsAndUpdateState()
            staged(1, "Newtitle" to "second guide", "Another" to "with a Wordinside description")
            indexer.beginBulkIngestion(useStaging = true)

            var swapped = false
            indexer.swapAndIndexIncrementally(listOf(1L), noPlayback, onWaiting = {}, onSwapped = { swapped = true })
            indexer.endBulkIngestion(useStaging = true)

            assertTrue(swapped)
            assertEquals(listOf(1L), found("Newtitle"))
            assertEquals(listOf(1L), found("Wordinside"))
            assertEquals(emptyList<Long>(), found("Oldtitle"))
            assertEquals(listOf(2L), found("Othersource"))
            integrityCheck()
            assertNull(IncrementalFts.read(sdb))
            assertEquals(emptySet<Long>(), indexer.pendingIndexSourceIds())
        }

    @Test
    fun `a run killed after its swap continues from where it stopped`() =
        runBlocking {
            live(1, "Oldtitle" to "first guide")
            indexer.rebuildFtsAndUpdateState()
            staged(1, "Newtitle" to "second guide")
            indexer.beginBulkIngestion(useStaging = true)
            // Remove and the swap as a run does them, then the process "dies" before Add.
            val cutoff = 0L
            var progress: FtsProgress? = FtsProgress(FtsProgress.Phase.REMOVE, listOf(1L), 0, cutoff, 0)
            IncrementalFts.write(sdb, progress!!)
            var last = progress
            while (progress != null) {
                last = progress
                progress = IncrementalFts.removeBatch(sdb, progress)
            }
            val boundary = index.epgIndexDao().getMaxProgrammeId()
            index.epgIndexDao().executeSwap(listOf(1L))
            IncrementalFts.write(sdb, last!!.copy(phase = FtsProgress.Phase.ADD, cursor = boundary, boundary = boundary))
            indexer.endBulkIngestion(useStaging = true)

            assertTrue(indexer.resumeIncrementalIndex(noPlayback))

            assertEquals(listOf(1L), found("Newtitle"))
            assertEquals(emptyList<Long>(), found("Oldtitle"))
            integrityCheck()
            assertNull(IncrementalFts.read(sdb))
        }

    @Test
    fun `nothing to resume without a run left unfinished`() =
        runBlocking {
            assertEquals(false, indexer.resumeIncrementalIndex(noPlayback))
        }

    @Test
    fun `a source still being indexed is searched in descriptions too`() =
        runBlocking {
            live(1, "Plain" to "the Hiddenword is here")
            val rows = index.epgIndexDao().searchByTextLike("hiddenword", listOf(1L), 0, Long.MAX_VALUE)
            assertEquals(listOf("Plain"), rows.map { it.title })
        }
}
