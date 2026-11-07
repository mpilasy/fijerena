package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/**
 * The live channels a game is matched to, kept for the whole process: one list per profile and
 * catalogue. The list is read again only when its [Key] changes (another profile, a source added,
 * removed or synced, a live category hidden or shown, a channel excluded). The same list object is
 * returned while the key holds, so [GameChannelMatcher] keeps what it built from it (folded names,
 * a word index) across the sports screens, Home and the reminders. The channels are kept without
 * their logos, which only the few channels of a game need ([withIcons]).
 */
object SportsChannels {
    /** What the list depends on: cheap to read, unlike the list itself. */
    internal data class Key(
        val profileId: String,
        val sources: List<String>,
    )

    private class Kept(
        val key: Key,
        val channels: List<LiveChannel>,
    )

    @Volatile private var kept: Kept? = null
    private val lock = Mutex()

    /**
     * The live channels of the profile in use, of every source that can have channels, hidden as
     * the guide search hides them (an excluded channel, or one in an excluded live category).
     */
    suspend fun get(context: Context): List<LiveChannel> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val profileId = AppSettings(app).activeProfileId
            val providers = GuideScope.searchedProviders(ProviderRepository(app).profileSourcesList())
            val db = XtreamDatabase.getInstance(app)
            val hidden =
                providers.associate { provider ->
                    provider.id to
                        db
                            .categoryDao()
                            .getAllCategoriesIncludingExcluded(provider.id, XtreamCategoryEntity.TYPE_LIVE)
                            .filter { it.excluded }
                            .mapTo(HashSet()) { it.categoryId }
                }
            val key =
                Key(
                    profileId,
                    providers.map { provider ->
                        val counts = db.streamDao().streamAndExcludedCounts(provider.id, XtreamStreamEntity.TYPE_LIVE)
                        "${provider.id}:${provider.lastSyncedAtMs}:$counts:${hidden[provider.id].orEmpty().sorted().hashCode()}"
                    },
                )
            kept?.takeIf { it.key == key }?.channels ?: lock.withLock {
                kept?.takeIf { it.key == key }?.channels ?: read(db, providers.map { it.id }, hidden).also {
                    // What the matcher built from the old list goes too: two lists of every channel
                    // would be kept until the next match.
                    GameChannelMatcher.drop()
                    kept = Kept(key, it)
                }
            }
        }

    /** [channels] with the logos of their streams, read for these channels only. */
    suspend fun withIcons(
        context: Context,
        channels: List<GameChannel>,
    ): List<GameChannel> {
        if (channels.isEmpty()) return channels
        val dao = XtreamDatabase.getInstance(context.applicationContext).streamDao()
        val icons =
            withContext(Dispatchers.IO) {
                channels.groupBy { it.stream.providerId }.mapValues { (providerId, list) ->
                    dao.getIconsByIds(providerId, XtreamStreamEntity.TYPE_LIVE, list.map { it.stream.streamId }.distinct())
                }
            }
        return channels.map { channel ->
            icons[channel.stream.providerId]?.get(channel.stream.streamId)?.let { channel.copy(stream = channel.stream.copy(iconUrl = it)) }
                ?: channel
        }
    }

    /**
     * Drops the list (and what the matcher built from it): 40-70 MB for a large source. The next
     * match reads it again. Called when the app goes to the background and when a film starts.
     */
    fun drop() {
        kept = null
        GameChannelMatcher.drop()
    }

    private fun read(
        db: XtreamDatabase,
        providerIds: List<Long>,
        hidden: Map<Long, Set<String>>,
    ): List<LiveChannel> =
        providerIds.flatMap { providerId ->
            val hiddenCategories = hidden[providerId].orEmpty()
            db.streamDao().getAllStreamsIncludingExcluded(providerId, XtreamStreamEntity.TYPE_LIVE).map {
                LiveChannel.of(it, hidden = it.excluded || it.categoryId in hiddenCategories, withIcon = false)
            }
        }
}
