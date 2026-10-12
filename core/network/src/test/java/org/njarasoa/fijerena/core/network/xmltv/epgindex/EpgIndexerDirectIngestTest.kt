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
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A direct (non-staging) re-ingest of a guide whose schedule changed: REPLACE only overwrites a
 * programme with the same channel and start, so the old listings it no longer has must be deleted
 * by [EpgIndexer.deleteProgrammesNotRewritten] — or they sit beside the new ones and overlap in the
 * TV Guide (an MLB game put in at 19:00 left the evening's old 19:31 and 20:02 shows under it).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerDirectIngestTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.now(ZoneOffset.UTC).plusDays(1)
    private lateinit var db: EpgIndexDatabase

    // The app's database opens through requery's native SQLite, which Robolectric can't load;
    // the same schema in memory on the framework's SQLite instead.
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

    private fun at(hhmm: String): String = day.format(DateTimeFormatter.BASIC_ISO_DATE) + hhmm + "00 +0000"

    private fun epochAt(hhmm: String): Long = day.atTime(hhmm.take(2).toInt(), hhmm.drop(2).toInt()).toEpochSecond(ZoneOffset.UTC)

    private fun guide(vararg programmes: Triple<String, String, String>): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><tv><channel id="WTIC.us"><display-name>FOX 61</display-name></channel>""")
            for ((start, stop, title) in programmes) {
                append("""<programme start="${at(start)}" stop="${at(stop)}" channel="WTIC.us"><title>$title</title></programme>""")
            }
            append("</tv>")
        }

    private suspend fun ingest(
        indexer: EpgIndexer,
        sourceId: Long,
        xml: String,
    ) {
        val maxIdBefore = indexer.maxProgrammeId()
        indexer.ingestFromStream(xml.byteInputStream(), sourceId = sourceId, useStaging = false)
        indexer.deleteProgrammesNotRewritten(sourceId, maxIdBefore)
    }

    private suspend fun titles(sourceId: Long): List<String> =
        db
            .epgIndexDao()
            .getProgrammesInWindow(listOf("WTIC.us"), listOf(sourceId), epochAt("0000"), epochAt("2359"))
            .map { it.title }

    @Test
    fun `a direct re-ingest drops the listings the new guide no longer has, and leaves other sources alone`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            ingest(
                indexer,
                1,
                guide(
                    Triple("1900", "1931", "The Simpsons"),
                    Triple("1931", "2002", "Animal Control"),
                    Triple("2002", "2031", "Universal Basic Guys"),
                    Triple("2200", "2300", "News"),
                ),
            )
            ingest(indexer, 2, guide(Triple("1931", "2002", "Other source")))

            ingest(
                indexer,
                1,
                guide(
                    Triple("1900", "2200", "MLB Baseball"),
                    Triple("2200", "2300", "News"),
                ),
            )

            assertEquals(listOf("MLB Baseball", "News"), titles(1))
            assertEquals(listOf("Other source"), titles(2))
        }
}
