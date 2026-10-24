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
import org.robolectric.RobolectricTestRunner

/**
 * TV Settings → a profile's page (`scripts/focus-walks/settings.txt`, the Profiles part), the
 * cases `SettingsProfilesFocusTest` leaves: a grown-up's own page, a grown-up's page in an
 * Expert's view, and a child's page when an Expert has a PIN, with its "Parental Controls ›" link.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsProfilePageFocusTest : SettingsFocusTest() {
    @Test
    fun `a grown-up walks its own page`() {
        open(SettingsFixture.GUEST)
        walk.assertFocused("Your Profile")
        walk.run(
            // Its own page has Name and PIN, and no Admin rows.
            right("Edit Name / Name / Guest"),
            down("Colour"),
            // OK on Colour opens its picker on the current colour; Left closes it on the row.
            center("Colour 2"),
            left("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Play Next Episode Automatically"),
            left("Your Profile"),
            right("Edit Name / Name / Guest"),
            back("Your Profile"),
        )
    }

    @Test
    fun `an Expert walks a grown-up's page`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            right("Set a PIN for Settings"),
            down("A / Admin"),
            down("G / Guest"),
            center("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            // Admin: Child profile and Expert mode, both free to change on a grown-up's page.
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Expert Mode / Lets this profile change sources, profiles, parental controls and backups"),
            down("Sources / 2 of 2"),
            down("Content Filters / Hide categories by prefix"),
            down("Switch to this profile"),
            down("Delete profile"),
            down("Delete profile"),
            back("G / Guest"),
        )
    }

    @Test
    fun `with an Expert PIN, a child's page has no Set a PIN row and links to Parental Controls`() {
        open(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN, SettingsFixture.KID))
        walk.run(
            // An Expert has a PIN: no reminder, the pane opens on the profiles.
            right("A / Admin / Using on this device"),
            down("G / Guest"),
            down("K / Kid"),
            center("Edit Name / Name / Kid"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / On"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Favourites / What this child sees on Home"),
            down("Sources / 1 of 2"),
            down("Content Filters"),
            down("Parental Controls / The household's word lists"),
            // The link opens the Parental Controls group, focus on its first list; Back goes to
            // that group's rail row.
            center("Kids' Categories"),
            back("Parental Controls"),
        )
    }
}
