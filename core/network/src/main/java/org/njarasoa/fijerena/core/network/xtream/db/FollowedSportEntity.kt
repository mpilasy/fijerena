package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A team or a league that a profile follows. Kept per profile and not per provider: it belongs to
 * no source. The Sports section, the Home row and the reminders fetch games only for these. See the
 * sports teams plan → Followed teams and leagues.
 *
 * [kind] is `FollowKind.id` (`TEAM` or `LEAGUE`), [source] is `SportsSource.id` (`espn`, `nhl`, `mlb`)
 * and [itemId] the team or league id at that source. [league] is the source's league key of a team
 * (null for a league). [altNames] is a JSON list of the other names the source gives (location,
 * nickname, abbreviation), stored as text like `xtream_streams.facts`. [name], [shortName],
 * [sport] and [logoUrl] show the item without a network call.
 *
 * Every column is synced (kind `followed_sport`): see [XtreamSyncTriggers].
 */
@Entity(
    tableName = "followed_sport",
    primaryKeys = ["profileId", "kind", "source", "itemId"],
)
data class FollowedSportEntity(
    val profileId: String,
    val kind: String,
    val source: String,
    val itemId: String,
    val league: String? = null,
    val name: String,
    val shortName: String? = null,
    val altNames: String = "[]",
    val sport: String,
    val logoUrl: String? = null,
    val createdAt: Long,
) {
    companion object {
        /**
         * The `contentType` of this row's key in `sync_version`, `sync_tombstone` and the synced
         * record: `<kind>:<source>`, for example `TEAM:espn`. The key has one slot for both, and
         * neither id has a colon. The triggers build the same text in SQL.
         */
        fun syncContentType(
            kind: String,
            source: String,
        ): String = "$kind:$source"

        private val namesSerializer = ListSerializer(String.serializer())

        /** [names] as the text of [altNames]. */
        fun encodeAltNames(names: List<String>): String = Json.encodeToString(namesSerializer, names)

        /** [altNames] read back; an empty list when the text is not a JSON list of strings. */
        fun decodeAltNames(text: String): List<String> =
            try {
                Json.decodeFromString(namesSerializer, text)
            } catch (e: IllegalArgumentException) {
                emptyList()
            }

        /** [syncContentType] read back as kind and source; null when it is not in that form. */
        fun parseSyncContentType(contentType: String): Pair<String, String>? {
            val i = contentType.indexOf(':')
            if (i <= 0 || i == contentType.lastIndex) return null
            return contentType.substring(0, i) to contentType.substring(i + 1)
        }
    }
}
