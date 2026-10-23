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
import org.njarasoa.fijerena.core.network.provider.ParentalList
import org.njarasoa.fijerena.core.network.sync.SyncKind

/** The Parental controls lists: filled once, shared and synced (docs/plans/20261020_parental-controls-plan.md). */
class AppSettingsParentalListsTest {
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
    }

    @Test
    fun `the lists are filled with the app's words once`() {
        assertTrue(settings.seedParentalListsOnce())
        assertFalse(settings.seedParentalListsOnce())
        ParentalList.entries.forEach { assertEquals(it.defaults, settings.parentalList(it)) }
    }

    @Test
    fun `a list from another device replaces the one here and is what this device sends`() {
        settings.seedParentalListsOnce()
        val key = ParentalList.EXCEPTIONS.key

        settings.applyRemoteSetting(key, SyncKind.SHARED, JsonPrimitive("""["toy story"]"""))

        assertEquals(listOf("toy story"), settings.parentalList(ParentalList.EXCEPTIONS))
        assertTrue(settings.parentalRules().allowsTitle("Toy Story 4"))
        assertFalse(settings.parentalRules().allowsTitle("Naruto"))
        assertEquals(JsonPrimitive("""["toy story"]"""), settings.syncedSetting(key, SyncKind.SHARED))
        assertTrue(key in AppSettings.SYNCED_SETTING_KEYS)
        assertFalse(key in AppSettings.PER_PROFILE_SETTING_KEYS)
    }

    @Test
    fun `a list that can't be read gives the app's words`() {
        settings.applyRemoteSetting(ParentalList.KIDS_CATEGORIES.key, SyncKind.SHARED, JsonPrimitive("not json"))

        assertEquals(ParentalList.KIDS_CATEGORIES.defaults, settings.parentalList(ParentalList.KIDS_CATEGORIES))
    }
}
