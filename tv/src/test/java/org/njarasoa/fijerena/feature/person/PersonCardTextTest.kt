package org.njarasoa.fijerena.feature.person

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.tmdb.CrewRole
import org.njarasoa.fijerena.core.network.tmdb.PersonRole
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.ui.person.appearanceCode
import org.njarasoa.fijerena.core.ui.person.appearanceLabel
import org.njarasoa.fijerena.core.ui.person.personRoleText
import org.robolectric.RobolectricTestRunner

/**
 * The text of an actor page's cards and of the lines of a person's appearances in a show
 * (docs/plans/20261030_episode-cast-plan.md, EC-D2).
 */
@RunWith(RobolectricTestRunner::class)
class PersonCardTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `a show card says how many episodes, then the role`() {
        assertEquals("1 episode · as Merritt Rook", personRoleText(resources, PersonRole("Merritt Rook", episodeCount = 1)))
        assertEquals("24 episodes · as Olivia Benson", personRoleText(resources, PersonRole("Olivia Benson", episodeCount = 24)))
    }

    @Test
    fun `a film card, a crew card and a card without a role keep their text`() {
        assertEquals("as Paul", personRoleText(resources, PersonRole("Paul")))
        assertEquals("Director, Writer", personRoleText(resources, PersonRole(jobs = listOf(CrewRole.DIRECTOR, CrewRole.WRITER))))
        assertEquals("3 episodes", personRoleText(resources, PersonRole(episodeCount = 3)))
        assertNull(personRoleText(resources, PersonRole()))
        assertNull(personRoleText(resources, null))
    }

    @Test
    fun `a line names the episode, else its number, else the season or the specials`() {
        assertEquals("S9E17 · Authority", appearanceLabel(resources, ShowAppearance(9, 17, "Authority")))
        assertEquals("S9E17", appearanceLabel(resources, ShowAppearance(9, 17)))
        assertEquals("Season 3", appearanceLabel(resources, ShowAppearance(3)))
        assertEquals("Specials", appearanceLabel(resources, ShowAppearance(0)))
        // The note for what the source lacks names the episode by its number only.
        assertEquals("S9E17", appearanceCode(resources, ShowAppearance(9, 17, "Authority")))
    }
}
