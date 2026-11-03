package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Admin's list of sections, and About (`scripts/focus-walks/settings.txt`): the five
 * section rows, for an Admin; About for everyone, with Logs under Device Info. The About rows open
 * other screens, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAboutFocusTest : SettingsFocusTest() {
    private val logs get() = context.getString(R.string.settings_diagnostics_title)

    @Test
    fun `an Admin walks Admin's list and About`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Admin"),
            // Each section's row says what is inside it.
            right("Profiles / Admin, Guest, Kid"),
            down("Parental Controls / The household's word lists, for every child profile"),
            down("This Device / Language, Recent and the clock, on this device only"),
            down("Sources & Guide / Sources, the guide, film details, charts and sports"),
            down("Sync & Backup / Live sync with your other devices, and backups to a file"),
            // The end of Admin's list: Down stays put.
            down("Sync & Backup"),
            up("Sources & Guide"),
            back("Admin"),
            down("About"),
            right("Fijerena v"),
            down("Device Info / This app and device"),
            down(logs),
            down("Terms of Use / Who is responsible for the sources you add"),
            down("Open-Source Licences / The libraries and fonts the app is built with"),
            // TMDB's attribution: one focusable row, last.
            down("Film and Show Data / TMDB logo"),
            down("Film and Show Data / TMDB logo"),
            up("Open-Source Licences"),
            back("About"),
        )
    }

    @Test
    fun `a grown-up walks About`() {
        open(SettingsFixture.GUEST)
        walk.run(
            down("About"),
            right("Fijerena v"),
            down("Device Info"),
            down(logs),
            down("Terms of Use"),
            down("Open-Source Licences"),
            // No Shrink Database for a profile that is not an Admin.
            down("Film and Show Data / TMDB logo"),
            down("Film and Show Data / TMDB logo"),
            left("About"),
        )
    }
}
