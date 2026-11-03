package org.njarasoa.fijerena.feature.settings

import androidx.compose.ui.input.key.Key
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.testing.FocusStep
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
 * TV Settings → Admin → Parental Controls (`scripts/focus-walks/parental-controls.txt`): the part's
 * rows, one list (Exceptions, the app's words) to its end and back to its row, Check a source and
 * the link to Profiles. Removing and putting back a word is `ParentalListContentTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsParentalControlsFocusTest : SettingsFocusTest() {
    /** From the rail into Admin → Parental Controls, on its first row. */
    private fun toParental(): Array<FocusStep> =
        arrayOf(
            *toAdminSection("Parental Controls"),
            center("Kids' Categories / A category shows to a child when its name has one of these words."),
        )

    @Test
    fun `an Expert walks the part and the Exceptions list`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toParental(),
            down("Kids' Film Categories / These also show, for film categories only."),
            down("Adult Categories / A category with one of these words never shows to a child."),
            down("Hidden Titles / A title with one of these words never shows to a child"),
            down("Exceptions / A film or show with one of these words shows to a child"),
            down("Check a Source"),
            down("Content Filters of Each Profile / Set on each profile's page, in Profiles"),
            // The end of the section: This Device is a section of its own.
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
            left("Exceptions / A film or show with one of these words shows to a child"),
            center("Add a word"),
            // Back leaves it too, onto the section, not Admin's list.
            back("Exceptions"),
            // Back again closes the section, focus on its row; once more, the rail.
            back("Parental Controls"),
            back("Admin"),
        )
    }

    @Test
    fun `the Exceptions list ends on Reset`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toParental(),
            *sectionDown(PARENTAL_ROWS, "Kids' Categories", "Exceptions"),
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
    fun `a word removed in Settings comes back on Menu`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toParental(),
            *sectionDown(PARENTAL_ROWS, "Kids' Categories", "Exceptions"),
            center("Add a word"),
            down("pokemon"),
            // OK removes the word: focus on the word that takes its place.
            center("doraemon"),
        )
        walk.press(Key.Menu)
        walk.awaitFocused("pokemon")
        walk.run(down("doraemon"))
    }

    @Test
    fun `Check a Source and the link to Profiles`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toParental(),
            *sectionDown(PARENTAL_ROWS, "Kids' Categories", "Check a Source"),
            // The child's only source: its row first, focus on it.
            center("Source One"),
            left("Check a Source"),
            down("Content Filters of Each Profile"),
            // OK opens Admin's Profiles section, focus on its first row; Back closes it on its row.
            center("Set a PIN for Settings"),
            back("Profiles"),
            back("Admin"),
        )
    }
}
