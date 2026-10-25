package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.provider.SettingsVersionEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A per-profile Jellyfin login from a device on an older build (JF-D2): the login travels with the
 * source now, so one received becomes the source's only while the source has none, and is then
 * sent on with the source. Nothing else changes the source's login.
 */
class SyncApplierOldLoginTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
    private val jellyfin =
        ProviderEntity(id = 7, name = "Jellyfin", url = "http://jf.test", username = "", type = "JELLYFIN", providerKey = "jf")

    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context =
        mockk<Context>(relaxed = true).also { context ->
            every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        }

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
        coEvery { sync.providerByKey("jf") } returns jellyfin
        coEvery { profiles.exists(PROFILE) } returns true
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any()) } just runs
        mockkObject(SettingsSyncQueue)
        every { SettingsSyncQueue.provider(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun applier() = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null })

    private fun oldLogin(
        username: String = "kilonga",
        deleted: Boolean = false,
    ) = SyncRecord(
        SyncKey(PROFILE, "jf", SyncKind.PROVIDER_LOGIN),
        hlc = 20,
        payload = if (deleted) null else SyncPayloads.encode(SyncPayloads.Login(username, "pw")),
        deleted = deleted,
    )

    @Test
    fun `an old login becomes the login of a source that has none, and is sent on with it`() =
        runBlocking {
            every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns false

            val result = applier().apply(listOf(oldLogin()))

            coVerify { anyConstructed<ProviderRepository>().applyRemoteLogin(jellyfin.id, SyncPayloads.Login("kilonga", "pw")) }
            verify { SettingsSyncQueue.provider(any(), jellyfin.id) }
            assertEquals(setOf(jellyfin.id), result.providerChangedIds)
        }

    @Test
    fun `an old login leaves a source that has one as it is`() =
        runBlocking {
            every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns true

            val result = applier().apply(listOf(oldLogin()))

            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any()) }
            verify(exactly = 0) { SettingsSyncQueue.provider(any(), any()) }
            assertEquals(1, result.applied)
            assertTrue(result.providerChangedIds.isEmpty())
            // Marked as seen, so it isn't looked at again.
            coVerify { sync.upsertVersion(SettingsVersionEntity(SyncKind.PROVIDER_LOGIN, PROFILE, "jf", 20, pending = false)) }
        }

    @Test
    fun `an old login's deletion never clears the source's login`() =
        runBlocking {
            every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns false

            val result = applier().apply(listOf(oldLogin(deleted = true)))

            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any()) }
            assertTrue(result.providerChangedIds.isEmpty())
        }

    @Test
    fun `an old login with no username is not a login`() =
        runBlocking {
            every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns false

            applier().apply(listOf(oldLogin(username = " ")))

            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any()) }
        }

    @Test
    fun `an old login for a source that isn't Jellyfin is ignored`() =
        runBlocking {
            coEvery { sync.providerByKey("jf") } returns jellyfin.copy(type = "XTREAM")
            every { anyConstructed<ProviderRepository>().hasLogin(any()) } returns false

            applier().apply(listOf(oldLogin()))

            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().applyRemoteLogin(any(), any()) }
        }

    private companion object {
        const val PROFILE = "kid"
    }
}
