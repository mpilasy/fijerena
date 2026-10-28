package org.njarasoa.fijerena.feature.settings

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
    /** From the rail to Admin's last profile row, Add Profile: the part's rows come next. */
    private fun toAddProfile(): Array<FocusStep> =
        arrayOf(
            down("Admin"),
            right("Set a PIN for Settings"),
            down("A / Admin"),
            *adminDown("A / Admin", "Add Profile"),
        )

    @Test
    fun `an Expert walks the part and the Exceptions list`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAddProfile(),
            down("Kids' Categories / A category shows to a child when its name has one of these words."),
            down("Kids' Film Categories / These also show, for film categories only."),
            down("Adult Categories / A category with one of these words never shows to a child."),
            down("Hidden Titles / A title with one of these words never shows to a child"),
            down("Exceptions / A film or series with one of these words shows to a child"),
            down("Check a Source"),
            down("Content Filters of Each Profile / Set on each profile's page, in Profiles"),
            // This Device follows on the same page.
            down("Language"),
            up("Content Filters of Each Profile"),
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
            back("Admin"),
        )
    }

    @Test
    fun `the Exceptions list ends on Reset`() {
        open(SettingsFixture.ADMIN)
        walk.run(
            *toAddProfile(),
            *adminDown("Add Profile", "Exceptions"),
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
            *toAddProfile(),
            *adminDown("Add Profile", "Check a Source"),
            // The child's only source: its row first, focus on it.
            center("Source One"),
            left("Check a Source"),
            down("Content Filters of Each Profile"),
            // OK goes up to Admin's Profiles part, focus on its first row; Back goes to the rail.
            center("Set a PIN for Settings"),
            back("Admin"),
        )
    }
}
