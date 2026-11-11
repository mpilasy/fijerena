package org.njarasoa.fijerena.core.ui.theme

import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.network.PackagedUiTheme
import org.njarasoa.fijerena.core.network.UiThemes
import org.njarasoa.fijerena.core.ui.R
import java.text.Collator

/** A UI theme's name: Custom, its colour theme's when they share the id (Console, Meadow, Meadow dusk), else its Look and Feel's. */
@StringRes
fun uiThemeNameRes(id: String): Int =
    UiThemes.packaged(id)?.let { theme ->
        AllPalettes.firstOrNull { it.id == theme.id }?.nameRes ?: styleById(theme.uiStyleId).nameRes
    } ?: R.string.ui_theme_custom

/** The packaged UI themes [admin] may pick (an Admin-only one stays listed while [current]). */
fun packagedUiThemesFor(
    admin: Boolean,
    current: String,
): List<PackagedUiTheme> = UiThemes.packaged.filter { admin || !it.adminOnly || it.id == current }

/**
 * The UI theme picker's order: Custom, then Standard, then the rest by [label] in the app's
 * language, as the Theme and Look and Feel pickers are.
 */
fun <T> List<T>.sortedAsUiThemes(
    id: (T) -> String,
    label: (T) -> String,
): List<T> {
    val collator = Collator.getInstance()
    return sortedWith(
        compareBy<T> {
            when (id(it)) {
                UiThemes.CUSTOM -> 0
                UiThemes.STANDARD -> 1
                else -> 2
            }
        }.thenComparing({ label(it) }, collator),
    )
}
