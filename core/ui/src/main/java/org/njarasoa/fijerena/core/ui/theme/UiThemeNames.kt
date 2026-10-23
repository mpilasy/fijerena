package org.njarasoa.fijerena.core.ui.theme

import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.network.PackagedUiTheme
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.ui.R

/** A UI theme's name: Custom, its colour theme's when they share the id (Console, Meadow, Meadow dusk), else its Look and Feel's. */
@StringRes
fun uiThemeNameRes(id: String): Int =
    UiThemes.packaged(id)?.let { theme ->
        AllPalettes.firstOrNull { it.id == theme.id }?.nameRes ?: styleById(theme.uiStyleId).nameRes
    } ?: R.string.ui_theme_custom

/** The packaged UI themes [admin] may pick (an Expert-only one stays listed while [current]). */
fun packagedUiThemesFor(
    admin: Boolean,
    current: String,
): List<PackagedUiTheme> = UiThemes.packaged.filter { admin || !it.expertOnly || it.id == current }
