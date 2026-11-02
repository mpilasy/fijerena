package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue

/** Search and EPG search history, and the phone's last tab, are per profile. */
class AppSettingsSearchHistoryTest {
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        prefs = FakeSharedPreferences()
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns prefs
        settings = AppSettings(context)
    }

    @Test
    fun `each profile has its own history`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.addSearchHistory("dune")
        settings.addEpgSearchHistory("news")

        settings.activeProfileId = OTHER
        assertEquals(emptyList<String>(), settings.getSearchHistory())
        assertEquals(emptyList<String>(), settings.getEpgSearchHistory())
        settings.addSearchHistory("bluey")
        settings.clearEpgSearchHistory()

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertEquals(listOf("dune"), settings.getSearchHistory())
        assertEquals(listOf("news"), settings.getEpgSearchHistory())
    }

    @Test
    fun `each profile has its own last tab`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.lastTab = "MOVIES"

        settings.activeProfileId = OTHER
        assertNull(settings.lastTab)
        settings.lastTab = "LIVE_TV"
        settings.removeLastTab(OTHER)
        assertNull(settings.lastTab)

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertEquals("MOVIES", settings.lastTab)
    }

    @Test
    fun `upgrade gives the install-wide history to the default profile only`() {
        prefs
            .edit()
            .putString("search_history", "dune\u001Falien")
            .putString("epg_search_history", "news")
            .commit()

        settings.moveLegacySearchHistoryToDefaultProfile()

        assertFalse(prefs.contains("search_history"))
        assertFalse(prefs.contains("epg_search_history"))
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertEquals(listOf("dune", "alien"), settings.getSearchHistory())
        assertEquals(listOf("news"), settings.getEpgSearchHistory())
        settings.activeProfileId = OTHER
        assertEquals(emptyList<String>(), settings.getSearchHistory())
    }

    @Test
    fun `upgrade keeps the default profile's own history`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.addSearchHistory("dune")
        prefs.edit().putString("search_history", "alien").commit()

        settings.moveLegacySearchHistoryToDefaultProfile()

        assertEquals(listOf("dune"), settings.getSearchHistory())
        assertFalse(prefs.contains("search_history"))
    }

    @Test
    fun `removing a profile drops its history`() {
        settings.activeProfileId = OTHER
        settings.addSearchHistory("bluey")
        settings.addEpgSearchHistory("cartoons")

        settings.removeProfileSearchHistory(OTHER)

        assertEquals(emptyList<String>(), settings.getSearchHistory())
        assertEquals(emptyList<String>(), settings.getEpgSearchHistory())
    }

    @Test
    fun `a profile made a child's loses the searches typed before`() {
        mockkObject(SettingsSyncQueue)
        try {
            every { SettingsSyncQueue.setting(any(), any(), any()) } just Runs
            settings.activeProfileId = OTHER
            settings.addSearchHistory("lust")
            settings.addEpgSearchHistory("late film")

            settings.setChildProfile(OTHER, true)
            assertEquals(emptyList<String>(), settings.getSearchHistory())
            assertEquals(emptyList<String>(), settings.getEpgSearchHistory())

            // A child's own searches stay while it stays a child.
            settings.addSearchHistory("bluey")
            settings.setChildProfile(OTHER, true)
            assertEquals(listOf("bluey"), settings.getSearchHistory())
        } finally {
            unmockkObject(SettingsSyncQueue)
        }
    }

    @Test
    fun `a profile made a child's on another device loses the searches typed before`() {
        settings.activeProfileId = OTHER
        settings.addSearchHistory("family guy")

        settings.applyRemoteSetting(AppSettings.KEY_CHILD_PROFILE, OTHER, JsonPrimitive(true))

        assertEquals(emptyList<String>(), settings.getSearchHistory())
    }

    @Test
    fun `upgrade gives no grown-up history to a default profile that is a child's`() {
        prefs
            .edit()
            .putBoolean("child_profile_${ProfileEntity.DEFAULT_ID}", true)
            .putString("search_history", "lust")
            .commit()

        settings.moveLegacySearchHistoryToDefaultProfile()

        assertFalse(prefs.contains("search_history"))
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertEquals(emptyList<String>(), settings.getSearchHistory())
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
