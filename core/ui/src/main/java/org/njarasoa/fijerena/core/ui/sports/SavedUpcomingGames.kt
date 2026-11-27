package org.njarasoa.fijerena.core.ui.sports

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The Upcoming Games row last read, one file per profile under [dir]. Home shows it at once when
 * the app starts, while [UpcomingGamesLoader] reads the row again. A file of other follows than
 * the profile's now is not used.
 */
class SavedUpcomingGames(
    private val dir: File,
) {
    @Serializable
    private data class Saved(
        val keys: Set<String>,
        val games: List<UpcomingGame>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** The row saved for [profileId] with the follows [keys]; null when there is none. */
    suspend fun read(
        profileId: String,
        keys: Set<String>,
    ): List<UpcomingGame>? =
        withContext(Dispatchers.IO) {
            try {
                file(profileId)
                    .takeIf { it.exists() }
                    ?.readText()
                    ?.let { json.decodeFromString<Saved>(it) }
                    ?.takeIf { it.keys == keys }
                    ?.games
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An old or broken file: the row is read again, and the file written again.
                Log.w(TAG, "Saved upcoming games unreadable", e)
                null
            }
        }

    suspend fun write(
        profileId: String,
        keys: Set<String>,
        games: List<UpcomingGame>,
    ) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(profileId).writeText(json.encodeToString(Saved.serializer(), Saved(keys, games)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next start waits for the row, as before.
            Log.w(TAG, "Upcoming games not saved", e)
        }
    }

    private fun file(profileId: String) = File(dir, "upcoming_" + profileId.replace(UNSAFE, "_") + ".json")

    private companion object {
        const val TAG = "SavedUpcomingGames"
        val UNSAFE = Regex("[^A-Za-z0-9_-]")
    }
}
