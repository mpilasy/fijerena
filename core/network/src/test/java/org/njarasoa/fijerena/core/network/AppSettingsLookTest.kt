package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SyncKind

/** The colour theme and the Look and Feel are per profile and synced, like developer mode. */
class AppSettingsLookTest {
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
    fun `defaults until set`() {
        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId)
        assertEquals(AppSettings.DEFAULT_UI_STYLE_ID, settings.uiStyleId)
        assertNull(settings.syncedSetting(THEME, ProfileEntity.DEFAULT_ID))
        assertNull(settings.syncedSetting(STYLE, ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `each profile has its own`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.themeId = "daylight"
        settings.uiStyleId = "roku"

        settings.activeProfileId = OTHER
        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId)
        assertEquals(AppSettings.DEFAULT_UI_STYLE_ID, settings.uiStyleId)

        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        assertEquals("daylight", settings.themeId)
        assertEquals("roku", settings.uiStyleId)
    }

    @Test
    fun `setting another profile's leaves the active one's alone`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID

        settings.setThemeId(OTHER, "forest")
        settings.setUiStyleId(OTHER, "cinema")

        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId)
        assertEquals("forest", settings.themeId(OTHER))
        assertEquals("cinema", settings.uiStyleId(OTHER))
        settings.activeProfileId = OTHER
        assertEquals("forest", settings.themeId)
        assertEquals("cinema", settings.uiStyleId)
    }

    @Test
    fun `a new profile starts with its maker's look`() {
        settings.activeProfileId = OTHER
        settings.themeId = "aurora"
        settings.uiStyleId = "cupertino"

        settings.copyLook(OTHER, NEW)

        assertEquals("aurora", settings.themeId(NEW))
        assertEquals("cupertino", settings.uiStyleId(NEW))
        settings.themeId = "amethyst"
        assertEquals("aurora", settings.themeId(NEW))
    }

    @Test
    fun `before the upgrade has run, the device-wide look still applies`() {
        prefs
            .edit()
            .putString(THEME, "daylight")
            .putString(STYLE, "bravia")
            .commit()

        assertEquals("daylight", settings.themeId(OTHER))
        assertEquals("bravia", settings.uiStyleId(OTHER))
    }

    @Test
    fun `upgrade copies the device-wide look to every profile and drops it`() {
        prefs
            .edit()
            .putString(THEME, "daylight")
            .putString(STYLE, "bravia")
            .commit()

        settings.copyLegacyLookToProfiles(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertFalse(prefs.contains(THEME))
        assertFalse(prefs.contains(STYLE))
        listOf(ProfileEntity.DEFAULT_ID, OTHER).forEach {
            assertEquals("daylight", settings.themeId(it))
            assertEquals("bravia", settings.uiStyleId(it))
        }
        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId("added-after-upgrade"))
    }

    @Test
    fun `upgrade keeps a profile's own look`() {
        prefs.edit().putString(THEME, "daylight").commit()
        settings.setThemeId(OTHER, "forest")

        settings.copyLegacyLookToProfiles(listOf(ProfileEntity.DEFAULT_ID, OTHER))

        assertEquals("forest", settings.themeId(OTHER))
        assertEquals("daylight", settings.themeId(ProfileEntity.DEFAULT_ID))
        assertEquals(AppSettings.DEFAULT_UI_STYLE_ID, settings.uiStyleId(OTHER))
    }

    @Test
    fun `it syncs per profile`() {
        assertTrue(THEME in AppSettings.SYNCED_SETTING_KEYS && THEME in AppSettings.PER_PROFILE_SETTING_KEYS)
        assertTrue(STYLE in AppSettings.SYNCED_SETTING_KEYS && STYLE in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.setThemeId(OTHER, "forest")
        settings.setUiStyleId(OTHER, "cinema")

        val otherDevice =
            AppSettings(mockk<Context> { every { getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences() })
        otherDevice.applyRemoteSetting(THEME, OTHER, settings.syncedSetting(THEME, OTHER)!!)
        otherDevice.applyRemoteSetting(STYLE, OTHER, settings.syncedSetting(STYLE, OTHER)!!)

        assertEquals("forest", otherDevice.themeId(OTHER))
        assertEquals("cinema", otherDevice.uiStyleId(OTHER))
        assertEquals(AppSettings.DEFAULT_THEME_ID, otherDevice.themeId(ProfileEntity.DEFAULT_ID))
        assertEquals(JsonPrimitive("forest"), settings.syncedSetting(THEME, OTHER))
    }

    @Test
    fun `a device-wide theme from an older build is dropped`() {
        settings.applyRemoteSetting(THEME, SyncKind.SHARED, JsonPrimitive("daylight"))

        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId)
    }

    @Test
    fun `observing gives the active profile's look`() =
        runBlocking {
            settings.setThemeId(OTHER, "forest")
            settings.activeProfileId = OTHER

            assertEquals(ProfileLook("forest", AppSettings.DEFAULT_UI_STYLE_ID, true), settings.observeLook().first())
        }

    @Test
    fun `removing a profile's look drops it`() {
        settings.setThemeId(OTHER, "forest")
        settings.setUiStyleId(OTHER, "cinema")

        settings.removeLook(OTHER)

        assertEquals(AppSettings.DEFAULT_THEME_ID, settings.themeId(OTHER))
        assertEquals(AppSettings.DEFAULT_UI_STYLE_ID, settings.uiStyleId(OTHER))
    }

    @Test
    fun `the poster background is on until turned off, per profile`() {
        assertTrue(settings.posterBackdrop(OTHER))

        settings.setPosterBackdrop(OTHER, false)

        assertFalse(settings.posterBackdrop(OTHER))
        assertTrue(settings.posterBackdrop(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `the UI theme and the poster background sync per profile`() {
        assertTrue(UI_THEME in AppSettings.SYNCED_SETTING_KEYS && UI_THEME in AppSettings.PER_PROFILE_SETTING_KEYS)
        assertTrue(POSTER in AppSettings.SYNCED_SETTING_KEYS && POSTER in AppSettings.PER_PROFILE_SETTING_KEYS)
        settings.setPosterBackdrop(OTHER, false)
        settings.setUiThemeId(OTHER, "meadow")

        val otherDevice =
            AppSettings(mockk<Context> { every { getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences() })
        otherDevice.applyRemoteSetting(POSTER, OTHER, settings.syncedSetting(POSTER, OTHER)!!)
        otherDevice.applyRemoteSetting(UI_THEME, OTHER, settings.syncedSetting(UI_THEME, OTHER)!!)

        assertFalse(otherDevice.posterBackdrop(OTHER))
        assertEquals("meadow", otherDevice.uiThemeId(OTHER))
        assertTrue(otherDevice.posterBackdrop(ProfileEntity.DEFAULT_ID))
        assertEquals(UiThemes.CUSTOM, otherDevice.uiThemeId(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a UI theme this build does not know leaves the profile's own look`() {
        settings.setThemeId(OTHER, "forest")
        settings.setPosterBackdrop(OTHER, false)

        settings.setUiThemeId(OTHER, "from-a-newer-build")

        assertEquals(ProfileLook("forest", AppSettings.DEFAULT_UI_STYLE_ID, false), settings.look(OTHER))
    }

    @Test
    fun `a new profile also gets its maker's UI theme and poster background`() {
        settings.setPosterBackdrop(OTHER, false)
        settings.setUiThemeId(OTHER, "meadow")

        settings.copyLook(OTHER, NEW)

        assertFalse(settings.posterBackdrop(NEW))
        assertEquals("meadow", settings.uiThemeId(NEW))
    }

    @Test
    fun `removing a profile's look drops its UI theme and poster background too`() {
        settings.setPosterBackdrop(OTHER, false)
        settings.setUiThemeId(OTHER, "meadow")

        settings.removeLook(OTHER)

        assertTrue(settings.posterBackdrop(OTHER))
        assertEquals(UiThemes.CUSTOM, settings.uiThemeId(OTHER))
    }

    private companion object {
        const val THEME = "theme_id"
        const val STYLE = "ui_style_id"
        const val UI_THEME = "ui_theme_id"
        const val POSTER = "poster_backdrop"
        const val OTHER = "5d0e8c1a-other-profile"
        const val NEW = "9f1b2c3d-new-profile"
    }
}
