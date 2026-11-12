package org.njarasoa.fijerena.core.network

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.sync.SettingsSyncQueue
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.player.domain.TrackLanguagePreference

/** A profile's track languages are per profile and synced, like Play Next Episode Automatically. */
class AppSettingsTrackLanguageTest {
    private lateinit var context: Context
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        context = mockk()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        settings = AppSettings(context)
        mockkObject(SettingsSyncQueue)
        every { SettingsSyncQueue.setting(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(SettingsSyncQueue)
    }

    @Test
    fun `no setting until set`() {
        assertEquals(TrackLanguagePreference(), settings.trackLanguages)
        assertTrue(settings.trackLanguages.subtitlesOtherAudioOnly)
        KEYS.forEach { assertNull(it, settings.syncedSetting(it, ProfileEntity.DEFAULT_ID)) }
    }

    @Test
    fun `the three keys sync per profile`() {
        KEYS.forEach {
            assertTrue(it, it in AppSettings.SYNCED_SETTING_KEYS)
            assertTrue(it, it in AppSettings.PER_PROFILE_SETTING_KEYS)
        }
    }

    @Test
    fun `each profile has its own`() {
        settings.activeProfileId = ProfileEntity.DEFAULT_ID
        settings.setAudioLanguage(OTHER, "fr")
        settings.setSubtitleLanguage(OTHER, "en")
        settings.setSubtitlesOtherAudioOnly(OTHER, false)

        assertEquals(TrackLanguagePreference(), settings.trackLanguages)
        assertEquals(TrackLanguagePreference("fr", "en", false), settings.trackLanguages(OTHER))
        settings.activeProfileId = OTHER
        assertEquals(TrackLanguagePreference("fr", "en", false), settings.trackLanguages)
    }

    @Test
    fun `a change queues its record for the profile`() {
        settings.setAudioLanguage(OTHER, "fr")
        settings.setSubtitleLanguage(OTHER, TrackLanguagePreference.SUBTITLES_OFF)
        settings.setSubtitlesOtherAudioOnly(OTHER, false)

        verify { SettingsSyncQueue.setting(context, AUDIO, OTHER) }
        verify { SettingsSyncQueue.setting(context, SUBTITLE, OTHER) }
        verify { SettingsSyncQueue.setting(context, OTHER_AUDIO_ONLY, OTHER) }
        assertEquals(JsonPrimitive("fr"), settings.syncedSetting(AUDIO, OTHER))
        assertEquals(JsonPrimitive("off"), settings.syncedSetting(SUBTITLE, OTHER))
        assertEquals(JsonPrimitive(false), settings.syncedSetting(OTHER_AUDIO_ONLY, OTHER))
        assertNull(settings.syncedSetting(AUDIO, ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `sent values land on the same profile on another device`() {
        settings.setAudioLanguage(OTHER, "fr")
        settings.setSubtitleLanguage(OTHER, "en")
        settings.setSubtitlesOtherAudioOnly(OTHER, false)

        val otherDevice = freshSettings()
        KEYS.forEach { otherDevice.applyRemoteSetting(it, OTHER, settings.syncedSetting(it, OTHER)!!) }

        assertEquals(TrackLanguagePreference("fr", "en", false), otherDevice.trackLanguages(OTHER))
        assertEquals(TrackLanguagePreference(), otherDevice.trackLanguages(ProfileEntity.DEFAULT_ID))
    }

    @Test
    fun `a received blank value clears the setting`() {
        settings.setAudioLanguage(OTHER, "fr")

        settings.applyRemoteSetting(AUDIO, OTHER, JsonPrimitive(""))

        assertEquals("", settings.trackLanguages(OTHER).audio)
    }

    @Test
    fun `a received value that is not a code is dropped and the local one stays`() {
        settings.setAudioLanguage(OTHER, "fr")
        settings.setSubtitleLanguage(OTHER, "en")

        settings.applyRemoteSetting(AUDIO, OTHER, JsonPrimitive("French"))
        settings.applyRemoteSetting(AUDIO, OTHER, JsonPrimitive("off"))
        settings.applyRemoteSetting(AUDIO, OTHER, JsonPrimitive(42))
        settings.applyRemoteSetting(SUBTITLE, OTHER, JsonPrimitive("EN"))
        settings.applyRemoteSetting(OTHER_AUDIO_ONLY, OTHER, JsonPrimitive("yes"))

        assertEquals(TrackLanguagePreference("fr", "en", true), settings.trackLanguages(OTHER))
    }

    @Test
    fun `a received code this version does not list is kept, and off for subtitles`() {
        settings.applyRemoteSetting(AUDIO, OTHER, JsonPrimitive("fil"))
        settings.applyRemoteSetting(SUBTITLE, OTHER, JsonPrimitive("off"))

        assertEquals("fil", settings.trackLanguages(OTHER).audio)
        assertEquals(TrackLanguagePreference.SUBTITLES_OFF, settings.trackLanguages(OTHER).subtitle)
    }

    @Test
    fun `a value sent for no profile is dropped`() {
        settings.applyRemoteSetting(AUDIO, SyncKind.SHARED, JsonPrimitive("fr"))

        assertEquals("", settings.trackLanguages(SyncKind.SHARED).audio)
    }

    @Test
    fun `removing a profile's languages drops them`() {
        settings.setAudioLanguage(OTHER, "fr")
        settings.setSubtitleLanguage(OTHER, "en")
        settings.setSubtitlesOtherAudioOnly(OTHER, false)

        settings.removeTrackLanguages(OTHER)

        assertEquals(TrackLanguagePreference(), settings.trackLanguages(OTHER))
        KEYS.forEach { assertNull(it, settings.syncedSetting(it, OTHER)) }
    }

    private fun freshSettings(): AppSettings {
        val context = mockk<Context>()
        every { context.getSharedPreferences("app_settings", any()) } returns FakeSharedPreferences()
        return AppSettings(context)
    }

    private companion object {
        const val AUDIO = "audio_language"
        const val SUBTITLE = "subtitle_language"
        const val OTHER_AUDIO_ONLY = "subtitles_other_audio_only"
        val KEYS = listOf(AUDIO, SUBTITLE, OTHER_AUDIO_ONLY)
        const val OTHER = "5d0e8c1a-other-profile"
    }
}
