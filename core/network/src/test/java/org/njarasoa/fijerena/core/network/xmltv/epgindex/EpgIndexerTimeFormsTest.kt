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
import org.njarasoa.fijerena.core.network.fixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * [EpgIndexer.ingestFromStream] on the hand-written `xmltv/var-xmltv-*` files: the same guide in
 * each time form of the XMLTV DTD and of field reports (14 digits, 12 digits, no space before the
 * zone, no zone) gives the same rows and the zone of the file. A guide without `stop` times ends
 * each programme at the next one on its channel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgIndexerTimeFormsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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

    private fun epoch(utc: String) = Instant.parse(utc).epochSecond

    // In the order the guide reads them: by channel id, then by start.
    private val expectedRows =
        listOf(
            Triple("Music", epoch("2026-11-11T18:00:00Z"), epoch("2026-11-11T19:00:00Z")),
            Triple("News", epoch("2026-11-11T18:00:00Z"), epoch("2026-11-11T19:00:00Z")),
            Triple("Film", epoch("2026-11-11T19:00:00Z"), epoch("2026-11-11T20:30:00Z")),
        )

    private suspend fun rows(channels: List<String>): List<Triple<String, Long, Long>> =
        db
            .epgIndexDao()
            .getProgrammesInWindow(channels, listOf(SOURCE), epoch("2026-11-11T00:00:00Z"), epoch("2026-11-13T00:00:00Z"))
            .map { Triple(it.title, it.startEpoch, it.endEpoch) }

    /** Ingests [name], then checks the counts, the rows and the zone the file's times carry. */
    private fun assertIngests(
        name: String,
        zoneMinutes: Int,
    ) = runBlocking {
        val stats = EpgIndexer.getInstance(context).ingestFromStream(fixture("xmltv/$name").byteInputStream(), sourceId = SOURCE)

        assertEquals(2, stats.channelsIngested)
        assertEquals(3, stats.programmesIngested)
        assertEquals(zoneMinutes, stats.fileUtcOffsetMinutes)
        assertEquals(expectedRows, rows(listOf("news.example", "music.example")))
    }

    @Test
    fun `14 digits with a space before the zone`() = assertIngests("var-xmltv-time-14-digits.xml", zoneMinutes = 0)

    @Test
    fun `12 digits without seconds`() = assertIngests("var-xmltv-time-12-digits.xml", zoneMinutes = 180)

    @Test
    fun `no space before the zone`() = assertIngests("var-xmltv-time-no-space.xml", zoneMinutes = 180)

    @Test
    fun `no zone is UTC`() = assertIngests("var-xmltv-time-no-zone.xml", zoneMinutes = 0)

    @Test
    fun `programmes without stop end at the next start on their channel`() =
        runBlocking {
            val indexer = EpgIndexer.getInstance(context)
            indexer.ingestFromStream(fixture("xmltv/var-xmltv-structure.xml").byteInputStream(), sourceId = SOURCE)
            indexer.clipOverlaps(SOURCE, useStaging = false)

            assertEquals(
                listOf(
                    Triple("Morning Show", epoch("2026-11-11T18:00:00Z"), epoch("2026-11-11T18:30:00Z")),
                    Triple("Weather", epoch("2026-11-11T18:30:00Z"), epoch("2026-11-11T19:00:00Z")),
                    Triple("Late Film", epoch("2026-11-11T19:00:00Z"), epoch("2026-11-11T23:00:00Z")),
                ),
                rows(listOf("news.example")),
            )
        }

    private companion object {
        const val SOURCE = 7L
    }
}
