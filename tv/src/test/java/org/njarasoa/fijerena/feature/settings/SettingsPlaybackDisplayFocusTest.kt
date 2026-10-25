package org.njarasoa.fijerena.feature.settings

import org.junit.Test
import org.junit.runner.RunWith
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
 * TV Settings → Playback, Display and This Device (`scripts/focus-walks/settings.txt`): Playback and
 * Display hold only the rows that open the page of the profile in use at their row ("Play Next
 * Episode Automatically", "Theme and Look and Feel"), Back returning to them; This Device (an
 * Expert's) holds the device's rows, a picker and the way back to its row.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPlaybackDisplayFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks Playback and Display`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            right("Play Next Episode Automatically / Set on your profile’s page / Off"),
            // Nothing else in the group: Down stays put.
            down("Play Next Episode Automatically / Set on your profile’s page"),
            // The page of the profile in use, focus on its switch; Back returns to the row.
            center("Play Next Episode Automatically / Near the end of an episode"),
            back("Play Next Episode Automatically / Set on your profile’s page"),
            left("Playback"),
            down("Display"),
            right("Theme and Look and Feel / Set on your profile’s page"),
            // The page of the profile in use at its UI theme; Back returns to the row.
            center("UI Theme / Sets the theme and the Look and Feel together."),
            back("Theme and Look and Feel / Set on your profile’s page"),
            up("Theme and Look and Feel"),
            back("Display"),
        )
    }

    @Test
    fun `an Expert walks This Device`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            down("Playback"),
            down("Display"),
            down("This Device"),
            right("Language / Language for menus and messages"),
            down("Text & Grid Size / Size of text and everything on screen, on every screen"),
            down("Items in Recent / How many films, episodes or channels Recent shows / 25"),
            down("Add a Channel to Recent After / So a channel you only zap past isn't added / 10 s"),
            down("Show Clock / The time at the top right of every screen"),
            // Down at the end of the pane stays put.
            down("Show Clock"),
            up("Add a Channel to Recent After"),
            // OK opens the picker on the current value; Left closes it, focus back on its row.
            center("10 s"),
            down("15 s"),
            left("Add a Channel to Recent After"),
            up("Items in Recent"),
            center("25"),
            back("Items in Recent"),
            up("Text & Grid Size"),
            up("Language"),
            // Up at the top of the pane stays put.
            up("Language"),
            back("This Device"),
        )
    }

    @Test
    fun `a grown-up opens its own page from Display and Playback`() {
        open(SettingsFixture.GUEST)
        walk.assertFocused("Your Profile")
        walk.run(
            down("Playback"),
            // Only the profile's row.
            right("Play Next Episode Automatically / Set on your profile’s page"),
            // Nothing above it: Up stays put.
            up("Play Next Episode Automatically / Set on your profile’s page"),
            center("Play Next Episode Automatically / Near the end of an episode"),
            // Left leaves the page too, focus back on the row.
            left("Play Next Episode Automatically / Set on your profile’s page"),
            left("Playback"),
            down("Display"),
            right("Theme and Look and Feel"),
            center("UI Theme"),
            // The page's rows below go on as on Your profile; no Admin rows for a grown-up.
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Play Next Episode Automatically"),
            back("Theme and Look and Feel"),
        )
    }
}
