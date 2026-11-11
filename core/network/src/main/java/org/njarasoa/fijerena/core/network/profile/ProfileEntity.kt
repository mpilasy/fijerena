package org.njarasoa.fijerena.core.network.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person using the app. Favourites, watch state, Recent Categories and the last-position
 * bookmarks are scoped to one profile; providers, EPG sources and settings are shared by all of
 * them, though each profile has its own set of sources (`AppSettings.profileSourceKeys`). See the
 * live sync plan → User profiles.
 *
 * [id] is a random UUID for every profile the user creates, except the one every install starts
 * with, [DEFAULT_ID]: a fixed id, so every device's data from before sync joins one profile instead
 * of each device adding its own "Default".
 *
 * [colorIndex] picks the avatar colour from the UI's fixed palette (`CinemaProfileColors` in
 * core:ui): an index, not a colour value, so the palette can change with no migration.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "Default"
    }
}
