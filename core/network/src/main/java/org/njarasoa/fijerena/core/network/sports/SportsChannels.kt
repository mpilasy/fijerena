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
 * a word index) across the sports screens, Home and the reminders.
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
                kept?.takeIf { it.key == key }?.channels ?: read(db, providers.map { it.id }, hidden).also { kept = Kept(key, it) }
            }
        }

    private fun read(
        db: XtreamDatabase,
        providerIds: List<Long>,
        hidden: Map<Long, Set<String>>,
    ): List<LiveChannel> =
        providerIds.flatMap { providerId ->
            val hiddenCategories = hidden[providerId].orEmpty()
            db.streamDao().getAllStreamsIncludingExcluded(providerId, XtreamStreamEntity.TYPE_LIVE).map {
                LiveChannel.of(it, hidden = it.excluded || it.categoryId in hiddenCategories)
            }
        }
}
