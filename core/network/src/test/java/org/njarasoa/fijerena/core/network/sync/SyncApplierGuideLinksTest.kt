package org.njarasoa.fijerena.core.network.sync

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.provider.EpgSourceAliasEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncTriggers
import org.njarasoa.fijerena.core.network.provider.SettingsTombstoneEntity
import org.njarasoa.fijerena.core.network.provider.SettingsVersionEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDao
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Live sync of one guide source per URL (the one guide source per URL plan, Design S): a record
 * names every media source of the guide; a key can be an alias of a guide (an older build's copy
 * for one source, or the same guide under another key); deleting a guide or a link here sends a
 * tombstone for each alias it removes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncApplierGuideLinksTest {
    private companion object {
        /** After every change made here: a received record at this clock is newer. */
        const val LATER = 4_000_000_000_000L
    }

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: SettingsDatabase
    private val index = mockk<EpgIndexDao>(relaxed = true)
    private var a = 0L
    private var b = 0L
    private var c = 0L

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, SettingsDatabase::class.java).allowMainThreadQueries().build()
        SettingsSyncTriggers.install(db.openHelper.writableDatabase)
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion, EpgIndexDatabase.Companion, EpgIndexer.Companion)
        every { SettingsDatabase.getInstance(any()) } returns db
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexDatabase.getInstance(any()) } returns mockk { every { epgIndexDao() } returns index }
        every { EpgIndexer.getInstance(any()) } returns mockk(relaxed = true)
        runBlocking {
            a = provider("a")
            b = provider("b")
            c = provider("c")
        }
        allSent()
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    private val dao get() = db.epgSourceDao()
    private val sync get() = db.settingsSyncDao()

    private suspend fun provider(name: String) =
        db.providerDao().insertProvider(
            ProviderEntity(name = name, url = "http://$name.test", username = "me", type = "M3U", providerKey = "prov-$name"),
        )

    private fun allSent() = db.openHelper.writableDatabase.execSQL("UPDATE sync_version SET pending = 0")

    private suspend fun queued() =
        sync
            .getPending(100)
            .filter { it.kind == SyncKind.EPG_SOURCE }
            .map { it.itemKey }
            .toSet()

    private fun applier() = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null })

    private fun upsert(
        key: String,
        url: String,
        providerKeys: List<String>,
        hlc: Long = LATER,
        label: String = "Guide",
        legacy: Boolean = false,
    ) = SyncRecord(
        SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, key),
        hlc,
        payload =
            SyncPayloads.encode(
                SyncPayloads.EpgSource(
                    providerKey = providerKeys.first(),
                    url = url,
                    label = label,
                    timezoneOffsetHours = 0,
                    enabled = true,
                    providerKeys = if (legacy) emptyList() else providerKeys,
                ),
            ),
    )

    private fun delete(
        key: String,
        hlc: Long = LATER,
    ) = SyncRecord(SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, key), hlc, deleted = true)

    private fun apply(record: SyncRecord) = runBlocking { applier().apply(listOf(record)) }

    private suspend fun guide(
        url: String,
        vararg providerIds: Long,
        key: String = "key-$url",
    ): Long {
        val id = dao.insertGuide(EpgSourceEntity(url = url, label = "Mine", sourceKey = key), providerIds.toList())
        allSent()
        return id
    }

    private suspend fun links(id: Long) = dao.getProviderIds(id).toSet()

    @Test
    fun `a record on the guide key writes the fields and replaces the links`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")

            assertEquals(1, apply(upsert("g", "http://epg.test/g.xml", listOf("prov-b", "prov-c"), label = "New")).applied)

            assertEquals("New", dao.getSourceById(id)!!.label)
            assertEquals(setOf(b, c), links(id))
            assertTrue(queued().isEmpty())
        }

    @Test
    fun `a record from an older build adds its source and keeps the other links`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")

            apply(upsert("g", "http://epg.test/g.xml", listOf("prov-c"), label = "Old", legacy = true))

            assertEquals("Old", dao.getSourceById(id)!!.label)
            assertEquals(setOf(a, b, c), links(id))
        }

    @Test
    fun `a record on a source alias writes the guide and makes sure the source reads it`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))

            assertEquals(1, apply(upsert("old-b", "http://epg.test/g.xml", listOf("prov-b"), label = "From old", legacy = true)).applied)

            assertEquals("From old", dao.getSourceById(id)!!.label)
            assertEquals(setOf(a, b), links(id))
            assertEquals(1, dao.getAllSourcesOnce().size)
        }

    @Test
    fun `a record on a source alias older than the guide's last change is stale`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))
            sync.upsertVersion(SettingsVersionEntity(SyncKind.EPG_SOURCE, SyncKind.SHARED, "g", LATER + 2, pending = false))

            assertEquals(1, apply(upsert("old-b", "http://epg.test/g.xml", listOf("prov-b"), hlc = LATER + 1, label = "Stale")).skipped)

            assertEquals("Mine", dao.getSourceById(id)!!.label)
        }

    @Test
    fun `a record on a guide alias writes the guide`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("other", id, null))

            apply(upsert("other", "http://epg.test/g.xml", listOf("prov-a", "prov-b"), label = "Same"))

            assertEquals("Same", dao.getSourceById(id)!!.label)
            assertEquals(setOf(a, b), links(id))
        }

    @Test
    fun `the same URL known to the server becomes an alias, with the source of a one-source record`() =
        runBlocking {
            val id = guide("HTTP://EPG.test:80/g.xml/", a, key = "g")

            apply(upsert("theirs", "http://epg.test/g.xml", listOf("prov-b"), legacy = true))

            assertEquals(1, dao.getAllSourcesOnce().size)
            assertEquals(EpgSourceAliasEntity("theirs", id, b), dao.getAlias("theirs"))
            assertEquals(setOf(a, b), links(id))
            assertEquals("g", dao.getSourceById(id)!!.sourceKey)
        }

    @Test
    fun `the same URL known to the server, from a record of several sources, becomes a guide alias`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, key = "g")

            apply(upsert("theirs", "http://epg.test/g.xml", listOf("prov-b", "prov-c")))

            assertNull(dao.getAlias("theirs")!!.providerId)
            assertEquals(setOf(a, b, c), links(id))
        }

    @Test
    fun `first sync adopts the key, keeps the sources of both and sends the guide again`() =
        runBlocking {
            val id = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/g.xml", label = "Mine", sourceKey = "local"), listOf(a))

            apply(upsert("theirs", "http://epg.test/g.xml", listOf("prov-b"), label = "Theirs"))

            val adopted = dao.getSourceById(id)!!
            assertEquals("theirs", adopted.sourceKey)
            assertEquals("Theirs", adopted.label)
            assertEquals(setOf(a, b), links(id))
            assertEquals(setOf("theirs"), queued())
            assertNull(sync.get(SyncKind.EPG_SOURCE, SyncKind.SHARED, "local"))
        }

    @Test
    fun `first sync with the same sources adopts the key and sends nothing back`() =
        runBlocking {
            val id = dao.insertGuide(EpgSourceEntity(url = "http://epg.test/g.xml", sourceKey = "local"), listOf(a))

            apply(upsert("theirs", "http://epg.test/g.xml", listOf("prov-a")))

            assertEquals("theirs", dao.getSourceById(id)!!.sourceKey)
            assertTrue(queued().isEmpty())
        }

    @Test
    fun `an automatic guide is never merged with a hand-added guide at its URL`() =
        runBlocking {
            val xtream =
                db.providerDao().insertProvider(
                    ProviderEntity(name = "x", url = "http://iptv.test", username = "me", type = "XTREAM", providerKey = "prov-x"),
                )
            val handAdded = guide("http://iptv.test/xmltv.php", a, key = "hand")
            allSent()

            apply(upsert("auto", "http://iptv.test/xmltv.php?username=me&password=pw", listOf("prov-x"), label = "iptv.test (Bulk)"))

            val auto = sync.sourceByKey("auto")!!
            assertTrue(auto.id != handAdded)
            assertEquals("http://iptv.test/xmltv.php", auto.url)
            assertEquals(listOf(xtream), dao.getProviderIds(auto.id))
            assertEquals(setOf(a), links(handAdded))
        }

    @Test
    fun `a new URL is added with every known source`() =
        runBlocking {
            apply(upsert("new", "http://epg.test/new.xml", listOf("prov-a", "prov-b")))

            val added = sync.sourceByKey("new")!!
            assertEquals(listOf(a, b), dao.getProviderIds(added.id))
        }

    @Test
    fun `a record with a source not here yet waits`() {
        val result = apply(upsert("new", "http://epg.test/new.xml", listOf("prov-a", "prov-unknown")))

        assertEquals(1, result.deferred.size)
        runBlocking { assertNull(sync.sourceByKey("new")) }
    }

    @Test
    fun `a record whose sources are all deleted is skipped, and one with some deleted applies with the others`() =
        runBlocking {
            sync.upsertTombstone(SettingsTombstoneEntity(SyncKind.PROVIDER, "prov-gone", 5))
            sync.upsertTombstone(SettingsTombstoneEntity(SyncKind.PROVIDER, "prov-gone2", 5))

            assertEquals(1, apply(upsert("x", "http://epg.test/x.xml", listOf("prov-gone", "prov-gone2"))).skipped)
            assertNull(sync.sourceByKey("x"))

            assertEquals(1, apply(upsert("y", "http://epg.test/y.xml", listOf("prov-gone", "prov-b"))).applied)
            assertEquals(listOf(b), dao.getProviderIds(sync.sourceByKey("y")!!.id))
        }

    @Test
    fun `a deletion on the guide key deletes the guide and its index rows`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))

            apply(delete("g"))

            assertNull(dao.getSourceById(id))
            assertNull(dao.getAlias("old-b"))
            coVerify { index.deleteBySourceId(id) }
            // The device that deleted it sends the alias tombstones; nothing is sent from here.
            assertTrue(queued().isEmpty())
            assertNull(sync.getTombstone(SyncKind.EPG_SOURCE, "old-b"))
        }

    @Test
    fun `a deletion on a source alias removes that source, and the guide with its last source`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))
            dao.insertAlias(EpgSourceAliasEntity("old-a", id, a))

            apply(delete("old-b"))

            assertEquals(setOf(a), links(id))
            assertNotNull(dao.getSourceById(id))

            apply(delete("old-a"))

            assertNull(dao.getSourceById(id))
            coVerify { index.deleteBySourceId(id) }
        }

    @Test
    fun `a deletion on a guide alias deletes the guide`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("other", id, null))

            apply(delete("other"))

            assertNull(dao.getSourceById(id))
        }

    @Test
    fun `deleting a guide here sends a tombstone for each alias`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))
            dao.insertAlias(EpgSourceAliasEntity("other", id, null))
            allSent()

            dao.deleteGuide(id)

            assertEquals(setOf("g", "old-b", "other"), queued())
            assertTrue(sync.getTombstone(SyncKind.EPG_SOURCE, "old-b")!!.deletedAt > 0)
            assertTrue(sync.getTombstone(SyncKind.EPG_SOURCE, "other")!!.deletedAt > 0)
        }

    @Test
    fun `removing a source of a guide here sends a tombstone for that source's alias only`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))
            dao.insertAlias(EpgSourceAliasEntity("old-a", id, a))
            allSent()

            dao.unlink(id, b)

            assertEquals(setOf("g", "old-b"), queued())
            assertNull(dao.getAlias("old-b"))
            assertNotNull(dao.getAlias("old-a"))
            assertNull(sync.getTombstone(SyncKind.EPG_SOURCE, "old-a"))
        }

    @Test
    fun `the record sent names every source in link order, and an alias of a live guide sends nothing`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", b, a, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-a", id, a))
            sync.queue(SyncKind.EPG_SOURCE, SyncKind.SHARED, "g")
            sync.queue(SyncKind.EPG_SOURCE, SyncKind.SHARED, "old-a")

            val out = LocalRecords(context).pending(10).map { it.record }

            val sent = SyncPayloads.decode<SyncPayloads.EpgSource>(out.single().payload)
            assertEquals(listOf("prov-b", "prov-a"), sent.providerKeys)
            assertEquals("prov-b", sent.providerKey)
            assertEquals("g", out.single().key.itemId)
        }

    @Test
    fun `an alias tombstone is sent as a deletion`() =
        runBlocking {
            val id = guide("http://epg.test/g.xml", a, b, key = "g")
            dao.insertAlias(EpgSourceAliasEntity("old-b", id, b))
            allSent()
            dao.unlink(id, b)

            val out = LocalRecords(context).pending(10).map { it.record }.associateBy { it.key.itemId }

            assertTrue(out.getValue("old-b").deleted)
            assertEquals(listOf("prov-a"), SyncPayloads.decode<SyncPayloads.EpgSource>(out.getValue("g").payload).providerKeys)
        }
}
