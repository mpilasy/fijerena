package org.njarasoa.fijerena.testing

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Rule
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.SettingsScreen

/**
 * The frame of a TV Settings focus test: the whole [SettingsScreen] (or a screen it opens) on
 * [SettingsFixture]'s data, and a [FocusWalk] on it. The subclass runs on Robolectric.
 */
abstract class SettingsFocusTest {
    // The v2 rule: a standard (queued) dispatcher, as on a device, that the rule's clock drives.
    // With an unconfined one a LaunchedEffect starts before the rows below it have registered,
    // and the first focus misses.
    @get:Rule
    val rule = createComposeRule()

    protected val walk = FocusWalk(rule)

    @After
    fun resetFixture() = SettingsFixture.reset()

    /** Opens Settings with [active] in use; the profiles in [pins] have a PIN. */
    protected fun open(
        active: String,
        pins: Set<String> = emptySet(),
    ) {
        seed(active, pins)
        showSettings()
    }

    /**
     * Shows Settings on the data [seed] wrote, and waits until its profiles have loaded. They load
     * from the database off the main thread, and until then the profile in use is unknown: a key
     * pressed before then finds an empty pane, and no group asks the PIN.
     */
    protected fun showSettings(settings: @Composable () -> Unit = { SettingsScreen(onBack = {}, onProviderChanged = {}) }) {
        lateinit var owner: ViewModelStoreOwner
        show {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            settings()
        }
        // The screen's own view model: the same store and key as its viewModel() call.
        val profiles = ViewModelProvider(owner, SettingsViewModelFactory(context))[ProfilesViewModel::class.java]
        // runOnIdle lets the main thread take each value the database gives.
        rule.waitUntil(PROFILES_TIMEOUT_MS) { rule.runOnIdle { profiles.activeProfile.value != null } }
        rule.waitForIdle()
    }

    /** Writes the fixture's data, with [active] in use; the profiles in [pins] have a PIN. */
    protected fun seed(
        active: String,
        pins: Set<String> = emptySet(),
    ) = SettingsFixture.seed(context, active, pins)

    /** Shows [screen]: Settings, or a screen Settings opens. */
    protected fun show(screen: @Composable () -> Unit) {
        rule.setContent(screen)
        rule.waitForIdle()
    }

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * From the rail into Admin's list of sections, down to the row of [section] (one of
     * [ADMIN_SECTIONS]), one step per row. The list opens on its first row, Profiles.
     */
    protected fun toAdminSection(section: String): Array<FocusStep> =
        arrayOf(
            down("Admin"),
            right(ADMIN_SECTIONS.first()),
            *ADMIN_SECTIONS.subList(1, ADMIN_SECTIONS.indexOf(section) + 1).map(::down).toTypedArray(),
        )

    /** Down from row [from] to row [to] of an open Admin section's [rows], one step per row. */
    protected fun sectionDown(
        rows: List<String>,
        from: String,
        to: String,
    ): Array<FocusStep> = rows.subList(rows.indexOf(from) + 1, rows.indexOf(to) + 1).map(::down).toTypedArray()

    protected companion object {
        private const val PROFILES_TIMEOUT_MS = 5_000L

        /** Admin's list of sections, in order; each row opens its section in place of the list. */
        val ADMIN_SECTIONS = listOf("Profiles", "Parental Controls", "This Device", "Sources & Guide", "Sync & Backup")

        /** Admin → Parental Controls, as an Admin sees it on the fixture's data. */
        val PARENTAL_ROWS =
            listOf(
                "Kids' Categories",
                "Kids' Film Categories",
                "Adult Categories",
                "Hidden Titles",
                "Exceptions",
                "Check a Source",
                "Content Filters of Each Profile",
            )

        /** Admin → Sources & Guide on the fixture's data, Shrink Database last. */
        val SOURCE_GUIDE_ROWS =
            listOf(
                "Manage Sources",
                "Guide Sources",
                "Guide Data Maintenance",
                "Film and Show Details (TMDB)",
                "TMDB Charts",
                "Refresh Charts",
                "Sports",
                "Shrink Database",
            )
    }
}
