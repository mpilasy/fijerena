package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** Admin is per profile — see the profile scoped settings plan. */
class AppSettingsAdminTest {
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
        settings.isAdmin = true

        settings.activeProfileId = OTHER
        assertFalse(settings.isAdmin)

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertTrue(settings.isAdmin)
    }

    @Test
    fun `setting another profile's flag leaves the active one's alone`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID

        settings.setAdmin(OTHER, true)

        assertFalse(settings.isAdmin)
        assertTrue(settings.admin(OTHER))
        settings.activeProfileId = OTHER
        assertTrue(settings.isAdmin)
    }

    @Test
    fun `the active profile's flag is the same through either accessor`() {
        settings.activeProfileId = OTHER
        settings.isAdmin = true

        assertTrue(settings.admin(OTHER))
        assertFalse(settings.admin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `before the upgrade has run, another profile also falls back to the install-wide flag`() {
        prefs.edit().putBoolean("dev_mode", true).commit()

        assertTrue(settings.admin(OTHER))
    }

    @Test
    fun `upgrade copies the install-wide flag to every profile, and later profiles start off`() {
        prefs.edit().putBoolean("dev_mode", true).commit()

        settings.upgradeAdminKeys(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertFalse(prefs.contains("dev_mode"))
        listOf(ProfileEntity.DEFAULT_ID, OTHER).forEach {
            settings.activeProfileId = it
            assertTrue(settings.isAdmin)
        }
        settings.activeProfileId = "added-after-upgrade"
        assertFalse(settings.isAdmin)
    }

    @Test
    fun `before the upgrade has run, the install-wide flag still applies`() {
        prefs.edit().putBoolean("dev_mode", true).commit()

        settings.activeProfileId = OTHER
        assertTrue(settings.isAdmin)
    }

    @Test
    fun `upgrade keeps a profile's own flag`() {
        prefs.edit().putBoolean("dev_mode", true).commit()
        settings.activeProfileId = OTHER
        settings.isAdmin = false

        settings.upgradeAdminKeys(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertFalse(settings.isAdmin)
    }

    @Test
    fun `the flag is stored as admin_ and the profile id`() {
        settings.setAdmin(OTHER, true)

        assertTrue(prefs.getBoolean("admin_$OTHER", false))
        assertFalse(prefs.contains("dev_mode_$OTHER"))
    }

    @Test
    fun `before the upgrade has run, a profile's old key applies, over the install-wide flag`() {
        prefs
            .edit()
            .putBoolean("dev_mode", true)
            .putBoolean("dev_mode_$OTHER", false)
            .putBoolean("dev_mode_${ProfileEntity.DEFAULT_ID}", true)
            .commit()

        assertFalse(settings.admin(OTHER))
        assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `upgrade copies each profile's old key to its admin key and removes the old keys`() {
        prefs
            .edit()
            .putBoolean("dev_mode_${ProfileEntity.DEFAULT_ID}", true)
            .putBoolean("dev_mode_$OTHER", false)
            .commit()

        settings.upgradeAdminKeys(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertTrue(prefs.getBoolean("admin_${ProfileEntity.DEFAULT_ID}", false))
        assertTrue(prefs.contains("admin_$OTHER"))
        assertFalse(prefs.getBoolean("admin_$OTHER", true))
        assertFalse(prefs.contains("dev_mode_${ProfileEntity.DEFAULT_ID}"))
        assertFalse(prefs.contains("dev_mode_$OTHER"))
        assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        assertFalse(settings.admin(OTHER))
    }

    @Test
    fun `upgrade keeps an admin key that is already there`() {
        prefs
            .edit()
            .putBoolean("admin_$OTHER", true)
            .putBoolean("dev_mode_$OTHER", false)
            .commit()

        settings.upgradeAdminKeys(listOf(OTHER))

        assertTrue(settings.admin(OTHER))
        assertFalse(prefs.contains("dev_mode_$OTHER"))
    }

    @Test
    fun `upgrade gives the install-wide flag only to profiles without an old key of their own`() {
        prefs
            .edit()
            .putBoolean("dev_mode", true)
            .putBoolean("dev_mode_$OTHER", false)
            .commit()

        settings.upgradeAdminKeys(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertTrue(settings.admin(ProfileEntity.DEFAULT_ID))
        assertFalse(settings.admin(OTHER))
        assertFalse(prefs.contains("dev_mode"))
        assertFalse(prefs.contains("dev_mode_$OTHER"))
    }

    @Test
    fun `upgrade with no old key changes nothing`() {
        settings.setAdmin(OTHER, true)

        settings.upgradeAdminKeys(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertTrue(settings.admin(OTHER))
        assertFalse(settings.admin(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a dev_mode record from an older build still sets the Admin flag`() {
        settings.applyRemoteSetting("dev_mode", OTHER, JsonPrimitive(true))

        assertTrue(settings.admin(OTHER))
        assertTrue(prefs.getBoolean("admin_$OTHER", false))

        settings.applyRemoteSetting("admin", OTHER, JsonPrimitive(false))

        assertFalse(settings.admin(OTHER))
    }

    @Test
    fun `live sync sends admin, not dev_mode`() {
        assertTrue("admin" in AppSettings.SYNCED_SETTING_KEYS)
        assertTrue("admin" in AppSettings.PER_PROFILE_SETTING_KEYS)
        assertFalse("dev_mode" in AppSettings.SYNCED_SETTING_KEYS)
        settings.setAdmin(OTHER, true)

        assertEquals(JsonPrimitive(true), settings.syncedSetting("admin", OTHER))
    }

    @Test
    fun `removing a profile's flag turns it off`() {
        settings.activeProfileId = OTHER
        settings.isAdmin = true

        prefs.edit().putBoolean("dev_mode_$OTHER", true).commit()

        settings.removeAdmin(OTHER)

        assertFalse(settings.isAdmin)
        assertFalse(prefs.contains("dev_mode_$OTHER"))
    }

    private companion object {
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
