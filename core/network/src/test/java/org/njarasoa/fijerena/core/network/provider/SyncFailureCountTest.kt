package org.njarasoa.fijerena.core.network.provider

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A failed sync keeps the last success and counts the failures since it; a success clears them
 * ([ProviderDao.updateSyncStats], [EpgSourceDao.markError], `markIngested`, `markUnchanged`,
 * `resetAllIngestionState`). None of it is queued for live sync. Against a real in-memory
 * `providers.db` with the live sync triggers, under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncFailureCountTest {
    private lateinit var db: SettingsDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SettingsDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        SettingsSyncTriggers.install(db.openHelper.writableDatabase)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pending() = runBlocking { db.settingsSyncDao().getPending(100).size }

    @Test
    fun `a source's failures keep the last success and count, a success clears them`() =
        runBlocking {
            val dao = db.providerDao()
            val id = dao.insertProvider(ProviderEntity(name = "P", url = "http://p.test", username = "u"))
            dao.updateSyncStats(id, 1_000, 10, null, 5, 6, 7)
            val queued = pending()

            dao.updateSyncStats(id, 2_000, 20, "first")
            dao.updateSyncStats(id, 3_000, 30, "second")

            dao.getProviderById(id)!!.let {
                assertEquals(1_000L, it.lastSyncedAtMs)
                assertEquals(10L, it.lastSyncDurationMs)
                assertEquals("second", it.lastSyncError)
                assertEquals(3_000L, it.lastFailedAtMs)
                assertEquals(2, it.failuresSinceSuccess)
                assertEquals(5, it.lastSyncInserted)
            }
            assertEquals(queued, pending())

            dao.updateSyncStats(id, 4_000, 40, null, 0, 1, 0)

            dao.getProviderById(id)!!.let {
                assertEquals(4_000L, it.lastSyncedAtMs)
                assertEquals(40L, it.lastSyncDurationMs)
                assertNull(it.lastSyncError)
                assertNull(it.lastFailedAtMs)
                assertEquals(0, it.failuresSinceSuccess)
                assertEquals(1, it.lastSyncUpdated)
            }
            assertEquals(queued, pending())
        }

    @Test
    fun `a source that never synced keeps 0 as its last success through failures`() =
        runBlocking {
            val dao = db.providerDao()
            val id = dao.insertProvider(ProviderEntity(name = "P", url = "http://p.test", username = "u"))
            dao.updateSyncStats(id, 2_000, 20, "no server")
            dao.getProviderById(id)!!.let {
                assertEquals(0L, it.lastSyncedAtMs)
                assertEquals(2_000L, it.lastFailedAtMs)
                assertEquals(1, it.failuresSinceSuccess)
            }
        }

    @Test
    fun `a guide source's failures count, each success kind clears them`() =
        runBlocking {
            val dao = db.epgSourceDao()
            val id = dao.insertSource(EpgSourceEntity(url = "http://epg.test/a.xml", providerId = 1, lastIngestedAtMs = 500))
            val queued = pending()

            dao.markError(id, "one", failedAtMs = 1_000)
            dao.markError(id, "two", failedAtMs = 2_000)
            dao.getSourceById(id)!!.let {
                assertEquals(500L, it.lastIngestedAtMs)
                assertEquals("two", it.lastError)
                assertEquals(2_000L, it.lastFailedAtMs)
                assertEquals(2, it.failuresSinceSuccess)
            }
            assertEquals(queued, pending())

            dao.markUnchanged(id, 3_000)
            dao.getSourceById(id)!!.assertCleared()

            dao.markError(id, "three", failedAtMs = 4_000)
            dao.markIngested(id, 5_000, channels = 1, programmes = 2, downloadBytes = 3)
            dao.getSourceById(id)!!.assertCleared()

            dao.markError(id, "four", failedAtMs = 6_000)
            dao.resetAllIngestionState()
            dao.getSourceById(id)!!.assertCleared()
            assertEquals(queued, pending())
        }

    private fun EpgSourceEntity.assertCleared() {
        assertNull(lastError)
        assertNull(lastFailedAtMs)
        assertEquals(0, failuresSinceSuccess)
    }
}
