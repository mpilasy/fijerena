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
 * (docs/plans/20261021_ui-themes-plan.md). [expertOnly]: only an Expert can pick it (a light theme).
 */
data class PackagedUiTheme(
    val id: String,
    val themeId: String,
    val uiStyleId: String,
    val expertOnly: Boolean = false,
)

object UiThemes {
    /** No packaged UI theme: the colour theme and the Look and Feel are picked one by one. */
    const val CUSTOM = "custom"

    /**
     * The packaged UI themes. The first four pair each platform-inspired Look and Feel with a colour
     * theme, under the Look's name (user, 2026-10-21); then Console, Meadow dusk and Meadow.
     */
    val packaged: List<PackagedUiTheme> =
        listOf(
            PackagedUiTheme("standard", themeId = "deep_night", uiStyleId = "material"),
            PackagedUiTheme("rounded", themeId = "midnight", uiStyleId = "cupertino"),
            PackagedUiTheme("sharp", themeId = "amethyst", uiStyleId = "roku"),
            PackagedUiTheme("balanced", themeId = "amoled_black", uiStyleId = "bravia"),
            // docs/plans/20261020_lcars-look-plan.md and docs/plans/20261021_meadow-look-plan.md.
            PackagedUiTheme("console", themeId = "console", uiStyleId = "console"),
            PackagedUiTheme("meadow_dusk", themeId = "meadow_dusk", uiStyleId = "meadow"),
            // Light, so kept for admins as the Meadow and Daylight colour themes are.
            PackagedUiTheme("meadow", themeId = "meadow", uiStyleId = "meadow", expertOnly = true),
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
