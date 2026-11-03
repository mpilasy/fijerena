package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** There is always an Admin profile: the oldest gets it back when none has. */
class ProfileAdminGuardTest {
    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        every { settingsDb.profileDao() } returns profileDao
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        // Kid is newer than Me, whatever order the list comes in.
        coEvery { profileDao.getAll() } returns listOf(ProfileEntity(KID, "Kid", 2L), ProfileEntity(ProfileEntity.DEFAULT_ID, "Me", 1L))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `with no Admin the oldest profile becomes one`() =
        runBlocking {
            assertEquals(ProfileEntity.DEFAULT_ID, ProfileRepository(context).ensureAnAdmin())

            val settings = AppSettings(context)
            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
            assertFalse(settings.admin(KID))
        }

    @Test
    fun `an existing Admin is left alone`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setAdmin(KID, true)

            assertNull(ProfileRepository(context).ensureAnAdmin())

            assertTrue(settings.admin(KID))
            assertFalse(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `startup leaves an Admin when no profile has one`() =
        runBlocking {
            ProfileRepository(context).migrateLegacyProfileSettings()

            assertTrue(AppSettings(context).admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `a child profile that is an Admin loses it`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setAdmin(KID, true)
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)
            settings.setChildProfile(KID, true)

            assertEquals(listOf(KID), ProfileRepository(context).clearChildAdmins())

            assertFalse(settings.admin(KID))
            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `a child profile loses Nerd, a grown-up keeps it`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)
            settings.setNerdMode(ProfileEntity.DEFAULT_ID, true)
            settings.setNerdMode(KID, true)
            settings.setChildProfile(KID, true)

            ProfileRepository(context).clearChildAdmins()

            assertFalse(settings.storedNerdMode(KID))
            assertTrue(settings.nerdMode(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `turning Child on turns Nerd off`() =
        runBlocking {
            mockkConstructor(ProviderRepository::class)
            coEvery { anyConstructed<ProviderRepository>().childProfileChanged(any()) } returns Unit
            val settings = AppSettings(context)
            settings.setNerdMode(KID, true)

            ProfileRepository(context).setChildProfile(KID, false)
            assertTrue(settings.nerdMode(KID))
            ProfileRepository(context).setChildProfile(KID, true)

            assertFalse(settings.storedNerdMode(KID))
            // Child off again gives nothing back.
            ProfileRepository(context).setChildProfile(KID, false)
            assertFalse(settings.nerdMode(KID))
        }

    @Test
    fun `a grown-up stays an Admin`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setAdmin(ProfileEntity.DEFAULT_ID, true)

            assertEquals(emptyList<String>(), ProfileRepository(context).clearChildAdmins())

            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `startup turns Admin off on a child and keeps an Admin`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setAdmin(KID, true)
            settings.setChildProfile(KID, true)

            ProfileRepository(context).migrateLegacyProfileSettings()

            assertFalse(settings.admin(KID))
            assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        }

    private companion object {
        const val KID = "kid"
    }
}
