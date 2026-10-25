package org.njarasoa.fijerena.core.ui.wanted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

/** More Like This for a child profile: only titles in its sources that the parental rules allow. */
class RelatedForChildTest {
    private val rules = ParentalRules(hiddenTitles = listOf("scary"))

    private fun film(
        id: String,
        name: String = id,
    ) = MediaItem(id = id, name = name, mediaType = MediaType.MOVIE, categoryId = "kids")

    @Test
    fun `a title no source has goes`() {
        val related = RelatedTitles(moreLikeThis = listOf(film("1"), film(TmdbOnlyItem.PREFIX + "2")))
        assertEquals(listOf("1"), related.forChild(rules).moreLikeThis.map { it.id })
    }

    @Test
    fun `a title the rules hide goes`() {
        val related = RelatedTitles(moreLikeThis = listOf(film("1", "Toy Story"), film("2", "Scary Movie")))
        assertEquals(listOf("1"), related.forChild(rules).moreLikeThis.map { it.id })
    }

    @Test
    fun `an emptied collection loses its name`() {
        val related = RelatedTitles(collection = listOf(film("2", "Scary Movie 2")), collectionName = "Scary Movie Collection")
        val shown = related.forChild(rules)
        assertEquals(emptyList<MediaItem>(), shown.collection)
        assertNull(shown.collectionName)
    }
}
