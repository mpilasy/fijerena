package org.njarasoa.fijerena.core.network

/**
 * The look a profile uses: its colour theme, its Look and Feel, and whether a poster shows behind
 * Home and the lists (the blurred wash, and the title art on TV Home).
 */
data class ProfileLook(
    val themeId: String,
    val uiStyleId: String,
    val posterBackdrop: Boolean,
)

/**
 * A packaged UI theme: it sets the colour theme and the Look and Feel together, and while a
 * profile uses it these cannot be changed one by one. The poster background stays the profile's own
 * (the UI themes plan). [adminOnly]: only an Admin can pick it (a light theme).
 */
data class PackagedUiTheme(
    val id: String,
    val themeId: String,
    val uiStyleId: String,
    val adminOnly: Boolean = false,
)

object UiThemes {
    /** No packaged UI theme: the colour theme and the Look and Feel are picked one by one. */
    const val CUSTOM = "custom"

    /** The first packaged theme, listed right after Custom. */
    const val STANDARD = "standard"

    /**
     * The packaged UI themes, in list order: Standard and Slate; the streaming-service looks; Clay
     * and Indigo; the platform-inspired Looks and Feels (Rounded, Sharp, Balanced), each with a
     * colour theme under the Look's name; then Console, Meadow dusk and Meadow.
     */
    val packaged: List<PackagedUiTheme> =
        listOf(
            PackagedUiTheme(STANDARD, themeId = "deep_night", uiStyleId = "material"),
            // Standard's system font and round corners over neutral greys with a light blue accent.
            PackagedUiTheme("slate", themeId = "slate", uiStyleId = "material"),
            // Streaming-service looks, each under its colour theme's name.
            PackagedUiTheme("crimson", themeId = "crimson", uiStyleId = "bold"),
            PackagedUiTheme("forest", themeId = "forest", uiStyleId = "groove"),
            PackagedUiTheme("aurora", themeId = "aurora", uiStyleId = "modern"),
            PackagedUiTheme("royal", themeId = "royal", uiStyleId = "spotlight"),
            // A calm, paper-like look: terracotta on warm charcoal, serif titles.
            PackagedUiTheme("clay", themeId = "clay", uiStyleId = "calm"),
            // A streaming-radio look: an indigo ground, a white accent, yellow badges.
            PackagedUiTheme("indigo", themeId = "indigo", uiStyleId = "dial"),
            PackagedUiTheme("rounded", themeId = "midnight", uiStyleId = "cupertino"),
            PackagedUiTheme("sharp", themeId = "amethyst", uiStyleId = "roku"),
            PackagedUiTheme("balanced", themeId = "amoled_black", uiStyleId = "bravia"),
            // See the Console look plan and the Meadow look plan.
            PackagedUiTheme("console", themeId = "console", uiStyleId = "console"),
            PackagedUiTheme("meadow_dusk", themeId = "meadow_dusk", uiStyleId = "meadow"),
            // Light, so kept for admins as the Meadow and Daylight colour themes are.
            PackagedUiTheme("meadow", themeId = "meadow", uiStyleId = "meadow", adminOnly = true),
        )

    /** The packaged UI theme [id] names; null for Custom and for an id this build does not know. */
    fun packaged(
        id: String,
        all: List<PackagedUiTheme> = packaged,
    ): PackagedUiTheme? = all.firstOrNull { it.id == id }

    /** The look in use: the packaged UI theme's colour theme and Look, else the profile's own ([stored]). */
    fun resolveLook(
        uiThemeId: String,
        stored: ProfileLook,
        all: List<PackagedUiTheme> = packaged,
    ): ProfileLook = packaged(uiThemeId, all)?.let { stored.copy(themeId = it.themeId, uiStyleId = it.uiStyleId) } ?: stored
}
