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
 * TV Settings → About & Advanced (`scripts/focus-walks/settings.txt`): its rows for an Expert, and
 * About for a grown-up (no Diagnostics, no Shrink Database). The rows open other screens or shrink
 * the database, so nothing here presses OK.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsAboutFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks About and Advanced`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("Sources & Guide"),
            down("Sync & Backup"),
            down("About & Advanced"),
            right("Fijerena v"),
            down("Device Info / This app and device"),
            down("Terms of Use / Who is responsible for the sources you add"),
            down("Open-Source Licences / The libraries and fonts the app is built with"),
            down("Diagnostics / Crashes and errors recorded on this device"),
            down("Shrink Database"),
            // TMDB's attribution: one focusable row, last.
            down("Film and Series Data / TMDB logo"),
            down("Film and Series Data / TMDB logo"),
            up("Shrink Database"),
            back("About & Advanced"),
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
