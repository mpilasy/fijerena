package org.njarasoa.fijerena.core.network.xmltv

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
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A changed guide offset moves the times already in the guide index ([EpgOffsetShift]), and an
 * import records the offset written in the file. The search index keeps finding the programmes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgOffsetShiftTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.now(ZoneOffset.UTC).plusDays(1)
    private lateinit var index: EpgIndexDatabase
    private lateinit var settings: SettingsDatabase

    // As EpgIndexerDirectIngestTest: the same schemas in memory on the framework's SQLite.
    @Before
    fun setup() {
        index = Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).allowMainThreadQueries().build()
        settings = Room.inMemoryDatabaseBuilder(context, SettingsDatabase::class.java).allowMainThreadQueries().build()
        mockkObject(EpgIndexDatabase.Companion)
        every { EpgIndexDatabase.getInstance(any()) } returns index
    }

    @After
    fun tearDown() {
        unmockkAll()
        index.close()
        settings.close()
    }

    private fun at(
        hhmm: String,
        zone: String,
    ): String = day.format(DateTimeFormatter.BASIC_ISO_DATE) + hhmm + "00" + if (zone.isEmpty()) "" else " $zone"

    private fun epoch(hhmm: String): Long = day.atTime(hhmm.take(2).toInt(), hhmm.drop(2).toInt()).toEpochSecond(ZoneOffset.UTC)

    private fun guide(vararg programmes: Triple<String, String, String>): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><tv><channel id="ARS.uk"><display-name>Arsenal TV</display-name></channel>""")
            for ((start, title, zone) in programmes) {
                val stop = "%02d%s".format(start.take(2).toInt() + 1, start.drop(2))
                append(
                    """<programme start="${at(start, zone)}" stop="${at(stop, zone)}" channel="ARS.uk"><title>$title</title></programme>""",
                )
            }
            append("</tv>")
        }

    /** Imports [xml] for a new source with [offsetHours], recorded as an import would. */
    private suspend fun imported(
        xml: String,
        offsetHours: Int = 0,
    ): Long {
        val dao = settings.epgSourceDao()
        val id = dao.insertSource(EpgSourceEntity(url = "http://epg.test/g.xml", timezoneOffsetHours = offsetHours, providerId = 1))
        val stats =
            EpgIndexer
                .getInstance(
                    context,
                ).ingestFromStream(xml.byteInputStream(), sourceId = id, timezoneOverrideHours = offsetHours)
        dao.markIngested(
            id = id,
            timestamp = 1,
            channels = stats.channelsIngested,
            programmes = stats.programmesIngested,
            downloadBytes = 0,
            etag = "\"v1\"",
            contentSha256 = "abc",
            fileUtcOffsetMinutes = stats.fileUtcOffsetMinutes,
            appliedUtcOffsetMinutes = EpgOffsetShift.readMinutes(offsetHours, stats.fileUtcOffsetMinutes),
        )
        return id
    }

    private suspend fun setOffset(
        id: Long,
        hours: Int,
    ): EpgOffsetShift.Result {
        val dao = settings.epgSourceDao()
        dao.updateSource(dao.getSourceById(id)!!.copy(timezoneOffsetHours = hours))
        return EpgOffsetShift.reconcile(id, dao, EpgIndexer.getInstance(context))
    }

    private fun starts(id: Long): List<Long> =
        index.openHelper.readableDatabase
            .query("SELECT start_epoch FROM epg_programme WHERE source_id = ? ORDER BY start_epoch", arrayOf<Any>(id))
            .use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList() }

    private fun count(sql: String): Int =
        index.openHelper.readableDatabase.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `an offset of 8 on a +0000 file moves 16 30 to 08 30 UTC at once`() =
        runBlocking {
            val id = imported(guide(Triple("1630", "Arsenal v Chelsea", "+0000")))

            assertEquals(EpgOffsetShift.Result.SHIFTED, setOffset(id, 8))

            assertEquals(listOf(epoch("0830")), starts(id))
            assertEquals(480, settings.epgSourceDao().getSourceById(id)!!.appliedUtcOffsetMinutes)
            // Back to 0: the file's own offset again.
            assertEquals(EpgOffsetShift.Result.SHIFTED, setOffset(id, 0))
            assertEquals(listOf(epoch("1630")), starts(id))
        }

    @Test
    fun `back-to-back programmes on a channel move without meeting each other's start`() =
        runBlocking {
            val id = imported(guide(Triple("1600", "First", "+0000"), Triple("1700", "Second", "+0000")))

            setOffset(id, 1)

            assertEquals(listOf(epoch("1500"), epoch("1600")), starts(id))
        }

    @Test
    fun `search still finds a moved programme, and the search index triggers are back`() =
        runBlocking {
            val id = imported(guide(Triple("1630", "Arsenal v Chelsea", "+0000")))

            setOffset(id, 8)

            assertEquals(1, count("SELECT COUNT(*) FROM epg_programme_fts WHERE epg_programme_fts MATCH 'chelsea'"))
            assertEquals(
                4,
                count(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'room_fts_content_sync_epg_programme_fts_%'",
                ),
            )
        }

    @Test
    fun `a file in more than one offset is imported again, not moved`() =
        runBlocking {
            val id = imported(guide(Triple("1600", "Before the change", "+0100"), Triple("1800", "After", "+0200")))
            val before = starts(id)
            assertNull(settings.epgSourceDao().getSourceById(id)!!.fileUtcOffsetMinutes)

            assertEquals(EpgOffsetShift.Result.NEEDS_IMPORT, setOffset(id, 3))

            assertEquals(before, starts(id))
            val source = settings.epgSourceDao().getSourceById(id)!!
            assertNull(source.etag)
            assertNull(source.lastContentSha256)
        }

    @Test
    fun `a source with nothing in the index yet is left alone`() =
        runBlocking {
            val dao = settings.epgSourceDao()
            val id = dao.insertSource(EpgSourceEntity(url = "http://epg.test/new.xml", providerId = 1))

            assertEquals(EpgOffsetShift.Result.IN_STEP, setOffset(id, 8))
        }

    @Test
    fun `an import records the file's offset`() =
        runBlocking {
            val dao = settings.epgSourceDao()
            assertEquals(120, dao.getSourceById(imported(guide(Triple("1600", "A", "+0200"))))!!.fileUtcOffsetMinutes)
            assertEquals(0, dao.getSourceById(imported(guide(Triple("1600", "A", ""))))!!.fileUtcOffsetMinutes)
            assertEquals(-330, dao.getSourceById(imported(guide(Triple("1600", "A", "-05:30"))))!!.fileUtcOffsetMinutes)
        }

    @Test
    fun `zone parts read as minutes`() {
        assertEquals(480, XmltvParser.zoneMinutes("+0800"))
        assertEquals(-330, XmltvParser.zoneMinutes("-05:30"))
        assertEquals(0, XmltvParser.zoneMinutes(""))
        assertNull(XmltvParser.zoneMinutes("EST"))
    }
}
