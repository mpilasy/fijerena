package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.fixture
import org.njarasoa.fijerena.core.network.provider.EpgSourceDao
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream

/**
 * A guide sent as gzip: at a URL that ends in `.gz`, and at one that does not (some Xtream panels
 * send `xmltv.php` compressed, with no `Content-Encoding`). Both ingest. The guide is
 * `xmltv/var-xmltv-time-14-digits.xml`, compressed here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpgFileManagerGzipTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sourceDao = mockk<EpgSourceDao>(relaxed = true)
    private lateinit var db: EpgIndexDatabase

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

    private fun ingest(
        url: String,
        gzip: Boolean,
    ): EpgFileManager.SourceStats =
        runBlocking {
            val xml = fixture("xmltv/var-xmltv-time-14-digits.xml").toByteArray()
            val file = File(context.cacheDir, "gzip_guide_tmp")
            file.outputStream().use { out -> if (gzip) GZIPOutputStream(out).use { it.write(xml) } else out.write(xml) }
            val source = EpgSourceEntity(id = 1, url = url)
            val downloaded = EpgFileManager.DownloadedSource(source, "Guide", file, downloadedBytes = file.length())
            EpgFileManager
                .getInstance(context)
                .ingestDownloadedSource(downloaded, sourceDao, EpgIndexer.getInstance(context), ConcurrentHashMap(), batchSize = 500) {}
                .stats
        }

    private fun assertIngested(stats: EpgFileManager.SourceStats) {
        assertNull(stats.error)
        assertEquals(2, stats.channelsIngested)
        assertEquals(3, stats.programmesIngested)
    }

    @Test
    fun `gzip at a URL that ends in gz`() = assertIngested(ingest("http://provider.invalid/guide.xml.gz", gzip = true))

    @Test
    fun `gzip at a URL without gz`() = assertIngested(ingest("http://provider.invalid/xmltv.php", gzip = true))

    @Test
    fun `plain XML at a URL without gz`() = assertIngested(ingest("http://provider.invalid/xmltv.php", gzip = false))
}
