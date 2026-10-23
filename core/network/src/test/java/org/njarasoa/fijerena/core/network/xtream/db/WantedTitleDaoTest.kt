package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Wanted list's storage and its live sync bookkeeping, against a real in-memory `xtream_v2.db`
 * with the sync triggers installed: every add and update is queued under the no-provider id, a
 * removal leaves a tombstone, re-adding clears it, and linking seeds the list. See
 * the wanted titles plan, Phase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WantedTitleDaoTest {
    private lateinit var db: XtreamDatabase
    private lateinit var wanted: WantedTitleDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = XtreamSyncTriggers.install(db)
                    },
                ).build()
        wanted = db.wantedTitleDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `adding a title queues it for sync without a provider`() =
        runBlocking {
            wanted.upsertClearingTombstone(dune())

            val version = db.syncVersionDao().get(SyncKind.WANTED_PROVIDER_ID, PROFILE, SyncKind.WANTED, "438631", ContentType.MOVIES)
            assertNotNull(version)
            assertEquals(true, version!!.pending)
        }

    @Test
    fun `marking notified sets it once and queues the change`() =
        runBlocking {
            wanted.upsertClearingTombstone(dune())
            db.openHelper.writableDatabase.execSQL("UPDATE sync_version SET pending = 0")

            wanted.markNotified(PROFILE, ContentType.MOVIES, "438631", at = 50)
            wanted.markNotified(PROFILE, ContentType.MOVIES, "438631", at = 60)

            assertEquals(50L, wanted.get(PROFILE, ContentType.MOVIES, "438631")!!.notifiedAt)
            assertEquals(
                true,
                db
                    .syncVersionDao()
                    .getPending(10)
                    .single()
                    .pending,
            )
        }

    @Test
    fun `removing a title records a tombstone and adding it again clears it`() =
        runBlocking {
            wanted.upsertClearingTombstone(dune())

            wanted.deleteRecordingTombstone(PROFILE, ContentType.MOVIES, "438631")
            assertNull(wanted.get(PROFILE, ContentType.MOVIES, "438631"))
            val tombstone = db.syncTombstoneDao().get(SyncKind.WANTED_PROVIDER_ID, PROFILE, SyncKind.WANTED, "438631", ContentType.MOVIES)
            assertNotNull(tombstone)
            assert(tombstone!!.deletedAt > 0) { "the trigger stamps the tombstone with the sync clock" }

            wanted.upsertClearingTombstone(dune())
            assertNull(db.syncTombstoneDao().get(SyncKind.WANTED_PROVIDER_ID, PROFILE, SyncKind.WANTED, "438631", ContentType.MOVIES))
        }

    @Test
    fun `release data is this device's own and queues nothing`() =
        runBlocking {
            wanted.upsertClearingTombstone(dune())
            db.openHelper.writableDatabase.execSQL("UPDATE sync_version SET pending = 0")

            wanted.setRelease(PROFILE, ContentType.MOVIES, "438631", "2021-09-15", "2022-01-19", true, null, checkedAt = 5)

            assertEquals(emptyList<SyncVersionEntity>(), db.syncVersionDao().getPending(10))
            assertEquals("2021-09-15", wanted.get(PROFILE, ContentType.MOVIES, "438631")!!.releaseDate)
        }

    @Test
    fun `linking seeds every title at its latest change`() =
        runBlocking {
            db.syncVersionDao().setApplying(true)
            wanted.upsert(dune().copy(addedAt = 10, seenAt = 30))
            db.syncVersionDao().setApplying(false)

            db.syncVersionDao().seedWanted()

            val version = db.syncVersionDao().get(SyncKind.WANTED_PROVIDER_ID, PROFILE, SyncKind.WANTED, "438631", ContentType.MOVIES)
            assertEquals(30L, version!!.hlc)
        }

    @Test
    fun `deleting a profile drops its list only`() =
        runBlocking {
            wanted.upsert(dune())
            wanted.upsert(dune().copy(profileId = "other"))

            wanted.deleteProfile(PROFILE)

            assertEquals(emptyList<WantedTitleEntity>(), wanted.getAll(PROFILE))
            assertEquals(1, wanted.getAll("other").size)
        }

    private fun dune() = WantedTitleEntity(PROFILE, ContentType.MOVIES, "438631", "Dune", originalTitle = "Dune", year = 2021, addedAt = 1)

    private companion object {
        const val PROFILE = "default"
    }
}
