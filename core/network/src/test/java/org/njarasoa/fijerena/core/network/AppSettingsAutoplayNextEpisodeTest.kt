package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** "Play next episode automatically" is per profile and synced, like developer mode. */
class AppSettingsAutoplayNextEpisodeTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `off until turned on`() {
        assertFalse(settings.autoplayNextEpisode)
        assertNull(settings.syncedSetting(KEY, ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `each profile has its own`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.autoplayNextEpisode = true

        settings.activeProfileId = OTHER
        assertFalse(settings.autoplayNextEpisode)

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertTrue(settings.autoplayNextEpisode)
    }

    @Test
    fun `setting another profile's choice leaves the active one's alone`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID

        settings.setAutoplayNextEpisode(OTHER, true)

        assertFalse(settings.autoplayNextEpisode)
        assertTrue(settings.autoplayNextEpisode(OTHER))
        assertEquals(JsonPrimitive(true), settings.syncedSetting(KEY, OTHER))
        assertNull(settings.syncedSetting(KEY, ProfileEntity.DEFAULT_ID))
        settings.activeProfileId = OTHER
        assertTrue(settings.autoplayNextEpisode)
    }

    @Test
    fun `it syncs per profile`() {
        assertTrue(KEY in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue(KEY in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.activeProfileId = OTHER
        settings.autoplayNextEpisode = true

        assertEquals(JsonPrimitive(true), settings.syncedSetting(KEY, OTHER))
        assertNull(settings.syncedSetting(KEY, ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a sent value applied on another device lands on the same profile`() {
        settings.activeProfileId = OTHER
        settings.autoplayNextEpisode = true
        val sent = settings.syncedSetting(KEY, OTHER)!!

        val otherDevice = freshSettings()
        otherDevice.applyRemoteSetting(KEY, OTHER, sent)

        otherDevice.activeProfileId = OTHER
        assertTrue(otherDevice.autoplayNextEpisode)
        otherDevice.activeProfileId = ProfileEntity.DEFAULT_ID
        assertFalse(otherDevice.autoplayNextEpisode)
    }

    @Test
    fun `a received non-boolean is ignored`() {
        settings.applyRemoteSetting(KEY, OTHER, JsonPrimitive(42))

        settings.activeProfileId = OTHER
        assertFalse(settings.autoplayNextEpisode)
    }

    @Test
    fun `removing a profile's choice turns it off`() {
        settings.activeProfileId = OTHER
        settings.autoplayNextEpisode = true

        settings.removeAutoplayNextEpisode(OTHER)

        assertFalse(settings.autoplayNextEpisode)
    }

    private fun freshSettings(): AppSettings {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        return AppSettings(context)
    }

    private companion object {
        const val KEY = "autoplay_next_episode"
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
