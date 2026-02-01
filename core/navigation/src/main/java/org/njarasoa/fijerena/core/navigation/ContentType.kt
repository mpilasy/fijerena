package org.njarasoa.fijerena.core.navigation

/**
 * Content types available in the IPTV application.
 */
enum class ContentType(val displayName: String) {
    LIVE_TV("Live TV"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows");

    companion object {
        fun fromString(value: String): ContentType? {
            return entries.find { it.name == value }
        }
    }
}
