package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences

/**
 * "Items in Recent" (per device), and the one-time move of the per-source sizes it replaces (the settings reorg plan, D2/D4).
 */
class AppSettingsPerSourcePlaybackTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `Recent shows 25 until changed`() {
        assertEquals(25, settings.recentRowSize)
    }

    @Test
    fun `Recent keeps the largest size any source had`() {
        settings.movePerSourcePlaybackOnce(listOf(10, 50, 25))

        assertEquals(50, settings.recentRowSize)
    }

    @Test
    fun `the move runs once and never overrides a choice made since`() {
        settings.recentRowSize = 15

        settings.movePerSourcePlaybackOnce(listOf(100))
        assertEquals(15, settings.recentRowSize)
    }

    @Test
    fun `resuming is no longer a synced setting`() {
        assertFalse("resume_playback" in AppSettings.SYNCED_SETTING_KEYS)
    }
}
