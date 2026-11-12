package org.njarasoa.fijerena.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Admin's own page under Your Profile (no Admin rows), a grown-up's page in Admin → Profiles, and
 * a child's page when an Admin has a PIN, with its "Parental Controls ›" link.
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
            down("Nerd / Shows technical details"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language / Used when you have not picked a track for the film or show; else the original. / English"),
            down("Subtitle Language / Stream default"),
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
            down("Nerd / Shows technical details"),
            center("Nerd / Shows technical details"),
        )
        rule.waitForIdle()

        assertTrue(AppSettings(context).nerdMode(SettingsFixture.GUEST))
    }

    @Test
    fun `a subtitle language shows the switch under its row`() {
        open(SettingsFixture.GUEST)
        walk.run(
            right("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN"),
            down("Nerd"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language"),
            // The picker opens on the current value: English with no setting.
            center("English"),
            left("Audio Language"),
            down("Subtitle Language / Stream default"),
            // Stream default, Off, then English: OK picks it and goes back to the row.
            center("Stream default"),
            down("Off"),
            down("English"),
            center("Subtitle Language / English"),
            down("Only When the Audio Is in Another Language"),
            center("Only When the Audio Is in Another Language"),
        )
        rule.waitForIdle()

        val languages = AppSettings(context).trackLanguages(SettingsFixture.GUEST)
        assertEquals("en", languages.subtitle)
        assertFalse(languages.subtitlesOtherAudioOnly)
    }

    @Test
    fun `a grown-up's own page ends on Add Profile, and Up goes back up the page`() {
        open(SettingsFixture.GUEST)
        walk.run(
            right("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows technical details"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language"),
            down("Subtitle Language / Stream default"),
            down("Add Profile"),
            up("Subtitle Language"),
            down("Add Profile"),
            // Left leaves the page from Add Profile too.
            left("Your Profile"),
        )
    }

    @Test
    fun `an Admin's own page under Your Profile has no Admin rows`() {
        open(SettingsFixture.ADMIN)
        walk.assertFocused("Your Profile")
        walk.run(
            right("Edit Name / Name / Admin"),
            down("Colour"),
            down("PIN"),
            down("Nerd / Shows technical details"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language"),
            down("Subtitle Language / Stream default"),
            // No Child profile, Admin switch, Sources or Content filters: those are in Admin →
            // Profiles. No Add Profile either: an Admin adds one there.
            down("Subtitle Language"),
            left("Your Profile"),
        )
    }

    @Test
    fun `an Admin walks a grown-up's page`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Profiles"),
            right("Set a PIN for Settings"),
            down("admi / Admin"),
            down("gues / Guest"),
            center("Edit Name / Name / Guest"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / Off"),
            down("Nerd / Shows technical details"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language"),
            down("Subtitle Language / Stream default"),
            // Admin: Child profile and the Admin switch, both free to change on a grown-up's page.
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Admin / Lets this profile change sources, profiles, parental controls and backups"),
            down("Sources / 2 of 2"),
            down("Content Filters / Hide categories by prefix"),
            down("Switch to this profile"),
            down("Delete profile"),
            down("Delete profile"),
            // Back closes the page onto the profile's row, in the section; Back again goes to the
            // section's rail row.
            back("gues / Guest"),
            back("Profiles"),
        )
    }

    @Test
    fun `with an Admin PIN, a child's page has no Set a PIN row and links to Parental Controls`() {
        open(SettingsFixture.ADMIN, pins = setOf(SettingsFixture.ADMIN, SettingsFixture.KID))
        walk.run(
            down("Profiles"),
            // An Admin has a PIN: no reminder, the section opens on the profiles.
            right("admi / Admin / Using on this device"),
            down("gues / Guest"),
            down("kid / Kid"),
            center("Edit Name / Name / Kid"),
            down("Colour"),
            down("PIN / Asked in Settings before sources, backups and profiles / On"),
            down("UI Theme"),
            down("Theme"),
            down("Look and Feel"),
            down("Poster in the Background"),
            down("Play Next Episode Automatically"),
            down("Audio Language"),
            down("Subtitle Language / Stream default"),
            down("Child Profile / On TV, Home shows only the favourites"),
            down("Favourites / What this child sees on Home"),
            down("Sources / 1 of 2"),
            down("Content Filters"),
            down("Parental Controls / The household's word lists"),
            // The link closes the page and selects the Parental Controls section on the rail, focus
            // on its first list; Back goes to that section's rail row.
            center("Kids' Categories"),
            back("Parental Controls"),
        )
    }
}
