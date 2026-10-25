package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.SettingsExportManager.ExportedSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager.GlobalSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager.ImportOptions
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/** A backup restore never makes a kid Expert: the file's Expert mode goes to a grown-up only. See the profile rules plan → PR-8. */
class SettingsExportChildExpertTest {
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
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().getAllProvidersList() } returns emptyList()
        coEvery { profileDao.getAll() } returns listOf(ProfileEntity(OLDER, "Me", 1L), ProfileEntity(ProfileEntity.DEFAULT_ID, "Kid", 2L))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun restore() =
        runBlocking {
            val file =
                SettingsExportManager.json.encodeToString(
                    ExportedSettings.serializer(),
                    ExportedSettings(global = GlobalSettings(isDevMode = true)),
                )
            val options = ImportOptions(importProviders = false, importEpgSources = false, importFavorites = false)
            SettingsExportManager(context).importFromJson(file, options = options)
        }

    @Test
    fun `an Expert file restored on a kid leaves it without Expert`() {
        val settings = AppSettings(context)
        settings.setChildProfile(ProfileEntity.DEFAULT_ID, true)

        assertNull(restore().error)

        assertFalse(settings.devMode(ProfileEntity.DEFAULT_ID))
        // The app still keeps an Expert: the oldest grown-up.
        assertTrue(settings.devMode(OLDER))
    }

    @Test
    fun `an Expert file restored on a grown-up makes it Expert`() {
        assertNull(restore().error)

        assertTrue(AppSettings(context).devMode(ProfileEntity.DEFAULT_ID))
    }

    private companion object {
        const val OLDER = "older"
    }
}
