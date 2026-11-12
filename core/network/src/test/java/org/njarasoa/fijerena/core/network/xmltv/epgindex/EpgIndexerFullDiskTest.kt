package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The guide index with too little free space for a write (the low storage plan, Phases 2 and 3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerFullDiskTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
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

    private fun guide(vararg titles: String): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><tv><channel id="ch.1"><display-name>One</display-name></channel>""")
            titles.forEachIndexed { i, title ->
                val start = "%02d00".format(i)
                val stop = "%02d00".format(i + 1)
                append("""<programme start="$day${start}00 +0000" stop="$day${stop}00 +0000" channel="ch.1">""")
                append("""<title>$title</title></programme>""")
            }
            append("</tv>")
        }

    @Test
    fun `a search index rebuild with not enough space leaves the indexed guide as it was`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(guide("News", "Film").byteInputStream(), sourceId = 1)
            indexer.rebuildFtsAndUpdateState()
            val indexed = indexer.state.value
            assertTrue(indexed is EpgIndexState.Indexed)

            mockkObject(EpgIndexer.Companion)
            every { EpgIndexer.ftsRebuildShortfall(any()) } returns StoredError(ErrorKind.STORAGE_LOW, raw = "Need 150 MB, 100 MB free")
            indexer.rebuildFtsAndUpdateState()

            assertSame(indexed, indexer.state.value)
            assertFalse(indexer.isFtsStale())
        }
}
