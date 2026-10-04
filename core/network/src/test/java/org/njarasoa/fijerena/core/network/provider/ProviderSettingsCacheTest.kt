package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/**
 * The app builds many ProviderRepository objects, and each used to cache settings for itself: a
 * category filter received through sync's instance left `AppContainer.providerRepository` serving
 * the old one until a restart. The cache is now one for the process. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 1.
 */
class ProviderSettingsCacheTest {
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
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `filters received through one instance reach another that had cached the old ones`() =
        runBlocking {
            val app = ProviderRepository(context)
            val sync = ProviderRepository(context)
            assertEquals(CategoryFilters(), app.getProviderSettings(PROVIDER_ID).categoryFilters)

            sync.applyRemoteCategoryFilters(PROVIDER_ID, ProfileEntity.DEFAULT_ID, adult)

            assertEquals(adult, app.getProviderSettings(PROVIDER_ID).categoryFilters)
        }

    private companion object {
        const val PROVIDER_ID = 4242L
    }
}
