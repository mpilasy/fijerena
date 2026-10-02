package org.njarasoa.fijerena.core.network.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.sync.pruneSyncTombstones
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Provider and profile deletions are recorded for live sync, and take their favourite and history
 * tombstones with them. See docs/plans/20260929_live-sync-plan.md → Deletions (tombstones).
 *
 * Runs against this test APK's own databases and prefs, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class SyncTombstoneTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deletingAProvider_recordsItsKeyAndDropsItsItemTombstones() =
        runBlocking {
            val providers = ProviderRepository(context)
            val id = providers.addProvider("tomb", "http://tomb.test", "u", "p", "XTREAM", activate = false)
            val key = providers.getProviderById(id)!!.providerKey
            val xtream = XtreamDatabase.getInstance(context)
            xtream.favoriteStateDao().deleteRecordingTombstone(id, "someone", "m1", "MOVIES", FavoriteKind.STREAM, 1L)
            assertEquals(1, xtream.syncTombstoneDao().getAll(id).size)

            providers.deleteProvider(id)

            val tombstone =
                SettingsDatabase
                    .getInstance(context)
                    .providerDao()
                    .getAllTombstones()
                    .single { it.kind == SyncKind.PROVIDER && it.itemKey == key }
            assertTrue(tombstone.deletedAt > 0)
            assertTrue(xtream.syncTombstoneDao().getAll(id).isEmpty())
        }

    @Test
    fun deletingAProfile_recordsItAndDropsItsItemTombstones() =
        runBlocking {
            val profiles = ProfileRepository(context)
            val keepId = profiles.addProfile("Keep", 1)
            val goneId = profiles.addProfile("Gone", 2)
            AppSettings(context).activeProfileId = keepId
            val xtream = XtreamDatabase.getInstance(context)
            xtream.favoriteStateDao().deleteRecordingTombstone(7L, goneId, "m1", "MOVIES", FavoriteKind.STREAM, 1L)

            assertEquals(ProfileRepository.DeleteBlocked.NONE, profiles.deleteProfile(goneId))

            val recorded = SettingsDatabase.getInstance(context).providerDao().getAllTombstones()
            assertTrue(recorded.any { it.kind == SyncKind.PROFILE && it.itemKey == goneId })
            assertFalse(xtream.syncTombstoneDao().getAll(7L).any { it.profileId == goneId })
        }

    @Test
    fun pruning_dropsOnlyDeletionsPastTheRetention() =
        runBlocking {
            val xtream = XtreamDatabase.getInstance(context)
            val now = 1_000_000_000_000L
            xtream.favoriteStateDao().deleteRecordingTombstone(
                9L,
                "p",
                "old",
                "MOVIES",
                FavoriteKind.STREAM,
                now - SyncKind.TOMBSTONE_RETENTION_MS - 1,
            )
            xtream.favoriteStateDao().deleteRecordingTombstone(9L, "p", "new", "MOVIES", FavoriteKind.STREAM, now - 1)

            pruneSyncTombstones(context, now)

            assertEquals(listOf("new"), xtream.syncTombstoneDao().getAll(9L).map { it.itemId })
        }
}
