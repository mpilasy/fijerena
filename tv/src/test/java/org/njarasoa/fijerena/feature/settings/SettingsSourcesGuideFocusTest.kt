package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.FocusStep
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.center
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Admin → Sources & Guide (`scripts/focus-walks/settings.txt` and
 * `settings-guide.txt`): its rows, the guide's maintenance sub-pane (never pressed) and the TMDB
 * key's, each closing on its row. Manage Sources and Guide Sources open other screens (Guide
 * Sources with no source chooser), tested on their own (`SourcesFocusTest`, `GuideSourcesFocusTest`).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsSourcesGuideFocusTest : SettingsFocusTest() {
    /** From the rail to Admin's last This Device row, Show Clock: the part's rows come next. */
    private fun toShowClock(): Array<FocusStep> =
        arrayOf(
            down("Admin"),
            right("Set a PIN for Settings"),
            down("A / Admin"),
            *adminDown("A / Admin", "Show Clock"),
        )

    @Test
    fun `an Expert walks Sources and Guide`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toShowClock(),
            down("Manage Sources / Source One, Source Two"),
            // The fixture's sources are Xtream ones, with live channels: Guide Sources shows.
            down("Guide Sources / The guides for each source’s channels"),
            down("Guide Data Maintenance / The guide's downloads and programmes kept on this device"),
            down("Film and Series Details (TMDB)"),
            down("TMDB Charts"),
            // Sync & Backup follows on the same page.
            down("Live Sync"),
            up("TMDB Charts"),
            up("Film and Series Details (TMDB)"),
            up("Guide Data Maintenance"),
            // The sub-pane opens on its first action, the destructive one last.
            center("Delete leftover downloads"),
            down("Delete programmes over 2 days old"),
            down("Clear all data"),
            down("Clear all data"),
            up("Delete programmes over 2 days old"),
            // Back leaves the sub-pane onto its row, Back again returns to the rail.
            back("Guide Data Maintenance"),
            back("Admin"),
        )
    }

    @Test
    fun `Guide Sources opens the one page, with no source chooser`() {
        seed(SettingsFixture.ADMIN)
        var opened = 0
        showSettings { SettingsScreen(onBack = {}, onProviderChanged = {}, onGuideSources = { opened++ }) }
        walk.run(
            *toShowClock(),
            down("Manage Sources"),
            down("Guide Sources"),
            // Two sources with live channels, and no chooser: the page opens at once.
            center("Guide Sources"),
        )
        assertEquals(1, opened)
    }

    @Test
    fun `the TMDB key's pane closes on its row`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toShowClock(),
            *adminDown("Show Clock", "Film and Series Details (TMDB)"),
            // Focus opens on the key's Edit button.
            center("Edit TMDB key / TMDB key"),
            left("Film and Series Details (TMDB)"),
            left("Admin"),
        )
    }
}
