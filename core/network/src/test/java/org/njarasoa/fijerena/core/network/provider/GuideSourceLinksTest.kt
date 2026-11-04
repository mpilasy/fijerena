package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDao
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A guide source linked to the media sources that read it (the one guide source per URL plan,
 * Phase 1): a link change queues the guide for live sync, and deleting a source keeps a guide
 * another source still reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideSourceLinksTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: SettingsDatabase
    private val index = mockk<EpgIndexDao>(relaxed = true)

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, SettingsDatabase::class.java).allowMainThreadQueries().build()
        SettingsSyncTriggers.install(db.openHelper.writableDatabase)
        mockkObject(SettingsDatabase.Companion, EpgIndexDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns db
        every { EpgIndexDatabase.getInstance(any()) } returns mockk { every { epgIndexDao() } returns index }
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    private suspend fun provider(name: String) =
        db.providerDao().insertProvider(ProviderEntity(name = name, url = "http://$name.test", username = "me", type = "M3U"))

    private fun allSent() = db.openHelper.writableDatabase.execSQL("UPDATE sync_version SET pending = 0")

    private suspend fun queuedKeys() =
        db
            .settingsSyncDao()
            .getPending(100)
            .filter { it.kind == SyncKind.EPG_SOURCE }
            .map { it.itemKey }

    @Test
    fun `adding or removing a source of a guide queues the guide`() =
        runBlocking {
            val a = provider("a")
            val b = provider("b")
            val dao = db.epgSourceDao()
            val id = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/g.xml"), listOf(a))
            val key = dao.getSourceById(id)!!.sourceKey
            allSent()

            dao.setLinks(id, listOf(a, b))
            assertEquals(listOf(key), queuedKeys())
            assertEquals(listOf(a, b), dao.getProviderIds(id))

            allSent()
            dao.unlink(id, a)
            assertEquals(listOf(key), queuedKeys())
            assertEquals(listOf(b), dao.getProviderIds(id))
        }

    @Test
    fun `deleting a guide deletes its links and aliases`() =
        runBlocking {
            val a = provider("a")
            val dao = db.epgSourceDao()
            val id = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/g.xml"), listOf(a))
            dao.insertAlias(EpgSourceAliasEntity("old-key", id, a))

            dao.deleteGuide(id)

            assertNull(dao.getSourceById(id))
            assertTrue(dao.getAllLinksOnce().isEmpty())
            assertNull(dao.getAlias("old-key"))
        }

    @Test
    fun `deleting a source keeps a guide another source reads, and deletes one only it read`() =
        runBlocking {
            val a = provider("a")
            val b = provider("b")
            val dao = db.epgSourceDao()
            val shared = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/shared.xml"), listOf(a, b))
            val own = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/own.xml"), listOf(a))

            ProviderRepository(context).deleteProviderEpgSources(a)

            assertEquals(listOf(b), dao.getProviderIds(shared))
            assertNull(dao.getSourceById(own))
            coVerify(exactly = 1) { index.deleteBySourceIds(listOf(own)) }
            coVerify(exactly = 0) { index.deleteBySourceIds(listOf(shared)) }
        }
}
