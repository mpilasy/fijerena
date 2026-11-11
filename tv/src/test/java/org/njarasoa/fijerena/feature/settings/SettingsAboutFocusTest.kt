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
 * section rows, for an Admin; About for everyone: About Fijerena, Device Info, then Logs. The About
 * rows open other screens, so nothing here presses OK.
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
            down("This Device / Language, Recent, reminders and the clock, on this device only"),
            down("Sources & Guide / Sources, the guide, film details, charts and sports"),
            down("Sync & Backup / Live sync with your other devices, and backups to a file"),
            // The end of Admin's list: Down stays put.
            down("Sync & Backup"),
            up("Sources & Guide"),
            back("Admin"),
            down("About"),
            right("About Fijerena / Version, source code, licences and credits"),
            down("Device Info / This app and device"),
            down(logs),
            // The end of About: Down stays put.
            down(logs),
            up("Device Info"),
            back("About"),
        )
    }

    @Test
    fun `a grown-up walks About`() {
        open(SettingsFixture.GUEST)
        walk.run(
            down("About"),
            right("About Fijerena"),
            down("Device Info"),
            down(logs),
            down(logs),
            left("About"),
        )
    }
}
