package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
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
 * TV Settings → a profile's page (`scripts/focus-walks/settings.txt`, the Profiles part), the
 * cases `SettingsProfilesFocusTest` leaves: a grown-up's own page (Add Profile at its end), an
 * Expert's own page under Your Profile (no Admin rows), a grown-up's page in Admin → Profiles, and
 * a child's page when an Expert has a PIN, with its "Parental Controls ›" link.
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
            center("Red"),
            left("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows this profile the stats"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            // A grown-up adds a profile from the end of its own page (the profile rules plan, PR-D1).
            down("Add Profile"),
            down("Add Profile"),
            left("Your Profile"),
            right("Edit Name / Name / Guest"),
            back("Your Profile"),
        )
    }

    @Test
    fun `a grown-up turns its own Nerd on, without the PIN`() {
        open(SettingsFixture.GUEST)
        walk.run(
            right("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows this profile the stats"),
            center("Nerd / Shows this profile the stats"),
        )
        rule.waitForIdle()

        assertTrue(AppSettings(context).nerdMode(SettingsFixture.GUEST))
    }

    @Test
    fun `a grown-up's own page ends on Add Profile, and Up goes back up the page`() {
        open(SettingsFixture.GUEST)
        walk.run(
            right("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows this profile the stats"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Add Profile"),
            up("Play Next Episode Automatically"),
            down("Add Profile"),
            // Left leaves the page from Add Profile too.
            left("Your Profile"),
        )
    }

    @Test
    fun `an Expert's own page under Your Profile has no Admin rows`() {
        open(SettingsFixture.ADMIN)
        walk.assertFocused("Your Profile")
        walk.run(
            right("Edit Name / Name / Admin"),
            down("Colour"),
            down("PIN"),
            down("Nerd / Shows this profile the stats"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            // No Child profile, Admin switch, Sources or Content filters: those are in Admin →
            // Profiles. No Add Profile either: an Expert adds one there.
            down("Play Next Episode Automatically"),
            left("Your Profile"),
        )
    }

    @Test
    fun `an Expert walks a grown-up's page`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Admin"),
            right("Profiles"),
            center("Set a PIN for Settings"),
            down("A / Admin"),
            down("G / Guest"),
            center("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows this profile the stats"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            // Admin: Child profile and Expert mode, both free to change on a grown-up's page.
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Admin / Lets this profile change sources, profiles, parental controls and backups"),
            down("Sources / 2 of 2"),
            down("Content Filters / Hide categories by prefix"),
            down("Switch to this profile"),
            down("Delete profile"),
            down("Delete profile"),
            // Back closes the page onto the profile's row, in the section, not Admin's list.
            back("G / Guest"),
            back("Profiles"),
        )
    }

    @Test
    fun `with an Expert PIN, a child's page has no Set a PIN row and links to Parental Controls`() {
        open(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN, SettingsFixture.KID))
        walk.run(
            down("Admin"),
            right("Profiles"),
            // An Expert has a PIN: no reminder, the section opens on the profiles.
            center("A / Admin / Using on this device"),
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
            // The link closes the page on Admin's Parental Controls section, focus on its first list;
            // Back closes the section on its row in Admin's list, then goes to the rail row.
            center("Kids' Categories"),
            back("Parental Controls"),
            back("Admin"),
        )
    }
}
