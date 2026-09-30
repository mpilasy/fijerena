package org.njarasoa.fijerena.core.network.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person using the app. Favourites, watch state, Recent Categories and the last-position
 * bookmarks are scoped to one profile; providers, EPG sources and settings are shared by all of
 * them. See `docs/plans/20260929_live-sync-plan.md` → User profiles.
 *
 * [id] is a random UUID for every profile the user creates, except the one every install starts
 * with, which is [DEFAULT_ID]. A fixed id rather than a per-device UUID so that, once sync lands,
 * every device's pre-existing data converges on the same profile instead of each device
 * contributing its own "Default".
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "Default"
    }
}
