package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/**
 * The best channel of a ringing reminder's game: the first channel [GameChannelMatcher] gives, on
 * the live channels of the profile in use and its guide. Only for the profile in use: the hidden
 * live categories in `xtream_categories` are that profile's. Another profile's reminder rings
 * without a channel. A read that fails or takes longer than [TIMEOUT_MS] gives no channel.
 */
object ReminderChannelFinder {
    private const val TAG = "ReminderChannelFinder"
    private const val TIMEOUT_MS = 5_000L

    suspend fun find(
        context: Context,
        profileId: String,
        game: SportsGame,
    ): ReminderChannel? {
        if (AppSettings(context).activeProfileId != profileId) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val channels = liveChannels(context)
                if (channels.isEmpty()) return@withTimeoutOrNull null
                GameChannelMatcher(EpgGuideLookup.load(context))
                    .match(game, channels)
                    .firstNotNullOfOrNull { it.stream }
                    ?.let { ReminderChannel(it.providerId, it.streamId, it.categoryId, it.name) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Best channel of ${game.id}: ${e.message}")
            null
        }
    }

    /**
     * The live channels of the profile in use, of every source that can have channels, hidden as
     * the guide search hides them (an excluded channel, or one in an excluded live category).
     */
    suspend fun liveChannels(context: Context): List<LiveChannel> {
        val sources = ProviderRepository(context).profileSourcesList()
        val db = XtreamDatabase.getInstance(context)
        return withContext(Dispatchers.IO) {
            GuideScope.searchedProviders(sources).flatMap { provider ->
                val hidden =
                    db
                        .categoryDao()
                        .getAllCategoriesIncludingExcluded(provider.id, XtreamCategoryEntity.TYPE_LIVE)
                        .filter { it.excluded }
                        .mapTo(HashSet()) { it.categoryId }
                db.streamDao().getAllStreamsIncludingExcluded(provider.id, XtreamStreamEntity.TYPE_LIVE).map {
                    LiveChannel.of(it, hidden = it.excluded || it.categoryId in hidden)
                }
            }
        }
    }
}
