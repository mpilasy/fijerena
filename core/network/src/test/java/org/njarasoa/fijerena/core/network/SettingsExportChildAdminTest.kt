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

/**
 * A backup restore never makes a kid Admin or Nerd: the file's flags go to a grown-up only. See the
 * profile rules plan → PR-8 and the Nerd role plan.
 */
class SettingsExportChildAdminTest {
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

    private fun adminFile() =
        SettingsExportManager.json.encodeToString(
            ExportedSettings.serializer(),
            ExportedSettings(global = GlobalSettings(isAdmin = true, isNerdMode = true)),
        )

    /** A file from an older build, which named the Admin flag `isDevMode`. */
    private fun olderAdminFile() = adminFile().replace("\"isAdmin\"", "\"isDevMode\"").also { assertTrue("isDevMode" in it) }

    private fun restore(file: String = adminFile()) =
        runBlocking {
            val options = ImportOptions(importProviders = false, importEpgSources = false, importFavorites = false)
            SettingsExportManager(context).importFromJson(file, options = options)
        }

    @Test
    fun `an Admin file restored on a kid leaves it without Admin`() {
        val settings = AppSettings(context)
        settings.setChildProfile(ProfileEntity.DEFAULT_ID, true)

        assertNull(restore().error)

        assertFalse(settings.admin(ProfileEntity.DEFAULT_ID))
        // The app still keeps an Admin: the oldest grown-up.
        assertTrue(settings.admin(OLDER))
    }

    @Test
    fun `an Admin file restored on a grown-up makes it Admin`() {
        assertNull(restore().error)

        assertTrue(AppSettings(context).admin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `an older file's isDevMode restored on a grown-up makes it Admin`() {
        assertNull(restore(olderAdminFile()).error)

        assertTrue(AppSettings(context).admin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `an older file's isDevMode restored on a kid leaves it without Admin`() {
        val settings = AppSettings(context)
        settings.setChildProfile(ProfileEntity.DEFAULT_ID, true)

        assertNull(restore(olderAdminFile()).error)

        assertFalse(settings.admin(ProfileEntity.DEFAULT_ID))
        assertTrue(settings.admin(OLDER))
    }

    @Test
    fun `the export writes the Admin flag as isAdmin`() {
        AppSettings(context).setAdmin(ProfileEntity.DEFAULT_ID, true)

        val file = runBlocking { SettingsExportManager(context).exportToJson() }

        assertTrue("\"isAdmin\": true" in file)
        assertFalse("isDevMode" in file)
    }

    @Test
    fun `a Nerd file restored on a kid leaves it without Nerd`() {
        val settings = AppSettings(context)
        settings.setChildProfile(ProfileEntity.DEFAULT_ID, true)

        assertNull(restore().error)

        assertFalse(settings.storedNerdMode(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a Nerd file restored on a grown-up makes it a Nerd`() {
        assertNull(restore().error)

        assertTrue(AppSettings(context).nerdMode(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `the export carries the Nerd flag next to the Admin flag`() {
        val settings = AppSettings(context)
        settings.setNerdMode(ProfileEntity.DEFAULT_ID, true)
        coEvery { anyConstructed<ProviderRepository>().getAllProvidersList() } returns emptyList()

        val file = runBlocking { SettingsExportManager(context).exportToJson() }

        assertTrue(
            SettingsExportManager.json
                .decodeFromString(ExportedSettings.serializer(), file)
                .global.isNerdMode,
        )
    }

    private companion object {
        const val OLDER = "older"
    }
}
