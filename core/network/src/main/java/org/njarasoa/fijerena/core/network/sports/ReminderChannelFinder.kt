package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings

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
                    .firstOrNull()
                    ?.stream
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
     * The live channels of the profile in use ([SportsChannels]: one list per profile and
     * catalogue, kept for the process). The one loader of the channels a game is matched to: the
     * sports pages, the Sports section and Home's Upcoming Games read it too.
     */
    suspend fun liveChannels(context: Context): List<LiveChannel> = SportsChannels.get(context)
}
