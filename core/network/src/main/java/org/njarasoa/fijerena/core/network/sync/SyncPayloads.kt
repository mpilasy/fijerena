package org.njarasoa.fijerena.core.network.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity
import org.njarasoa.fijerena.core.player.model.NowPlayingSnapshot

/**
 * The payload of each [SyncKind], as it travels in [SyncRecord.payload]: only what another device
 * needs to recreate the item — never local ids (they differ per device), sync statistics or
 * per-device state such as which provider is active or a Jellyfin session token. See
 * `docs/plans/archive/20260929_live-sync-plan.md` → What syncs.
 */
object SyncPayloads {
    val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Watch(
        val itemName: String,
        val categoryId: String,
        val positionMs: Long,
        val durationMs: Long,
        val isCompleted: Boolean,
        val updatedAt: Long,
        val lastPlayedAt: Long? = null,
        val seriesId: String? = null,
        val episodeId: String? = null,
        val seriesName: String? = null,
        val episodeExtension: String? = null,
        val audioTrackIndex: Int? = null,
        val subtitleTrackIndex: Int? = null,
    ) {
        fun toEntity(
            providerId: Long,
            profileId: String,
            itemId: String,
            contentType: String,
        ) = WatchStateEntity(
            providerId = providerId,
            profileId = profileId,
            itemId = itemId,
            contentType = contentType,
            itemName = itemName,
            categoryId = categoryId,
            positionMs = positionMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
            updatedAt = updatedAt,
            lastPlayedAt = lastPlayedAt,
            seriesId = seriesId,
            episodeId = episodeId,
            seriesName = seriesName,
            episodeExtension = episodeExtension,
            audioTrackIndex = audioTrackIndex,
            subtitleTrackIndex = subtitleTrackIndex,
        )

        companion object {
            fun of(e: WatchStateEntity) =
                Watch(
                    e.itemName,
                    e.categoryId,
                    e.positionMs,
                    e.durationMs,
                    e.isCompleted,
                    e.updatedAt,
                    e.lastPlayedAt,
                    e.seriesId,
                    e.episodeId,
                    e.seriesName,
                    e.episodeExtension,
                    e.audioTrackIndex,
                    e.subtitleTrackIndex,
                )
        }
    }

    @Serializable
    data class Favorite(
        val name: String,
        val parentCategoryId: String? = null,
        val createdAt: Long,
    ) {
        companion object {
            fun of(e: FavoriteStateEntity) = Favorite(e.name, e.parentCategoryId, e.createdAt)
        }
    }

    /** A provider. [password] is the shared login — for Jellyfin, the Default profile's. Phase 8 encrypts it. */
    @Serializable
    data class Provider(
        val name: String,
        val url: String,
        val username: String,
        val type: String,
        val config: String,
        val providerSettings: String,
        val password: String? = null,
    ) {
        companion object {
            fun of(
                e: ProviderEntity,
                password: String?,
            ) = Provider(e.name, e.url, e.username, e.type, e.config, e.providerSettings, password)
        }
    }

    /** A profile's own Jellyfin login. No session token: each device signs in itself. */
    @Serializable
    data class Login(
        val username: String,
        val password: String? = null,
    )

    @Serializable
    data class EpgSource(
        val providerKey: String,
        val url: String,
        val label: String,
        val timezoneOffsetHours: Int,
        val enabled: Boolean,
    ) {
        companion object {
            fun of(
                e: EpgSourceEntity,
                providerKey: String,
            ) = EpgSource(providerKey, e.url, e.label, e.timezoneOffsetHours, e.enabled)
        }
    }

    @Serializable
    data class Profile(
        val name: String,
        val colorIndex: Int,
        val createdAt: Long,
    ) {
        companion object {
            fun of(e: ProfileEntity) = Profile(e.name, e.colorIndex, e.createdAt)
        }
    }

    @Serializable
    data class Setting(
        val value: JsonPrimitive,
    )

    /**
     * What a device is playing ([SyncKind.NOW_PLAYING]). [sentAt] is the sender's wall clock, for
     * staleness: a device switched off at the wall never sends [STOPPED].
     */
    @Serializable
    data class NowPlaying(
        val state: String,
        val title: String = "",
        val showTitle: String? = null,
        val episodeLabel: String? = null,
        val isLive: Boolean = false,
        val channelName: String? = null,
        val programTitle: String? = null,
        val profileName: String = "",
        val positionMs: Long? = null,
        val durationMs: Long? = null,
        val sentAt: Long,
        /** The sender's playback id — what a remote Stop names. Null from a sender before Phase 4. */
        val sessionId: String? = null,
    ) {
        companion object {
            const val PLAYING = "playing"
            const val PAUSED = "paused"
            const val STOPPED = "stopped"

            /** [snapshot] null: nothing is playing. */
            fun of(
                snapshot: NowPlayingSnapshot?,
                profileName: String,
                sentAt: Long,
            ): NowPlaying =
                if (snapshot == null) {
                    NowPlaying(state = STOPPED, profileName = profileName, sentAt = sentAt)
                } else {
                    NowPlaying(
                        state = if (snapshot.paused) PAUSED else PLAYING,
                        title = snapshot.title,
                        showTitle = snapshot.showTitle,
                        episodeLabel = snapshot.episodeLabel,
                        isLive = snapshot.isLive,
                        channelName = snapshot.channelName,
                        programTitle = snapshot.programTitle,
                        profileName = profileName,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        sentAt = sentAt,
                        sessionId = snapshot.sessionId,
                    )
                }
        }
    }

    /**
     * A command to the device the key names ([SyncKind.REMOTE_COMMAND]). The target obeys only if
     * [sessionId] is the playback it has on right now — never by clock, so a command re-read
     * later (a resync from 0) can't match anything. [fromDeviceName] is what the target shows
     * ("Playback stopped from Pixel 8"); [issuedBy] is the sender's server device id — the
     * device, not a profile: the command is from one device to another, and the id joins the
     * devices list. Kept for diagnostics; nothing checks it (any device of the group may send).
     */
    @Serializable
    data class RemoteCommand(
        val command: String,
        val sessionId: String,
        val fromDeviceName: String = "",
        val issuedBy: String = "",
    ) {
        companion object {
            const val STOP = "stop"
        }
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(payload: String?): T = json.decodeFromString(requireNotNull(payload) { "Record has no payload" })
}
