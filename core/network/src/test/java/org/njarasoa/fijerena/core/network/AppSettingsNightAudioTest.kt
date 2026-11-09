package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.player.audio.NightAudioSetting

/** Night Audio is one per-device value, never synced, and it stays on until turned off. */
class AppSettingsNightAudioTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `off until turned on, then it stays on`() {
        assertFalse(settings.nightAudio)
        settings.nightAudio = true
        assertTrue(settings.nightAudio)
    }

    @Test
    fun `not synced and not per profile`() {
        assertFalse(NightAudioSetting.KEY in AppSettings.SYNCED_SETTING_KEYS)
        assertFalse(NightAudioSetting.KEY in AppSettings.PER_PROFILE_SETTING_KEYS)
    }

    @Test
    fun `the player reads the same file and key`() {
        // The player module reads these directly: they must not drift from what AppSettings uses.
        assertTrue(NightAudioSetting.PREFS_NAME == "app_settings")
    }
}
