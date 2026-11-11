package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

/**
 * The guide sources Search the guide reads and [EpgSyncWorker] keeps fresh: the enabled ones of
 * every source of the profile in use that can have channels — Xtream, and M3U ones (Remote M3U,
 * Local), which kept their guide while they could be the source in use
 * (docs/plans/20261012_multi-source-search-history-plan.md, Phases 6 and 7). A guide source can
 * serve more than one source: [providerIdsBySourceId] keeps the searched sources that read each one,
 * in the profile's source order (the one guide per URL plan, Design G).
 */
data class GuideScope(
    val providers: List<ProviderEntity>,
    val providerIdsBySourceId: Map<Long, List<Long>>,
) {
    val sourceIds: List<Long> get() = providerIdsBySourceId.keys.toList()

    /** The searched sources that read the guide source [sourceId], empty when it isn't searched. */
    fun providerIdsOf(sourceId: Long): List<Long> = providerIdsBySourceId[sourceId].orEmpty()

    /** Each source's name by its id when more than one source is searched (their airings are badged), else empty. */
    val sourceNames: Map<Long, String>
        get() = if (providers.size > 1) providers.associate { it.id to it.name } else emptyMap()

    companion object {
        /** The sources of [profileSources] whose guides are searched (see [GuideScope]). */
        fun searchedProviders(profileSources: List<ProviderEntity>): List<ProviderEntity> =
            profileSources.filter { it.type !in NO_CHANNELS }

        /** Source types with no channels, so no guide: Jellyfin and SMB. */
        private val NO_CHANNELS = setOf("JELLYFIN", "SMB")

        internal suspend fun of(
            providers: List<ProviderEntity>,
            enabledSources: suspend (providerId: Long) -> List<EpgSourceEntity>,
        ): GuideScope {
            val bySourceId = LinkedHashMap<Long, MutableList<Long>>()
            for (provider in providers) {
                enabledSources(provider.id).forEach { bySourceId.getOrPut(it.id) { ArrayList() } += provider.id }
            }
            return GuideScope(providers, bySourceId)
        }

        /** The scope of the profile in use; each source's guide sources come from its links. */
        suspend fun load(context: Context): GuideScope =
            of(
                searchedProviders(ProviderRepository(context).profileSourcesList()),
                SettingsDatabase.getInstance(context).epgSourceDao()::getEnabledSourcesForProvider,
            )
    }
}

/**
 * Matches an airing on the channels of the sources that read its guide source: each source's
 * channels ([matchers], by source id) and hidden live categories ([hiddenCategoryIds]) decide.
 * The same guide channel id on two sources matches each source's own stream.
 */
class GuideAiringMatcher(
    private val providerIdsBySourceId: Map<Long, List<Long>>,
    private val matchers: Map<Long, EpgChannelMatcher>,
    private val hiddenCategoryIds: Map<Long, Set<String>>,
) {
    /** The searched sources that read the guide source [sourceId], in the profile's order; empty when it isn't searched. */
    fun providerIdsOf(sourceId: Long): List<Long> = providerIdsBySourceId[sourceId].orEmpty()

    /** One match for each source of [sourceId] that has the channel, in the order of [providerIdsOf]. */
    fun matchAll(
        sourceId: Long,
        channelId: String,
        channelName: String,
    ): List<EpgBrowserMatchedStream> = providerIdsOf(sourceId).mapNotNull { matchers[it]?.match(channelId, channelName) }

    /**
     * The first match that is not hidden. When all matches are hidden, the first one, so that
     * [isHidden] still drops the airing. Null when no source has the channel.
     */
    fun match(
        sourceId: Long,
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? {
        val all = matchAll(sourceId, channelId, channelName)
        return all.firstOrNull { !isHidden(it) } ?: all.firstOrNull()
    }

    /**
     * An excluded channel, or one in a live category its source hides for the profile: it can't be
     * opened, and a filter (a Kid profile's) must not be bypassed through the guide. Checked per
     * match, so each source keeps its own filter.
     */
    fun isHidden(matched: EpgBrowserMatchedStream): Boolean =
        matched.excluded || matched.categoryId in hiddenCategoryIds[matched.providerId].orEmpty()

    /** Whether a search row stays: no source has the channel, or at least one source has it not hidden. */
    fun isShown(
        sourceId: Long,
        channelId: String,
        channelName: String,
    ): Boolean {
        val all = matchAll(sourceId, channelId, channelName)
        return all.isEmpty() || all.any { !isHidden(it) }
    }
}
