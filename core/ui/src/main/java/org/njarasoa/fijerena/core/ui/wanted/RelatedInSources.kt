package org.njarasoa.fijerena.core.ui.wanted

import android.content.Context
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.wanted.CatalogueLookup
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

/** The profile's Xtream sources: the ones a [CatalogueLookup] searches. */
suspend fun profileXtreamSources(context: Context): List<Long> =
    ProviderRepository(context)
        .profileSourcesList()
        .filter { it.type == "XTREAM" }
        .map { it.id }

/**
 * [related] with each "no source" card that a profile source has turned into that source's card
 * ([CatalogueLookup.inSources]); for a child profile, only what the child may see ([forChild]).
 * Blocking: call it on `Dispatchers.IO`.
 */
suspend fun relatedInSources(
    context: Context,
    related: RelatedTitles,
    contentType: String,
): RelatedTitles {
    val inSources = CatalogueLookup(XtreamDatabase.getInstance(context)).inSources(related, profileXtreamSources(context), contentType)
    val settings = AppSettings(context)
    return if (settings.isChildProfile(settings.activeProfileId)) inSources.forChild(settings.parentalRules()) else inSources
}

/**
 * These rows as a child profile may see them, as Search shows them: no card of a title no source
 * has (it opens the Wishlist, which a child has not), and no title the parental rules hide. The
 * categories were filtered already: every catalogue card comes from queries that leave out the
 * child's hidden categories.
 */
fun RelatedTitles.forChild(rules: ParentalRules): RelatedTitles {
    fun List<MediaItem>.allowed() = filterNot { it.id.startsWith(TmdbOnlyItem.PREFIX) || rules.hidesTitle(it.name) }
    val shownCollection = collection.allowed()
    return copy(
        collection = shownCollection,
        collectionName = collectionName.takeIf { shownCollection.isNotEmpty() },
        moreLikeThis = moreLikeThis.allowed(),
    )
}
