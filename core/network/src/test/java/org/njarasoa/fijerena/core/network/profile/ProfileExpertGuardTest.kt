package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** There is always a profile with Expert mode (the admin, for now): the oldest gets it back when none has. */
class ProfileExpertGuardTest {
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
    fun `with no Expert the oldest profile becomes one`() =
        runBlocking {
            assertEquals(ProfileEntity.DEFAULT_ID, ProfileRepository(context).ensureAnExpert())

            val settings = AppSettings(context)
            assertTrue(settings.devMode(ProfileEntity.DEFAULT_ID))
            assertFalse(settings.devMode(KID))
        }

    @Test
    fun `an existing Expert is left alone`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setDevMode(KID, true)

            assertNull(ProfileRepository(context).ensureAnExpert())

            assertTrue(settings.devMode(KID))
            assertFalse(settings.devMode(ProfileEntity.DEFAULT_ID))
        }

    @Test
    fun `startup leaves an Expert when no profile has one`() =
        runBlocking {
            ProfileRepository(context).migrateLegacyProfileSettings()

            assertTrue(AppSettings(context).devMode(ProfileEntity.DEFAULT_ID))
        }

    private companion object {
        const val KID = "kid"
    }
}
