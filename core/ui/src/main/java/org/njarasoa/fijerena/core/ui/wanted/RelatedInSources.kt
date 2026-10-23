package org.njarasoa.fijerena.core.ui.wanted

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.wanted.CatalogueLookup
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.player.domain.RelatedTitles

/** The profile's Xtream sources: the ones a [CatalogueLookup] searches. */
suspend fun profileXtreamSources(context: Context): List<Long> =
    ProviderRepository(context)
        .profileSourcesList()
        .filter { it.type == "XTREAM" }
        .map { it.id }

/**
 * [related] with each "no source" card that a profile source has turned into that source's card
 * ([CatalogueLookup.inSources]). Blocking: call it on `Dispatchers.IO`.
 */
suspend fun relatedInSources(
    context: Context,
    related: RelatedTitles,
    contentType: String,
): RelatedTitles = CatalogueLookup(XtreamDatabase.getInstance(context)).inSources(related, profileXtreamSources(context), contentType)
