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
 * TV Settings → Parental Controls (`scripts/focus-walks/parental-controls.txt`): the group's rows,
 * one list (Exceptions, the app's words) to its end and back to its row, Check a source and the
 * link to Profiles. Removing and putting back a word is `ParentalListContentTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsParentalControlsFocusTest : SettingsFocusTest() {
    @Test
    fun `an Expert walks the group and the Exceptions list`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            right("Kids' Categories / A category shows to a child when its name has one of these words."),
            down("Kids' Film Categories / These also show, for film categories only."),
            down("Adult Categories / A category with one of these words never shows to a child."),
            down("Hidden Titles / A title with one of these words never shows to a child"),
            down("Exceptions / A film or series with one of these words shows to a child"),
            down("Check a Source"),
            down("Content Filters of Each Profile / Set on each profile's page, in Profiles"),
            // Down at the end of the pane stays put.
            down("Content Filters of Each Profile"),
            up("Check a Source"),
            up("Exceptions"),
            // OK opens the list in place of the rows, focus on Add a word.
            center("Add a word"),
            down("pokemon"),
            down("doraemon"),
            up("pokemon"),
            up("Add a word"),
            // Left leaves the list, focus back on its row.
            left("Exceptions / A film or series with one of these words shows to a child"),
            center("Add a word"),
            // Back leaves it too.
            back("Exceptions"),
            back("Parental Controls"),
        )
    }

    @Test
    fun `the Exceptions list ends on Reset`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            right("Kids' Categories"),
            down("Kids' Film Categories"),
            down("Adult Categories"),
            down("Hidden Titles"),
            down("Exceptions"),
            center("Add a word"),
            down("pokemon"),
            down("doraemon"),
            down("one piece"),
            down("naruto"),
            down("boruto"),
            down("dragon ball"),
            down("detective conan"),
            down("sailor moon"),
            down("yu-gi-oh"),
            down("yugioh"),
            down("Reset to the app's list"),
            down("Reset to the app's list"),
            back("Exceptions"),
        )
    }

    @Test
    fun `Check a Source and the link to Profiles`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            down("Parental Controls"),
            right("Kids' Categories"),
            down("Kids' Film Categories"),
            down("Adult Categories"),
            down("Hidden Titles"),
            down("Exceptions"),
            down("Check a Source"),
            // The child's only source: its row first, focus on it.
            center("Source One"),
            left("Check a Source"),
            down("Content Filters of Each Profile"),
            // OK opens the Profiles group, focus on its first row; Back goes to the rail.
            center("Set a PIN for Settings"),
            back("Profiles"),
        )
    }
}
