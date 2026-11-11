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
 * TV Settings → About (`scripts/focus-walks/settings.txt`), last on the rail, under the Admin
 * sections for an Admin: About Fijerena, Device Info, then Logs, for everyone. The About rows open
 * other screens, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAboutFocusTest : SettingsFocusTest() {
    private val logs get() = context.getString(R.string.settings_diagnostics_title)

    @Test
    fun `an Admin walks About`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAdminSection("Sync & Backup"),
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
