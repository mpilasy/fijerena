package org.njarasoa.fijerena.core.network.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.EpgSourceEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.xtream.db.FavoriteStateEntity
import org.njarasoa.fijerena.core.network.xtream.db.WatchStateEntity

/**
 * The payload of each [SyncKind], as it travels in [SyncRecord.payload]: only what another device
 * needs to recreate the item — never local ids (they differ per device), sync statistics or
 * per-device state such as which provider is active or a Jellyfin session token. See
 * `docs/plans/20260929_live-sync-plan.md` → What syncs.
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
                    e.itemName, e.categoryId, e.positionMs, e.durationMs, e.isCompleted, e.updatedAt, e.lastPlayedAt,
                    e.seriesId, e.episodeId, e.seriesName, e.episodeExtension, e.audioTrackIndex, e.subtitleTrackIndex,
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

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> decode(payload: String?): T = json.decodeFromString(requireNotNull(payload) { "Record has no payload" })
}
