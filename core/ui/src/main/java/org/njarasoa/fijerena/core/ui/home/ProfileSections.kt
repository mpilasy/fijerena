package org.njarasoa.fijerena.core.ui.home

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.di.AppContainer

/** A source the profile browses on its own, through its "Browse <name>" entry (O1). */
data class BrowseSource(
    val providerId: Long,
    val name: String,
)

/**
 * What the profile in use browses (docs/plans/20261012_multi-source-search-history-plan.md →
 * Phase 6): Live TV, Movies and TV Shows over its Xtream sources, merged — the union of their
 * sections — and a Browse entry for each of its other sources (Jellyfin, M3U…), which stay out of
 * the merged lists (O1).
 */
data class ProfileSections(
    /**
     * The merged sections' content types; null when the profile has no Xtream source, where the
     * sections stay the active source's own, as before.
     */
    val merged: Set<String>?,
    /** The profile's other sources, in its order; not the active one while the sections are its own. */
    val browse: List<BrowseSource>,
) {
    /** What a Browse entry opens. */
    sealed interface Opening {
        /** This profile has no login for the source (a Jellyfin server): its sign-in. */
        data object SignIn : Opening

        /** The source's sections, in rail / bar order (Live TV, Movies, TV Shows). */
        data class Sections(
            val contentTypes: List<String>,
        ) : Opening
    }

    companion object {
        private const val TAG = "ProfileSections"
        private const val XTREAM = "XTREAM"

        /** Every Xtream source has the three sections. */
        private val XTREAM_SECTIONS = setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)

        /** Section order, as the rail and the bottom bar show them. */
        private val SECTION_ORDER = listOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS)

        /**
         * [sources] are the profile's, in its order; [noChannels] the Xtream ones whose last sync
         * found no live categories (Live TV is left out when every one of them has none, as one
         * source's empty Live TV was); [activeId] the active source.
         */
        internal fun of(
            sources: List<ProviderEntity>,
            noChannels: Set<Long>,
            activeId: Long?,
        ): ProfileSections {
            val xtream = sources.filter { it.type == XTREAM }
            val merged =
                when {
                    xtream.isEmpty() -> null
                    xtream.all { it.id in noChannels } -> XTREAM_SECTIONS - ContentType.LIVE_TV
                    else -> XTREAM_SECTIONS
                }
            val browse =
                sources
                    .filter { it.type != XTREAM && (merged != null || it.id != activeId) }
                    .map { BrowseSource(it.id, it.name) }
            return ProfileSections(merged, browse)
        }

        /** [of] for the profile in use: its sources, and a count of each synced Xtream one's live categories. */
        suspend fun load(context: Context): ProfileSections =
            withContext(Dispatchers.IO) {
                val providerRepo = ProviderRepository(context.applicationContext)
                val container = AppContainer.getInstance(context.applicationContext)
                val sources = providerRepo.profileSourcesList()
                val noChannels =
                    sources
                        .filter { it.type == XTREAM && it.lastSyncedAtMs > 0 }
                        .filter { entity ->
                            // Only stored data: a count of the source's live categories, no network call.
                            suspendRunCatching {
                                (container.getStoredDataRepository(entity.id).getProvider() as? XtreamMediaProvider)
                                    ?.getCategoryTotalCount(ContentType.LIVE_TV) == 0
                            }.onFailure { Log.w(TAG, "Source ${entity.id}'s channels unknown", it) }
                                .getOrDefault(false)
                        }.mapTo(HashSet()) { it.id }
                of(sources, noChannels, providerRepo.getActiveProvider()?.id)
            }

        /** [contentTypes] in section order. */
        internal fun inSectionOrder(contentTypes: Set<String>): List<String> = SECTION_ORDER.filter { it in contentTypes }

        /**
         * What [providerId]'s Browse entry opens: its sign-in while this profile has no login for
         * it (no repository is built for it then: it could only fail to authenticate), else its
         * sections. Builds (and connects) its repository, as opening its list would.
         */
        suspend fun opening(
            context: Context,
            providerId: Long,
        ): Opening =
            withContext(Dispatchers.IO) {
                val providerRepo = ProviderRepository(context.applicationContext)
                val entity = providerRepo.getProviderById(providerId)
                if (entity != null && !providerRepo.hasLogin(entity)) {
                    Opening.SignIn
                } else {
                    val types =
                        AppContainer
                            .getInstance(context.applicationContext)
                            .getMediaRepository(providerId)
                            .getProvider()
                            ?.capabilities
                            ?.supportedContentTypes
                            .orEmpty()
                    Opening.Sections(inSectionOrder(types))
                }
            }
    }
}
