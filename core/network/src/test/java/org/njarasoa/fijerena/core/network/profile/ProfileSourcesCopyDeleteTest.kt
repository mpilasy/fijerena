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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** A new profile starts with the sources of the profile in use; a deleted one's go with it (multi-source plan, Phase 2). */
class ProfileSourcesCopyDeleteTest {
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
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a new profile starts with the sources of the profile in use`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setProfileSourceKeys(ProfileEntity.DEFAULT_ID, setOf("a", "b"))

            val id = ProfileRepository(context).addProfile("Kids", 1)

            assertEquals(setOf("a", "b"), settings.profileSourceKeys(id))
        }

    @Test
    fun `a new profile copies nothing when the profile in use has none stored`() =
        runBlocking {
            val id = ProfileRepository(context).addProfile("Kids", 1)

            assertNull(AppSettings(context).profileSourceKeys(id))
        }

    @Test
    fun `deleting a profile drops its sources only`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setProfileSourceKeys(ProfileEntity.DEFAULT_ID, setOf("a"))
            settings.setProfileSourceKeys(OTHER, setOf("b"))
            coEvery { profileDao.getAll() } returns
                listOf(ProfileEntity(ProfileEntity.DEFAULT_ID, "Me", 0L), ProfileEntity(OTHER, "Kids", 1L))

            assertEquals(ProfileRepository.DeleteBlocked.NONE, ProfileRepository(context).deleteProfile(OTHER))

            assertNull(settings.profileSourceKeys(OTHER))
            assertEquals(setOf("a"), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
        }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
