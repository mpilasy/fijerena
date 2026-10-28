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
 * TV Settings → Admin → Profiles, the whole screen on the fixture's data
 * (`scripts/focus-walks/settings.txt`, the Profiles part): the list of profiles, a profile's page
 * with its Admin rows and the way back, for an Expert; a child's own page.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsProfilesFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks the profiles and their pages`() {
        open(SettingsFixture.ADMIN)
        walk.assertFocused("Your Profile")
        walk.run(
            down("Admin"),
            // A child profile and no Expert PIN: the reminder comes first, then the profiles.
            right("Set a PIN for Settings"),
            down("A / Admin / Using on this device"),
            // OK opens the profile's page in place of the rows, focus on the name's Edit button.
            // Its own page in Admin has the Admin rows too (the admin settings plan, AS-D1).
            center("Edit Name / Name / Admin"),
            down("Colour"),
            down("PIN"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Child Profile / The only admin profile"),
            down("Admin"),
            down("Sources / 2 of 2"),
            down("Content Filters"),
            // Down at the end of the page stays put.
            down("Content Filters"),
            // Left leaves the page, focus back on the profile's row.
            left("A / Admin"),
            down("G / Guest"),
            down("K / Kid"),
            // A child's page: no Expert mode row; its own rows after Child profile.
            center("Edit Name / Name / Kid"),
            down("Colour"),
            down("PIN"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Favourites / What this child sees on Home"),
            down("Sources / 1 of 2"),
            down("Content Filters / Hide categories by prefix (e.g., 'Adult', 'XXX') / Source One"),
            down("Parental Controls / The household's word lists"),
            down("Switch to this profile"),
            down("Delete profile"),
            down("Delete profile"),
            // Back leaves the page too, focus back on the row.
            back("K / Kid"),
            down("Add Profile"),
            // Parental Controls follows on the same page.
            down("Kids' Categories"),
            up("Add Profile"),
            // Back in the pane returns to the rail row, not out of Settings.
            back("Admin"),
        )
    }

    @Test
    fun `a child walks its own page`() {
        open(SettingsFixture.KID)
        // A child's rail: its own page and About only.
        walk.assertFocused("Your Profile")
        walk.run(
            down("About"),
            down("About"),
            up("Your Profile"),
            // The page shows beside the rail; Right enters it on its first row.
            right("Colour"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Play Next Episode Automatically"),
            left("Your Profile"),
            right("Colour"),
            back("Your Profile"),
        )
    }
}
