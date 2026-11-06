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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Clear guide data: [EpgIndexer.clearSource] deletes one guide source's channels and programmes
 * and keeps the others; `EpgSourceDao.forgetIngest` makes its next refresh import it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerClearSourceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var index: EpgIndexDatabase
    private lateinit var settings: SettingsDatabase

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

    private val guide =
        """<?xml version="1.0" encoding="UTF-8"?><tv><channel id="a.uk"><display-name>A</display-name></channel>""" +
            """<programme start="20990101100000 +0000" stop="20990101110000 +0000" channel="a.uk"><title>T</title></programme>""" +
            """<programme start="20990101110000 +0000" stop="20990101120000 +0000" channel="a.uk"><title>U</title></programme></tv>"""

    private suspend fun indexed(sourceId: Long) =
        EpgIndexer.getInstance(context).ingestFromStream(guide.byteInputStream(), sourceId = sourceId)

    @Test
    fun `the cleared guide source's rows go, the others stay`() =
        runBlocking {
            indexed(1)
            indexed(2)
            index.epgIndexDao().insertMetadata(
                EpgIndexMetadata(
                    fileSizeBytes = 0,
                    fileLastModifiedMs = 0,
                    indexedAtMs = 0,
                    channelCount = 2,
                    programmeCount = 4,
                    timezoneOffsetHours = 0,
                ),
            )

            val progress = mutableListOf<EpgIndexer.ClearProgress>()
            EpgIndexer.getInstance(context).clearSource(1) { progress += it }

            assertEquals(listOf(2L), index.epgIndexDao().getIndexedSourceIds())
            assertEquals(2, index.epgIndexDao().getProgrammeCount())
            assertEquals(1, index.epgIndexDao().getChannelCount())
            assertEquals(2, index.epgIndexDao().getMetadata()?.programmeCount)
            // The card counts up to the source's programmes, then frees the space.
            assertEquals(EpgIndexer.ClearProgress.Deleting(0, 2), progress.first())
            assertEquals(EpgIndexer.ClearProgress.Deleting(2, 2), progress[progress.size - 2])
            assertEquals(EpgIndexer.ClearProgress.FreeingSpace, progress.last())
        }

    @Test
    fun `a cleared guide source shows as never refreshed and is imported again in full`() =
        runBlocking {
            val dao = settings.epgSourceDao()
            val id = dao.insertSource(EpgSourceEntity(url = "http://epg.test/big.xml", enabled = false))
            dao.markIngested(id, timestamp = 1_000, channels = 1, programmes = 2, downloadBytes = 300, contentSha256 = "abc", etag = "e1")

            dao.forgetIngest(id)

            val source = dao.getSourceById(id)!!
            assertEquals(0L, source.lastIngestedAtMs)
            assertEquals(0, source.lastProgrammes)
            assertNull(source.etag)
            assertNull(source.lastContentSha256)
            assertEquals(false, source.enabled)
        }
}
