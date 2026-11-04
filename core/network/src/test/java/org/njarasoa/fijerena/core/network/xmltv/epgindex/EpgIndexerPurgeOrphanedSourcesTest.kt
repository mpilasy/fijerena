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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EpgIndexer.purgeOrphanedSources] deletes the guide rows of guide sources no longer configured
 * (the copies one guide source per URL merged), and nothing when no guide source is configured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerPurgeOrphanedSourcesTest {
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
            """<programme start="20990101100000 +0000" stop="20990101110000 +0000" channel="a.uk"><title>T</title></programme></tv>"""

    private suspend fun indexed(sourceId: Long) =
        EpgIndexer.getInstance(context).ingestFromStream(guide.byteInputStream(), sourceId = sourceId)

    private suspend fun sourceIds() = index.epgIndexDao().getIndexedSourceIds().sorted()

    @Test
    fun `the rows of a guide source no longer configured go, the others stay`() =
        runBlocking {
            val kept = settings.epgSourceDao().insertSource(EpgSourceEntity(url = "http://epg.test/kept.xml"))
            indexed(kept)
            indexed(kept + 100)

            EpgIndexer.getInstance(context).purgeOrphanedSources()

            assertEquals(listOf(kept), sourceIds())
        }

    @Test
    fun `nothing goes when no guide source is configured`() =
        runBlocking {
            indexed(5)

            EpgIndexer.getInstance(context).purgeOrphanedSources()

            assertEquals(listOf(5L), sourceIds())
        }
}
