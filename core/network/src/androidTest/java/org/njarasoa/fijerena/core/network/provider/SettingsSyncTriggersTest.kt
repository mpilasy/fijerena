package org.njarasoa.fijerena.core.network.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.SyncKind

/**
 * [SettingsSyncTriggers] queue changes to providers, profiles and EPG sources — only when a synced
 * column changes — and stamp and queue their deletions. Uses the real `providers.db` of this test
 * APK (the triggers are installed on open), never the app's.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSyncTriggersTest {
    private val db = SettingsDatabase.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
    private val sync = db.settingsSyncDao()

    private fun queued(
        kind: String,
        itemKey: String,
    ) = runBlocking { sync.getPending(100_000) }.filter { it.kind == kind && it.itemKey == itemKey }

    @Test
    fun aProviderIsQueuedForSyncedChangesOnly() =
        runBlocking {
            val id = db.providerDao().insertProvider(ProviderEntity(name = "trig", url = "http://trig.test", username = "u"))
            val key = db.providerDao().getProviderById(id)!!.providerKey
            val added = queued(SyncKind.PROVIDER, key).single()
            assertEquals(SyncKind.SHARED, added.profileId)

            db.providerDao().activateProvider(id)
            db.providerDao().updateSyncStats(id, 1L, 1L, null, 1, 1, 1)
            assertEquals(added.hlc, queued(SyncKind.PROVIDER, key).single().hlc)

            db.providerDao().updateProvider(db.providerDao().getProviderById(id)!!.copy(name = "renamed"))
            assertTrue(queued(SyncKind.PROVIDER, key).single().hlc > added.hlc)
        }

    @Test
    fun aProfileAndItsDeletionAreQueued() =
        runBlocking {
            db.profileDao().insert(
                org.njarasoa.fijerena.core.network.profile
                    .ProfileEntity("trig-profile", "Trig", 1L),
            )
            val added = queued(SyncKind.PROFILE, "trig-profile").single()

            db.profileDao().deleteRecordingTombstone("trig-profile")

            val tombstone = db.providerDao().getAllTombstones().single { it.itemKey == "trig-profile" }
            assertTrue(tombstone.deletedAt > added.hlc)
            assertEquals(tombstone.deletedAt, queued(SyncKind.PROFILE, "trig-profile").single().hlc)
        }

    @Test
    fun anEpgSourceIsQueuedButNotItsIngestionAndItsDeletionIsRecorded() =
        runBlocking {
            val sources = db.epgSourceDao()
            val id = sources.insertSource(EpgSourceEntity(url = "http://trig.test/epg.xml", providerId = 1L))
            val key = sources.getSourceById(id)!!.sourceKey
            val added = queued(SyncKind.EPG_SOURCE, key).single()

            sources.markError(id, "boom")
            assertEquals(added.hlc, queued(SyncKind.EPG_SOURCE, key).single().hlc)

            sources.deleteSource(id)
            val tombstone = db.providerDao().getAllTombstones().single { it.kind == SyncKind.EPG_SOURCE && it.itemKey == key }
            assertTrue(tombstone.deletedAt > added.hlc)
            assertEquals(tombstone.deletedAt, queued(SyncKind.EPG_SOURCE, key).single().hlc)
        }

    @Test
    fun explicitQueueingMovesTheClockForward() =
        runBlocking {
            val before = sync.clock()
            sync.queue(SyncKind.SETTING, SyncKind.SHARED, "theme_id")
            val entry = queued(SyncKind.SETTING, "theme_id").single()
            assertTrue(entry.hlc > before)
            assertEquals(entry.hlc, sync.clock())
        }

    @Test
    fun nothingIsQueuedWhileApplyingRemoteChanges() =
        runBlocking {
            val raw = db.openHelper.writableDatabase
            raw.execSQL("UPDATE sync_clock SET applying = 1")
            val id =
                try {
                    db.providerDao().insertProvider(ProviderEntity(name = "remote", url = "http://remote.test", username = "u"))
                } finally {
                    raw.execSQL("UPDATE sync_clock SET applying = 0")
                }
            assertTrue(queued(SyncKind.PROVIDER, db.providerDao().getProviderById(id)!!.providerKey).isEmpty())
        }
}
