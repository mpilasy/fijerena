package org.njarasoa.fijerena.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.PinSession
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.robolectric.RobolectricTestRunner

/**
 * The phone's Settings by role (the admin settings plan): an Expert sees Your Profile, Admin and
 * About; a grown-up and a child Your Profile and About. Admin asks the PIN once on the way in
 * (Cancel stays on the list), then shows every Expert group under its header. Your Profile has only
 * the personal rows; Admin → Profiles → a profile has the Admin rows too.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAdminTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun text(
        @StringRes id: Int,
    ) = context.getString(id)

    @After
    fun reset() {
        PinSession.onActiveProfile("")
        listOf(
            "org.njarasoa.fijerena.core.network.provider.SettingsDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.ui.di.AppContainer" to "instance",
            "org.njarasoa.fijerena.core.ui.sync.SyncManager" to "instance",
        ).forEach { (className, fieldName) ->
            val field = Class.forName(className).getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, null)
        }
    }

    @Test
    fun `an Expert sees Your Profile, Admin and About, no Playback or Display`() {
        showSettings(active = ADMIN)

        rule.onNodeWithText(text(R.string.profile_own_page_title)).assertExists()
        rule.onNodeWithText(text(R.string.settings_group_admin)).assertExists()
        scrollTo(text(R.string.settings_about_section_title))
        rule.onNodeWithText(text(R.string.settings_playback_section_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.settings_group_display)).assertDoesNotExist()
        // Your Profile is the Expert's own page; the other profiles are in Admin.
        rule.onNodeWithText("Guest").assertDoesNotExist()
        rule.onNodeWithText(text(R.string.settings_profiles_add)).assertDoesNotExist()
    }

    @Test
    fun `a grown-up sees Your Profile and About, no Admin`() {
        showSettings(active = GUEST)

        rule.onNodeWithText(text(R.string.profile_own_page_title)).assertExists()
        rule.onNodeWithText(text(R.string.settings_profiles_add)).assertExists()
        rule.onNodeWithText(text(R.string.settings_group_admin)).assertDoesNotExist()
        scrollTo(text(R.string.settings_about_section_title))
    }

    @Test
    fun `a child sees Your Profile and About, no Admin`() {
        showSettings(active = KID)

        rule.onNodeWithText(text(R.string.profile_own_page_title)).assertExists()
        rule.onNodeWithText(text(R.string.settings_profiles_add)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.settings_group_admin)).assertDoesNotExist()
        scrollTo(text(R.string.settings_about_section_title))
    }

    @Test
    fun `Admin asks the PIN, and Cancel stays on the list`() {
        showSettings(active = ADMIN, pins = setOf(ADMIN), locked = true)

        rule.onNodeWithText(text(R.string.settings_group_admin)).performClick()
        rule.onNodeWithText(context.getString(R.string.pin_enter_for, "Ada")).assertExists()
        rule.onNodeWithText(text(R.string.common_cancel)).performClick()
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.pin_enter_for, "Ada")).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.profile_own_page_title)).assertExists()
        rule.onNodeWithText(text(R.string.settings_group_this_device)).assertDoesNotExist()
    }

    @Test
    fun `Your Profile opens the Expert's own page`() {
        var opened: String? = null
        showSettings(active = ADMIN) {
            MobileSettingsScreen(onBack = null, onEditProfile = { opened = it }, onProviderChanged = {})
        }

        rule.onNodeWithText("Ada").performClick()
        assertEquals(ADMIN, opened)
    }

    @Test
    fun `the Admin page shows each Expert group, and a profile opens with its Admin rows`() {
        var opened: String? = null
        showSettings(active = ADMIN) {
            MobileSettingsScreen(onBack = null, onAdminProfile = { opened = it }, onProviderChanged = {})
        }

        rule.onNodeWithText(text(R.string.settings_group_admin)).performClick()
        rule.waitForIdle()
        listOf(
            R.string.settings_profiles_title,
            R.string.parental_controls_title,
            R.string.settings_group_this_device,
            R.string.settings_group_source_guide,
            R.string.settings_group_sync_backup,
            R.string.settings_section_advanced,
        ).forEach { scrollTo(text(it)) }
        scrollTo("Guest").performClick()
        assertEquals(GUEST, opened)
    }

    @Test
    fun `Your Profile has no Admin rows, an Expert's too`() {
        seed(active = ADMIN)
        showProfilePage(ADMIN, adminRows = false)

        rule.onNodeWithText(text(R.string.profile_own_page_title)).assertExists()
        rule.onNodeWithText(text(R.string.profile_section_look)).assertExists()
        rule.onNodeWithText(text(R.string.settings_developer_mode_title)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.profile_child_title)).assertDoesNotExist()
    }

    @Test
    fun `Admin, Profiles, a profile has its Admin rows`() {
        seed(active = ADMIN)
        showProfilePage(GUEST, adminRows = true)

        rule.onNodeWithText(text(R.string.profile_dialog_edit_title)).assertExists()
        scrollTo(text(R.string.profile_child_title))
        scrollTo(text(R.string.settings_developer_mode_title))
        scrollTo(text(R.string.profile_delete_button))
    }

    /** Scrolls the screen's list or page to the node with [label], which must exist. */
    private fun scrollTo(label: String) =
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(label)).let {
            // The first: a header can share a row's text (Parental Controls).
            rule.onAllNodesWithText(label).onFirst().assertExists()
        }

    private fun showSettings(
        active: String,
        pins: Set<String> = emptySet(),
        locked: Boolean = false,
        screen: @Composable () -> Unit = { MobileSettingsScreen(onBack = null, onProviderChanged = {}) },
    ) {
        seed(active, pins)
        if (locked) PinSession.onActiveProfile("")
        lateinit var owner: ViewModelStoreOwner
        rule.setContent {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            screen()
        }
        // The profiles load off the main thread; until then the profile in use is unknown.
        val profiles = ViewModelProvider(owner, SettingsViewModelFactory(context))[ProfilesViewModel::class.java]
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { profiles.activeProfile.value != null } }
        rule.waitForIdle()
    }

    private fun showProfilePage(
        id: String,
        adminRows: Boolean,
    ) {
        lateinit var profiles: ProfilesViewModel
        rule.setContent {
            profiles = viewModel(factory = SettingsViewModelFactory(context))
            MobileProfileEditScreen(
                profileId = id,
                viewModel = profiles,
                onBack = {},
                onProfileSwitched = {},
                adminRows = adminRows,
            )
        }
        rule.waitUntil(TIMEOUT_MS) { rule.runOnIdle { profiles.profiles.value.isNotEmpty() } }
        rule.waitForIdle()
    }

    /** Ada (Expert), Guest (a grown-up) and Kid (a child); [active] in use, unlocked. */
    private fun seed(
        active: String,
        pins: Set<String> = emptySet(),
    ) = runBlocking {
        EpgIndexDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).build())
        }
        val profiles = SettingsDatabase.getInstance(context).profileDao()
        profiles.delete(ProfileEntity.DEFAULT_ID)
        profiles.insert(ProfileEntity(ADMIN, "Ada", createdAt = 1, colorIndex = 0))
        profiles.insert(ProfileEntity(GUEST, "Guest", createdAt = 2, colorIndex = 1))
        profiles.insert(ProfileEntity(KID, "Kid", createdAt = 3, colorIndex = 2))
        val settings = AppSettings(context)
        settings.activeProfileId = active
        settings.setDevMode(ADMIN, true)
        settings.setChildProfile(KID, true)
        pins.forEach { ProfilePins(settings).set(it, PIN) }
        if (active in pins) PinSession.unlock(active)
    }

    private companion object {
        const val ADMIN = "admin"
        const val GUEST = "guest"
        const val KID = "kid"
        const val PIN = "1234"
        const val TIMEOUT_MS = 5_000L
    }
}
