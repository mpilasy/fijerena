package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** Search and EPG search history are per profile. */
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
    fun `upgrade gives the install-wide history to the default profile only`() {
        prefs.edit().putString("search_history", "dune\u001Falien").putString("epg_search_history", "news").commit()

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

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
