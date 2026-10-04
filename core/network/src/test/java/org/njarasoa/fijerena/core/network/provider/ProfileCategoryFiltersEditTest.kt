package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * A profile's page edits that profile's content filters for the source in use; only the profile
 * this device uses has them applied straight away. See
 * docs/plans/20261003_sources-guide-profiles-plan.md → P9.
 */
class ProfileCategoryFiltersEditTest {
    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)
    private val dao = mockk<ProviderDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)

    // Jellyfin: applying its filters touches no Xtream catalogue flags.
    private val provider = ProviderEntity(id = PROVIDER_ID, name = "Jelly", url = "http://jelly.test", username = "me", type = "JELLYFIN")

    private val adult = CategoryFilters(rules = listOf(CategoryMatcher("Adult")))

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        every { settingsDb.providerDao() } returns dao
        mockkObject(SettingsDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        coEvery { dao.getProviderById(PROVIDER_ID) } returns provider
        mockkObject(MediaProviderFactory)
        every { MediaProviderFactory.providerChanged(any()) } just runs
        AppSettings(context).activeProfileId = ProfileEntity.DEFAULT_ID
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `another profile's filters are stored for it and leave the profile in use alone`() =
        runBlocking {
            val repo = ProviderRepository(context)
            assertEquals(CategoryFilters(), repo.getProviderSettings(PROVIDER_ID).categoryFilters)

            repo.setCategoryFilters(PROVIDER_ID, OTHER, adult)

            assertEquals(adult, repo.getCategoryFilters(PROVIDER_ID, OTHER))
            assertEquals(CategoryFilters(), repo.getCategoryFilters(PROVIDER_ID, ProfileEntity.DEFAULT_ID))
            assertEquals(CategoryFilters(), repo.getProviderSettings(PROVIDER_ID).categoryFilters)
            verify(exactly = 0) { MediaProviderFactory.providerChanged(any()) }
        }

    @Test
    fun `the profile in use gets its filters applied at once`() =
        runBlocking {
            val repo = ProviderRepository(context)
            assertEquals(CategoryFilters(), repo.getProviderSettings(PROVIDER_ID).categoryFilters)

            repo.setCategoryFilters(PROVIDER_ID, ProfileEntity.DEFAULT_ID, adult)

            assertEquals(adult, repo.getProviderSettings(PROVIDER_ID).categoryFilters)
            verify(exactly = 1) { MediaProviderFactory.providerChanged(PROVIDER_ID) }
        }

    @Test
    fun `after switching to the other profile its filters are the ones in use`() =
        runBlocking {
            val repo = ProviderRepository(context)
            repo.setCategoryFilters(PROVIDER_ID, OTHER, adult)

            AppSettings(context).activeProfileId = OTHER

            assertEquals(adult, repo.getProviderSettings(PROVIDER_ID).categoryFilters)
        }

    private companion object {
        const val PROVIDER_ID = 4343L
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
