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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ingest ends a programme at the next start on its channel ([EpgIndexer.clipOverlaps]): panel-a lists
 * "Eagles @ Jaguars" on beIN Sports 6 from 18:30 to the next day's 22:00 over its normal schedule.
 * See the guide overlaps plan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerClipOverlapsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: EpgIndexDatabase

    // Same in-memory stand-in as EpgIndexerDirectIngestTest: requery's native SQLite doesn't load here.
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

    // Days ahead, so nothing is in the past; the times only matter relative to each other.
    private val guide =
        """
        <?xml version="1.0" encoding="UTF-8"?><tv>
        <channel id="beINSports6.qa"><display-name>beIN 6</display-name></channel>
        <channel id="Other.qa"><display-name>Other</display-name></channel>
        <programme start="20301011183000 +0200" stop="20301012220000 +0200" channel="beINSports6.qa"><title>Eagles</title></programme>
        <programme start="20301011213000 +0200" stop="20301011220000 +0200" channel="beINSports6.qa"><title>Italy</title></programme>
        <programme start="20301011220000 +0200" stop="20301012012500 +0200" channel="beINSports6.qa"><title>Vikings</title></programme>
        <programme start="20301011183000 +0200" stop="20301012220000 +0200" channel="Other.qa"><title>Night</title></programme>
        </tv>
        """.trimIndent()

    private suspend fun ends(): Map<String, Long> =
        db
            .epgIndexDao()
            .getProgrammesInWindow(listOf("beINSports6.qa", "Other.qa"), listOf(1L), 0, Long.MAX_VALUE)
            .associate { it.title to it.endEpoch }

    private val sec = { s: String ->
        java.time.OffsetDateTime
            .parse(s)
            .toEpochSecond()
    }

    private val expected =
        mapOf(
            "Eagles" to sec("2030-10-11T21:30+02:00"),
            "Italy" to sec("2030-10-11T22:00+02:00"),
            "Vikings" to sec("2030-10-12T01:25+02:00"),
            // Alone on its channel: nothing to end it, so it keeps its own end.
            "Night" to sec("2030-10-12T22:00+02:00"),
        )

    @Test
    fun `the direct path ends overlapping programmes at the next start`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(guide.byteInputStream(), sourceId = 1, useStaging = false)
            assertEquals(1, indexer.clipOverlaps(1, useStaging = false))
            assertEquals(expected, ends())
        }

    @Test
    fun `the staging path does it before the swap`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(guide.byteInputStream(), sourceId = 1, useStaging = true)
            assertEquals(1, indexer.clipOverlaps(1, useStaging = true))
            db.epgIndexDao().executeSwap(listOf(1L))
            assertEquals(expected, ends())
        }
}
