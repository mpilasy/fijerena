package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * A new source is on for the profile that added it only, and the upgrade seeds profiles before the
 * first add (multi-source plan, Phase 2).
 */
class ProfileSourcesRepositoryTest {
    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)
    private val dao = mockk<ProviderDao>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        every { settingsDb.providerDao() } returns dao
        every { settingsDb.profileDao() } returns profileDao
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion, EpgIndexDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
        // A Jellyfin server with no Default-profile login to store keeps the encrypted credential
        // store out of these tests.
        coEvery { profileDao.exists(ProfileEntity.DEFAULT_ID) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a new source is on for the profile that added it only`() =
        runBlocking {
            val settings = AppSettings(context)
            settings.setProfileSourceKeys(ProfileEntity.DEFAULT_ID, setOf("old"))
            settings.setProfileSourceKeys(OTHER, setOf("old"))
            val inserted = slot<ProviderEntity>()
            coEvery { dao.insertProvider(capture(inserted)) } returns 9L

            ProviderRepository(context).addProvider("New", "http://new.test", "me", "pw", type = "JELLYFIN")

            assertEquals(setOf("old", inserted.captured.providerKey), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
            assertEquals(setOf("old"), settings.profileSourceKeys(OTHER))
        }

    @Test
    fun `adding a source before the upgrade ran seeds profiles first`() =
        runBlocking {
            val settings = AppSettings(context)
            val existing = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", providerKey = "a")
            coEvery { dao.getAllProvidersList() } returns listOf(existing)
            // The source an older build had the device on.
            coEvery { dao.legacyActiveProviderKey() } returns "a"
            coEvery { profileDao.getAll() } returns
                listOf(ProfileEntity(ProfileEntity.DEFAULT_ID, "Me", 0L), ProfileEntity(OTHER, "Kids", 1L))
            val inserted = slot<ProviderEntity>()
            coEvery { dao.insertProvider(capture(inserted)) } returns 9L

            ProviderRepository(context).addProvider("New", "http://new.test", "me", "pw", type = "JELLYFIN")

            // Both had the device's current source; only the profile in use gets the new one.
            assertEquals(setOf("a", inserted.captured.providerKey), settings.profileSourceKeys(ProfileEntity.DEFAULT_ID))
            assertEquals(setOf("a"), settings.profileSourceKeys(OTHER))
        }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
