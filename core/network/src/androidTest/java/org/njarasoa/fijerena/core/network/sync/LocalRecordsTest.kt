package org.njarasoa.fijerena.core.network.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteKind
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.util.UUID

/**
 * [LocalRecords] turns pending versions into records to send, and seeding queues what already
 * exists. Real databases of this test APK, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class LocalRecordsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settingsDb = SettingsDatabase.getInstance(context)
    private val xtreamDb = XtreamDatabase.getInstance(context)
    private val local = LocalRecords(context)

    private suspend fun everythingPending(): List<LocalRecords.Outgoing> {
        val all = mutableListOf<LocalRecords.Outgoing>()
        // Mark each batch sent so the next call moves on; collect everything.
        while (true) {
            val batch = local.pending(500)
            if (batch.isEmpty()) return all
            all += batch
            batch.forEach { it.markSent() }
        }
    }

    @Test
    fun aFavoriteAndItsProviderBecomeRecordsAndAreMarkedSent() =
        runBlocking {
            everythingPending() // start from an empty queue
            val profile = ProfileRepository(context).addProfile("LR-${UUID.randomUUID()}", 1)
            val providerId =
                ProviderRepository(
                    context,
                ).addProvider("lr", "http://${UUID.randomUUID()}.test", "u", "pw", "XTREAM", activate = false)
            val providerKey = settingsDb.providerDao().getProviderById(providerId)!!.providerKey
            xtreamDb.favoriteStateDao().upsertClearingTombstone(
                FavoriteStateEntity(providerId, profile, "m1", "MOVIES", FavoriteKind.STREAM, "Film", "c1", 1L),
            )

            val records = everythingPending().map { it.record }
            val provider = records.single { it.key.kind == SyncKind.PROVIDER && it.key.providerKey == providerKey }
            assertEquals("pw", SyncPayloads.decode<SyncPayloads.Provider>(provider.payload).password)
            val favorite = records.single { it.key.kind == SyncKind.FAVORITE_STREAM && it.key.providerKey == providerKey }
            assertEquals(SyncKey(profile, providerKey, SyncKind.FAVORITE_STREAM, "m1", "MOVIES"), favorite.key)
            assertEquals("Film", SyncPayloads.decode<SyncPayloads.Favorite>(favorite.payload).name)
            // Providers go before the favourites that depend on them.
            assertTrue(records.indexOf(provider) < records.indexOf(favorite))

            assertTrue(local.pending(500).isEmpty())
        }

    @Test
    fun aRemovedFavoriteIsSentAsADeletion() =
        runBlocking {
            everythingPending()
            val profile = ProfileRepository(context).addProfile("LR-${UUID.randomUUID()}", 1)
            val providerId =
                ProviderRepository(
                    context,
                ).addProvider("lr", "http://${UUID.randomUUID()}.test", "u", "pw", "XTREAM", activate = false)
            xtreamDb.favoriteStateDao().upsertClearingTombstone(
                FavoriteStateEntity(providerId, profile, "m1", "MOVIES", FavoriteKind.STREAM, "Film", "c1", 1L),
            )
            xtreamDb.favoriteStateDao().deleteRecordingTombstone(providerId, profile, "m1", "MOVIES", FavoriteKind.STREAM)

            val favorite =
                everythingPending().map { it.record }.single {
                    it.key.kind == SyncKind.FAVORITE_STREAM &&
                        it.key.profileKey == profile
                }
            assertTrue(favorite.deleted)
            assertNull(favorite.payload)
        }

    @Test
    fun aChangeMadeWhileSendingStaysPending() =
        runBlocking {
            everythingPending()
            val profile = ProfileRepository(context).addProfile("LR-${UUID.randomUUID()}", 1)
            val providerId =
                ProviderRepository(
                    context,
                ).addProvider("lr", "http://${UUID.randomUUID()}.test", "u", "pw", "XTREAM", activate = false)
            val favorite = FavoriteStateEntity(providerId, profile, "m1", "MOVIES", FavoriteKind.STREAM, "Film", "c1", 1L)
            xtreamDb.favoriteStateDao().upsertClearingTombstone(favorite)

            val sending = local.pending(500)
            xtreamDb.favoriteStateDao().upsertClearingTombstone(favorite.copy(name = "Renamed")) // while "in flight"
            sending.forEach { it.markSent() }

            val again = local.pending(500).single { it.record.key.profileKey == profile }
            assertEquals("Renamed", SyncPayloads.decode<SyncPayloads.Favorite>(again.record.payload).name)
        }

    @Test
    fun seedingQueuesWhatAlreadyExistsAtItsOwnTime() =
        runBlocking {
            val profile = ProfileRepository(context).addProfile("LR-${UUID.randomUUID()}", 1)
            val providerId =
                ProviderRepository(
                    context,
                ).addProvider("lr", "http://${UUID.randomUUID()}.test", "u", "pw", "XTREAM", activate = false)
            xtreamDb.favoriteStateDao().upsertClearingTombstone(
                FavoriteStateEntity(providerId, profile, "m1", "MOVIES", FavoriteKind.STREAM, "Film", "c1", 123L),
            )
            everythingPending()
            // As if it existed before sync: no version at all.
            xtreamDb.syncVersionDao().deleteVersion(providerId, profile, SyncKind.FAVORITE_STREAM, "m1", "MOVIES")

            local.seedEverything()

            val seeded = xtreamDb.syncVersionDao().get(providerId, profile, SyncKind.FAVORITE_STREAM, "m1", "MOVIES")
            assertNotNull(seeded)
            assertEquals(123L, seeded!!.hlc)
            assertTrue(seeded.pending)
        }

    @Test
    fun aProviderThisDeviceAlreadyHadAdoptsTheOtherDevicesKey() =
        runBlocking {
            everythingPending()
            val url = "http://${UUID.randomUUID()}.test"
            val providerId = ProviderRepository(context).addProvider("mine", url, "same-user", "pw", "XTREAM", activate = false)
            val otherKey = UUID.randomUUID().toString()

            SyncApplier(context).apply(
                listOf(
                    SyncRecord(
                        SyncKey(SyncKind.SHARED, otherKey, SyncKind.PROVIDER),
                        4_000_000_000_000L,
                        payload = SyncPayloads.encode(SyncPayloads.Provider("theirs", "$url/", "same-user", "XTREAM", "", "{}", "pw")),
                    ),
                ),
            )

            val provider = settingsDb.providerDao().getProviderById(providerId)!!
            assertEquals(otherKey, provider.providerKey)
            assertEquals("theirs", provider.name)
            assertEquals(1, settingsDb.providerDao().getAllProvidersList().count { it.url.trimEnd('/') == url })
            assertFalse(settingsDb.settingsSyncDao().get(SyncKind.PROVIDER, SyncKind.SHARED, otherKey)!!.pending)
        }
}
