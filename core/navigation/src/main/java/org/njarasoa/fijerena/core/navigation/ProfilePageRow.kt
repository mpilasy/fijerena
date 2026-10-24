package org.njarasoa.fijerena.core.navigation

/**
 * The rows of a profile's page that Settings opens directly: Display's "Theme and Look and Feel"
 * opens the page at [LOOK], Playback's "Play next episode automatically" at [AUTOPLAY]
 * (settings flow review, G1).
 */
enum class ProfilePageRow {
    LOOK,
    AUTOPLAY,
    ;

    companion object {
        /** The row with this [name], or null for null or an unknown name (the top of the page). */
        fun of(name: String?): ProfilePageRow? = entries.find { it.name == name }
    }
}
