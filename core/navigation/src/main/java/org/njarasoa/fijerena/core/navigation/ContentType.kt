package org.njarasoa.fijerena.core.navigation

enum class ContentType(
    val displayName: String,
) {
    LIVE_TV("Live TV"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    ;

    companion object {
        fun fromString(value: String): ContentType? = entries.find { it.name == value }
    }
}
