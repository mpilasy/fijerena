package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A provider record, login or category filters received from another device must reach the cached
 * `MediaRepository`: the pass reports the providers it changed, and `SyncManager` evicts them. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 2.
 */
class SyncApplierProviderChangesTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val provider = ProviderEntity(id = 7, name = "IPTV", url = "http://iptv.test", username = "me", providerKey = "prov-1")

    @Before
    fun setup() {
        every { settingsDb.settingsSyncDao() } returns sync
        every { settingsDb.profileDao() } returns profiles
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        coEvery { sync.get(any(), any(), any()) } returns null
        coEvery { sync.getTombstone(any(), any()) } returns null
        coEvery { sync.providerByKey("prov-1") } returns provider
        coEvery { profiles.exists(ProfileEntity.DEFAULT_ID) } returns true
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().applyRemoteProvider(any(), any()) } returns provider.id
        coEvery { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any(), any()) } returns Unit
        coEvery { anyConstructed<ProviderRepository>().applyRemoteCategoryFilters(any(), any(), any()) } returns Unit
        coEvery { anyConstructed<ProviderRepository>().deleteProvider(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun applier() = SyncApplier(mockk<Context>(relaxed = true), thisDeviceId = { "this-device" }, playingSessionId = { null })

    private val providerRecord =
        SyncRecord(
            SyncKey(SyncKind.SHARED, "prov-1", SyncKind.PROVIDER),
            hlc = 10,
            payload = SyncPayloads.encode(SyncPayloads.Provider("IPTV", "http://new.test", "me", "XTREAM", "", "{}", "new-password")),
        )
    private val loginRecord =
        SyncRecord(
            SyncKey(ProfileEntity.DEFAULT_ID, "prov-1", SyncKind.PROVIDER_LOGIN),
            hlc = 11,
            payload = SyncPayloads.encode(SyncPayloads.Login("me", "new-password")),
        )
    private val filtersRecord =
        SyncRecord(SyncKey(ProfileEntity.DEFAULT_ID, "prov-1", SyncKind.CATEGORY_FILTERS), hlc = 12, payload = "{}")

    @Test
    fun `a provider record reports its provider`() =
        runBlocking {
            val result = applier().apply(listOf(providerRecord))

            assertEquals(1, result.applied)
            assertEquals(setOf(provider.id), result.providerChangedIds)
        }

    @Test
    fun `a login or category filters record reports its provider`() =
        runBlocking {
            assertEquals(setOf(provider.id), applier().apply(listOf(loginRecord)).providerChangedIds)
            assertEquals(setOf(provider.id), applier().apply(listOf(filtersRecord)).providerChangedIds)
        }

    private val providerDeletion = SyncRecord(SyncKey(SyncKind.SHARED, "prov-1", SyncKind.PROVIDER), hlc = 14, deleted = true)

    @Test
    fun `deleting this device's active provider is reported, so the UI follows the next one`() =
        runBlocking {
            coEvery { sync.providerByKey("prov-1") } returns provider.copy(isActive = true)

            val result = applier().apply(listOf(providerDeletion))

            coVerify { anyConstructed<ProviderRepository>().deleteProvider(provider.id, fromRemote = true) }
            assertEquals(setOf(provider.id), result.providerChangedIds)
            assertTrue(result.activeProviderDeleted)
        }

    @Test
    fun `deleting another provider is not an active-provider change`() =
        runBlocking {
            val result = applier().apply(listOf(providerDeletion))

            assertEquals(setOf(provider.id), result.providerChangedIds)
            assertFalse(result.activeProviderDeleted)
        }

    @Test
    fun `user data and settings records report no provider change`() =
        runBlocking {
            val setting =
                SyncRecord(
                    SyncKey(SyncKind.SHARED, "", SyncKind.SETTING, "unknown_key"),
                    hlc = 13,
                    payload = SyncPayloads.encode(SyncPayloads.Setting(kotlinx.serialization.json.JsonPrimitive("x"))),
                )

            assertEquals(emptySet<Long>(), applier().apply(listOf(setting)).providerChangedIds)
        }
}
