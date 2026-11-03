package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.provider.SettingsSyncDao
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A kid is never Admin, also when live sync brings the flags in: Admin is turned off on a
 * profile that is a child after the pass, whatever order the two flags came in, and the app still
 * keeps an Admin. Nerd likewise (the Nerd role plan). See the profile rules plan → PR-8.
 */
class SyncApplierChildAdminTest {
    private val sync = mockk<SettingsSyncDao>(relaxed = true)
    private val profiles = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)
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
        coEvery { profiles.exists(any()) } returns true
        coEvery { profiles.getAll() } returns listOf(ProfileEntity(ProfileEntity.DEFAULT_ID, "Me", 1L), ProfileEntity(KID, "Kid", 2L))
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().childProfileChanged(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun applier() = SyncApplier(context, thisDeviceId = { "this-device" }, playingSessionId = { null })

    private fun setting(
        key: String,
        profileId: String,
        on: Boolean,
        hlc: Long,
    ) = SyncRecord(
        SyncKey(profileId, "", SyncKind.SETTING, key),
        hlc = hlc,
        payload = SyncPayloads.encode(SyncPayloads.Setting(JsonPrimitive(on))),
    )

    private val settings get() = AppSettings(context)

    @Test
    fun `child and Admin in one pass leave the kid without Admin, child first`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)

            applier().apply(listOf(setting(AppSettings.KEY_CHILD_PROFILE, KID, true, 10), setting(ADMIN, KID, true, 11)))

            assertTrue(settings.isChildProfile(KID))
            assertFalse(settings.admin(KID))
            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `child and Admin in one pass leave the kid without Admin, Admin first`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)

            applier().apply(listOf(setting(ADMIN, KID, true, 10), setting(AppSettings.KEY_CHILD_PROFILE, KID, true, 11)))

            assertTrue(settings.isChildProfile(KID))
            assertFalse(settings.admin(KID))
        }

    @Test
    fun `Admin alone on a kid is turned off, and the change is queued for sync`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)
            settings.setChildProfile(KID, true)

            applier().apply(listOf(setting(ADMIN, KID, true, 10)))

            assertFalse(settings.admin(KID))
            // So every device ends with Admin off, not only this one.
            coVerify(timeout = 2_000) { sync.queue(SyncKind.SETTING, KID, ADMIN) }
        }

    @Test
    fun `child alone on the only Admin turns Admin off and the oldest grown-up gets it`() =
        runBlocking {
            settings.setAdmin(KID, true)

            applier().apply(listOf(setting(AppSettings.KEY_CHILD_PROFILE, KID, true, 10)))

            assertFalse(settings.admin(KID))
            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `Admin on a grown-up is left alone`() =
        runBlocking {
            settings.setChildProfile(KID, true)

            applier().apply(listOf(setting(ADMIN, ProfileEntity.DEFAULT_ID, true, 10)))

            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
            assertFalse(settings.admin(KID))
        }

    @Test
    fun `a dev_mode record from an older build still sets the Admin flag`() =
        runBlocking {
            applier().apply(listOf(setting(LEGACY_DEV_MODE, ProfileEntity.DEFAULT_ID, true, 10)))

            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `a dev_mode record from an older build on a kid is turned off as admin`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)
            settings.setChildProfile(KID, true)

            applier().apply(listOf(setting(LEGACY_DEV_MODE, KID, true, 10)))

            assertFalse(settings.admin(KID))
            coVerify(timeout = 2_000) { sync.queue(SyncKind.SETTING, KID, ADMIN) }
        }

    @Test
    fun `child and Nerd in one pass leave the kid without Nerd, in either order`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)

            applier().apply(listOf(setting(NERD_MODE, KID, true, 10), setting(AppSettings.KEY_CHILD_PROFILE, KID, true, 11)))

            assertTrue(settings.isChildProfile(KID))
            assertFalse(settings.storedNerdMode(KID))
            // So every device ends with Nerd off, not only this one.
            coVerify(timeout = 2_000) { sync.queue(SyncKind.SETTING, KID, NERD_MODE) }
        }

    @Test
    fun `Nerd alone on a kid is turned off, and a grown-up keeps it`() =
        runBlocking {
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)
            settings.setChildProfile(KID, true)

            applier().apply(listOf(setting(NERD_MODE, KID, true, 10), setting(NERD_MODE, ProfileEntity.DEFAULT_ID, true, 11)))

            assertFalse(settings.storedNerdMode(KID))
            assertTrue(settings.nerdMode(ProfileEntity.DEFAULT_ID))
        }

    private companion object {
        const val KID = "kid"

        // AppSettings.KEY_NERD_MODE, which is private.
        const val NERD_MODE = "nerd_mode"

        // AppSettings.KEY_ADMIN, which is private.
        const val ADMIN = "admin"

        // The Admin flag's old name, which an older build still sends.
        const val LEGACY_DEV_MODE = "dev_mode"
    }
}
