package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** Developer mode is per profile — see docs/plans/archive/20260930_profile-scoped-settings-plan.md. */
class AppSettingsDevModeTest {
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
    fun `each profile has its own flag`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.isDevMode = true

        settings.activeProfileId = OTHER
        assertFalse(settings.isDevMode)

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertTrue(settings.isDevMode)
    }

    @Test
    fun `upgrade copies the install-wide flag to every profile, and later profiles start off`() {
        prefs.edit().putBoolean("dev_mode", true).commit()

        settings.copyLegacyDevModeToProfiles(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertFalse(prefs.contains("dev_mode"))
        listOf(ProfileEntity.DEFAULT_ID, OTHER).forEach {
            settings.activeProfileId = it
            assertTrue(settings.isDevMode)
        }
        settings.activeProfileId = "added-after-upgrade"
        assertFalse(settings.isDevMode)
    }

    @Test
    fun `before the upgrade has run, the install-wide flag still applies`() {
        prefs.edit().putBoolean("dev_mode", true).commit()

        settings.activeProfileId = OTHER
        assertTrue(settings.isDevMode)
    }

    @Test
    fun `upgrade keeps a profile's own flag`() {
        prefs.edit().putBoolean("dev_mode", true).commit()
        settings.activeProfileId = OTHER
        settings.isDevMode = false

        settings.copyLegacyDevModeToProfiles(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertFalse(settings.isDevMode)
    }

    @Test
    fun `removing a profile's flag turns it off`() {
        settings.activeProfileId = OTHER
        settings.isDevMode = true

        settings.removeDevMode(OTHER)

        assertFalse(settings.isDevMode)
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
