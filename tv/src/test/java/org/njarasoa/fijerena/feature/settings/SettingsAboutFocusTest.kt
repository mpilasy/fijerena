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
 * TV Settings → the end of Admin, and About (`scripts/focus-walks/settings.txt`): Shrink Database,
 * the last row of Admin's page, for an Expert; About for everyone, with Logs under Device Info. The rows
 * open other screens or shrink the database, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAboutFocusTest : SettingsFocusTest() {
    private val logs get() = context.getString(R.string.settings_diagnostics_title)

    @Test
    fun `an Expert walks the end of Admin and About`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Admin"),
            right("Set a PIN for Settings"),
            down("A / Admin"),
            *adminDown("A / Admin", "Restore from a File"),
            down("Shrink Database"),
            // The end of Admin's page: Down stays put.
            down("Shrink Database"),
            up("Restore from a File"),
            back("Admin"),
            down("About"),
            right("Fijerena v"),
            down("Device Info / This app and device"),
            down(logs),
            down("Terms of Use / Who is responsible for the sources you add"),
            down("Open-Source Licences / The libraries and fonts the app is built with"),
            // TMDB's attribution: one focusable row, last.
            down("Film and Series Data / TMDB logo"),
            down("Film and Series Data / TMDB logo"),
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
            // No Shrink Database without Expert mode.
            down("Film and Series Data / TMDB logo"),
            down("Film and Series Data / TMDB logo"),
            left("About"),
        )
    }
}
