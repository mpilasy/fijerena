package org.njarasoa.fijerena.core.network.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersSerializer
import org.njarasoa.fijerena.core.network.provider.CategoryFiltersStore
import org.njarasoa.fijerena.core.network.provider.CategoryMatcher
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.util.UUID

/**
 * [SyncApplier] applies received records against the real databases of this test APK (never the
 * app's): each kind lands, loses to newer local state, and is never queued to be sent back — its
 * version is the received one, not pending.
 */
@RunWith(AndroidJUnit4::class)
class SyncApplierTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val applier = SyncApplier(context)
    private val settingsDb = SettingsDatabase.getInstance(context)
    private val sync = settingsDb.settingsSyncDao()
    private val xtreamDb = XtreamDatabase.getInstance(context)

    private suspend fun newProfile(): String = ProfileRepository(context).addProfile("P-${UUID.randomUUID()}", 1)

    private suspend fun newProvider(type: String = "XTREAM"): Pair<Long, String> {
        val id = ProviderRepository(context).addProvider("prov", "http://${UUID.randomUUID()}.test", "u", "p", type, activate = false)
        return id to settingsDb.providerDao().getProviderById(id)!!.providerKey
    }

    /** The received version, not queued to go back. */
    private suspend fun assertReceived(
        kind: String,
        profileKey: String,
        itemKey: String,
        hlc: Long,
    ) {
        val version = sync.get(kind, profileKey, itemKey)!!
        assertEquals(hlc, version.hlc)
        assertFalse(version.pending)
    }

    private fun favorite(
        profile: String,
        providerKey: String,
        hlc: Long,
        deleted: Boolean = false,
    ) = SyncRecord(
        SyncKey(profile, providerKey, SyncKind.FAVORITE_STREAM, "m1", "MOVIES"),
        hlc,
        deleted,
        if (deleted) null else SyncPayloads.encode(SyncPayloads.Favorite("Film", "c1", 1L)),
    )

    @Test
    fun aFavoriteArrivesIsDeletedAndLosesToNewerLocalState() =
        runBlocking {
            val profile = newProfile()
            val (providerId, providerKey) = newProvider()
            val favorites = xtreamDb.favoriteStateDao()
            val versions = xtreamDb.syncVersionDao()

            val result = applier.apply(listOf(favorite(profile, providerKey, FAR_FUTURE)))
            assertEquals(1, result.applied)
            assertEquals(setOf(providerId), result.userDataChangedProviderIds)
            assertEquals(listOf("m1"), favorites.getAll(providerId, profile).map { it.itemId })
            val version = versions.get(providerId, profile, SyncKind.FAVORITE_STREAM, "m1", "MOVIES")!!
            assertEquals(FAR_FUTURE, version.hlc)
            assertFalse(version.pending)

            // An older record changes nothing.
            assertEquals(1, applier.apply(listOf(favorite(profile, providerKey, FAR_FUTURE - 5))).skipped)

            applier.apply(listOf(favorite(profile, providerKey, FAR_FUTURE + 1, deleted = true)))
            assertTrue(favorites.getAll(providerId, profile).isEmpty())
            assertEquals(
                FAR_FUTURE + 1,
                xtreamDb.syncTombstoneDao().get(providerId, profile, SyncKind.FAVORITE_STREAM, "m1", "MOVIES")!!.deletedAt,
            )
            assertFalse(versions.get(providerId, profile, SyncKind.FAVORITE_STREAM, "m1", "MOVIES")!!.pending)
            // The clock took the received value in: this device's next change comes after it.
            assertTrue(versions.clock() >= FAR_FUTURE + 1)
        }

    @Test
    fun aHistoryClearDropsOnlyOlderRows() =
        runBlocking {
            val profile = newProfile()
            val (providerId, providerKey) = newProvider()
            fun watch(
                item: String,
                hlc: Long,
            ) = SyncRecord(
                SyncKey(profile, providerKey, SyncKind.WATCH, item, "MOVIES"),
                hlc,
                payload = SyncPayloads.encode(SyncPayloads.Watch("Film", "c1", 1_000, 5_000, false, 1L)),
            )
            applier.apply(listOf(watch("old", FAR_FUTURE), watch("new", FAR_FUTURE + 20)))
            assertEquals(2, xtreamDb.watchStateDao().getAll(providerId, profile).size)

            applier.apply(listOf(SyncRecord(SyncKey(profile, providerKey, SyncKind.WATCH_CLEAR), FAR_FUTURE + 10)))
            assertEquals(listOf("new"), xtreamDb.watchStateDao().getAll(providerId, profile).map { it.itemId })

            // A watch row from before the clear, arriving late, stays out.
            applier.apply(listOf(watch("old", FAR_FUTURE + 5)))
            assertEquals(listOf("new"), xtreamDb.watchStateDao().getAll(providerId, profile).map { it.itemId })
        }

    @Test
    fun recordsWaitForTheirProviderAndJellyfinHistoryIsIgnored() =
        runBlocking {
            val profile = newProfile()
            assertEquals(1, applier.apply(listOf(favorite(profile, "not-here-yet", FAR_FUTURE))).deferred.size)

            val (jellyfinId, jellyfinKey) = newProvider("JELLYFIN")
            assertEquals(1, applier.apply(listOf(favorite(profile, jellyfinKey, FAR_FUTURE))).skipped)
            assertTrue(xtreamDb.favoriteStateDao().getAll(jellyfinId, profile).isEmpty())
        }

    @Test
    fun aProviderArrivesWithItsPasswordAndInactiveThenIsDeleted() =
        runBlocking {
            val key = UUID.randomUUID().toString()
            val payload = SyncPayloads.Provider("From phone", "http://phone.test", "user", "XTREAM", "", "{}", "secret")

            applier.apply(listOf(SyncRecord(SyncKey(SyncKind.SHARED, key, SyncKind.PROVIDER), FAR_FUTURE, payload = SyncPayloads.encode(payload))))

            val provider = sync.providerByKey(key)!!
            assertEquals("From phone", provider.name)
            assertFalse(provider.isActive)
            assertEquals("secret", ProviderRepository(context).getPassword(provider.id))
            assertReceived(SyncKind.PROVIDER, SyncKind.SHARED, key, FAR_FUTURE)

            applier.apply(listOf(SyncRecord(SyncKey(SyncKind.SHARED, key, SyncKind.PROVIDER), FAR_FUTURE + 1, deleted = true)))
            assertNull(sync.providerByKey(key))
            assertEquals(FAR_FUTURE + 1, sync.getTombstone(SyncKind.PROVIDER, key)!!.deletedAt)
            assertReceived(SyncKind.PROVIDER, SyncKind.SHARED, key, FAR_FUTURE + 1)
        }

    @Test
    fun anEpgSourceArrivesOnItsProvider() =
        runBlocking {
            val (providerId, providerKey) = newProvider()
            val sourceKey = UUID.randomUUID().toString()
            applier.apply(
                listOf(
                    SyncRecord(
                        SyncKey(SyncKind.SHARED, "", SyncKind.EPG_SOURCE, sourceKey),
                        FAR_FUTURE,
                        payload = SyncPayloads.encode(SyncPayloads.EpgSource(providerKey, "http://epg.test/$sourceKey.xml", "EPG", 1, true)),
                    ),
                ),
            )
            val stored = sync.sourceByKey(sourceKey)!!
            assertEquals(providerId, stored.providerId)
            assertEquals(1, stored.timezoneOffsetHours)
            assertReceived(SyncKind.EPG_SOURCE, SyncKind.SHARED, sourceKey, FAR_FUTURE)
        }

    @Test
    fun profilesArriveAndAnActiveOneIsNotDeletedUnderTheUser() =
        runBlocking {
            val id = UUID.randomUUID().toString()
            val profileKey = SyncKey(SyncKind.SHARED, "", SyncKind.PROFILE, id)
            applier.apply(listOf(SyncRecord(profileKey, FAR_FUTURE, payload = SyncPayloads.encode(SyncPayloads.Profile("Remote", 3, 1L)))))
            assertTrue(settingsDb.profileDao().exists(id))
            assertReceived(SyncKind.PROFILE, SyncKind.SHARED, id, FAR_FUTURE)

            AppSettings(context).activeProfileId = id
            val result = applier.apply(listOf(SyncRecord(profileKey, FAR_FUTURE + 1, deleted = true)))
            assertTrue(result.activeProfileDeleted)
            assertEquals(1, result.deferred.size)
            assertTrue(settingsDb.profileDao().exists(id))

            AppSettings(context).activeProfileId = newProfile()
            applier.apply(result.deferred)
            assertFalse(settingsDb.profileDao().exists(id))
            assertEquals(FAR_FUTURE + 1, sync.getTombstone(SyncKind.PROFILE, id)!!.deletedAt)
            assertReceived(SyncKind.PROFILE, SyncKind.SHARED, id, FAR_FUTURE + 1)
        }

    @Test
    fun settingsAndCategoryFiltersArrive() =
        runBlocking {
            val profile = newProfile()
            val (providerId, providerKey) = newProvider()
            val adult = CategoryFilters(rules = listOf(CategoryMatcher("Adult")))
            applier.apply(
                listOf(
                    SyncRecord(
                        SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, "theme_id"),
                        FAR_FUTURE,
                        payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive("midnight_test"))),
                    ),
                    SyncRecord(
                        SyncKey(profile, providerKey, SyncKind.CATEGORY_FILTERS),
                        FAR_FUTURE,
                        payload = SyncPayloads.json.encodeToString(CategoryFiltersSerializer, adult),
                    ),
                ),
            )
            assertEquals("midnight_test", AppSettings(context).themeId)
            assertEquals(adult, CategoryFiltersStore(context).get(providerId, profile))
            assertReceived(SyncKind.SETTING, SyncKind.SHARED, "theme_id", FAR_FUTURE)
            assertReceived(SyncKind.CATEGORY_FILTERS, profile, providerKey, FAR_FUTURE)
        }

    private companion object {
        // Far beyond any wall clock, so these records always beat local state.
        const val FAR_FUTURE = 4_000_000_000_000L
    }
}
