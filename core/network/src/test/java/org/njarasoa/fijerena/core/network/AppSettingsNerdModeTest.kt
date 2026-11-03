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

/** The Nerd flag is per profile, synced, and never on for a child — see the Nerd role plan. */
class AppSettingsNerdModeTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `off until turned on, and each profile has its own flag`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertFalse(settings.isNerdMode)

        settings.setNerdMode(OTHER, true)

        assertFalse(settings.isNerdMode)
        assertTrue(settings.nerdMode(OTHER))
        settings.activeProfileId = OTHER
        assertTrue(settings.isNerdMode)
    }

    @Test
    fun `Nerd and Admin are separate flags`() {
        settings.setNerdMode(OTHER, true)

        assertFalse(settings.devMode(OTHER))

        settings.setNerdMode(OTHER, false)
        settings.setDevMode(OTHER, true)

        assertFalse(settings.nerdMode(OTHER))
    }

    @Test
    fun `a child is never a Nerd`() {
        settings.setNerdMode(OTHER, true)

        settings.setChildProfile(OTHER, true)

        assertFalse(settings.nerdMode(OTHER))
    }

    @Test
    fun `the flag syncs out and in per profile`() {
        settings.setNerdMode(OTHER, true)

        assertTrue("nerd_mode" in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue("nerd_mode" in AppSettings.PER_PROFILE_SETTING_KEYS)
        assertEquals(JsonPrimitive(true), settings.syncedSetting("nerd_mode", OTHER))
        assertNull(settings.syncedSetting("nerd_mode", ProfileEntity.DEFAULT_ID))

        settings.applyRemoteSetting("nerd_mode", ProfileEntity.DEFAULT_ID, JsonPrimitive(true))

        assertTrue(settings.nerdMode(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `removing a profile's flag turns it off`() {
        settings.setNerdMode(OTHER, true)

        settings.removeNerdMode(OTHER)

        assertFalse(settings.nerdMode(OTHER))
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
