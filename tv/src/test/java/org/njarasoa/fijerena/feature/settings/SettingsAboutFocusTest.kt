package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.SettingsFixture
import org.njarasoa.fijerena.testing.SettingsFocusTest
import org.njarasoa.fijerena.testing.back
import org.njarasoa.fijerena.testing.down
import org.njarasoa.fijerena.testing.left
import org.njarasoa.fijerena.testing.right
import org.njarasoa.fijerena.testing.up
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → Advanced and About (`scripts/focus-walks/settings.txt`): Advanced for an Expert
 * (Diagnostics, Shrink Database), About for everyone. The rows open other screens or shrink the
 * database, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAboutFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks Advanced and About`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("This Device"),
            down("Sources & Guide"),
            down("Sync & Backup"),
            down("Advanced"),
            right("Diagnostics / Crashes and errors recorded on this device"),
            down("Shrink Database"),
            down("Shrink Database"),
            up("Diagnostics"),
            back("Advanced"),
            down("About"),
            right("Fijerena v"),
            down("Device Info / This app and device"),
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
            down("Playback"),
            down("Display"),
            down("About"),
            right("Fijerena v"),
            down("Device Info"),
            down("Terms of Use"),
            down("Open-Source Licences"),
            // No Diagnostics and no Shrink Database without Expert mode.
            down("Film and Series Data / TMDB logo"),
            down("Film and Series Data / TMDB logo"),
            left("About"),
        )
    }
}
