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
import org.junit.Assert.assertEquals
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

/** Text & grid size is this device's, like the language: a restore leaves it, an export leaves it out. See the profile rules plan → PR-10. */
class SettingsExportUiScaleTest {
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
        coEvery { profileDao.getAll() } returns listOf(ProfileEntity(ProfileEntity.DEFAULT_ID, "Me", 1L))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a restore leaves this device's text size`() {
        val settings = AppSettings(context)
        settings.uiScaleChoice = DEVICE_SCALE
        // An older file that still carries a text size.
        val file =
            SettingsExportManager.json.encodeToString(
                ExportedSettings.serializer(),
                ExportedSettings(global = GlobalSettings(uiScale = FILE_SCALE)),
            )
        val options = ImportOptions(importProviders = false, importEpgSources = false, importFavorites = false)

        assertNull(runBlocking { SettingsExportManager(context).importFromJson(file, options = options) }.error)

        assertEquals(DEVICE_SCALE, settings.uiScaleChoice)
    }

    @Test
    fun `a new file has no text size`() {
        val file = SettingsExportManager.json.encodeToString(ExportedSettings.serializer(), ExportedSettings(global = GlobalSettings()))

        assertFalse(file.contains("uiScale"))
    }

    private companion object {
        const val DEVICE_SCALE = 0.8f
        const val FILE_SCALE = 0.6f
    }
}
