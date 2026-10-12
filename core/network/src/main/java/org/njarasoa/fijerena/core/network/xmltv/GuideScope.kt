package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase

/**
 * The guide sources Search the guide reads and [EpgSyncWorker] keeps fresh: the enabled ones of
 * every Xtream source of the profile in use, and of the source in use whatever its type
 * (docs/plans/20261012_multi-source-search-history-plan.md, Phase 7; Jellyfin and M3U sources join
 * later, O1). A guide source belongs to one source, which [providerIdBySourceId] keeps.
 */
data class GuideScope(
    val providers: List<ProviderEntity>,
    val providerIdBySourceId: Map<Long, Long>,
) {
    val sourceIds: List<Long> get() = providerIdBySourceId.keys.toList()

    /** Each source's name by its id when more than one source is searched (their airings are badged), else empty. */
    val sourceNames: Map<Long, String>
        get() = if (providers.size > 1) providers.associate { it.id to it.name } else emptyMap()

    companion object {
        /** The sources of [profileSources] whose guides are searched (see [GuideScope]). */
        fun searchedProviders(profileSources: List<ProviderEntity>): List<ProviderEntity> =
            profileSources.filter { it.isActive || it.type == "XTREAM" }

        internal suspend fun of(
            providers: List<ProviderEntity>,
            enabledSources: suspend (providerId: Long) -> List<EpgSourceEntity>,
        ): GuideScope {
            val bySourceId = LinkedHashMap<Long, Long>()
            for (provider in providers) {
                enabledSources(provider.id).forEach { bySourceId[it.id] = provider.id }
            }
            return GuideScope(providers, bySourceId)
        }

        /** The scope of the profile in use. */
        suspend fun load(context: Context): GuideScope =
            of(
                searchedProviders(ProviderRepository(context).profileSourcesList()),
                SettingsDatabase.getInstance(context).epgSourceDao()::getEnabledSourcesForProvider,
            )
    }
}

/**
 * Matches an airing on a channel of its own source: its guide source names the source, whose
 * channels ([matchers], by source id) and hidden live categories ([hiddenCategoryIds]) decide.
 * The same guide channel id on two sources matches each source's own stream.
 */
class GuideAiringMatcher(
    private val providerIdBySourceId: Map<Long, Long>,
    private val matchers: Map<Long, EpgChannelMatcher>,
    private val hiddenCategoryIds: Map<Long, Set<String>>,
) {
    /** The source the guide source [sourceId] belongs to, 0 when it isn't searched. */
    fun providerIdOf(sourceId: Long): Long = providerIdBySourceId[sourceId] ?: 0L

    fun match(
        sourceId: Long,
        channelId: String,
        channelName: String,
    ): EpgBrowserMatchedStream? = matchers[providerIdOf(sourceId)]?.match(channelId, channelName)

    /**
     * An excluded channel, or one in a live category its source hides for the profile: it can't be
     * opened, and a filter (a Kid profile's) must not be bypassed through the guide.
     */
    fun isHidden(matched: EpgBrowserMatchedStream): Boolean =
        matched.excluded || matched.categoryId in hiddenCategoryIds[matched.providerId].orEmpty()
}
