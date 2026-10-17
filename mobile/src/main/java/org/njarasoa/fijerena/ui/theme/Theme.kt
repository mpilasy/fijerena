package org.njarasoa.fijerena.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import org.njarasoa.fijerena.core.ui.theme.CinemaColorRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.CinemaTypeRoles
import org.njarasoa.fijerena.core.ui.theme.ProvideCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.UiStyleHolder
import org.njarasoa.fijerena.core.ui.theme.cinemaMaterialColorScheme
import org.njarasoa.fijerena.core.ui.theme.cinemaMaterialShapes
import org.njarasoa.fijerena.core.ui.theme.paletteById
import org.njarasoa.fijerena.core.ui.theme.styleById

/** [CinemaTheme]'s text roles from the phone's typography (which carries the Look and Feel). */
private fun cinemaTypeRoles(typography: Typography) =
    CinemaTypeRoles(
        display = typography.headlineMedium,
        title = typography.titleLarge,
        heading = typography.titleMedium,
        body = typography.bodyMedium,
        bodySmall = typography.bodySmall,
        label = typography.labelLarge,
        caption = typography.labelSmall,
    )

/**
 * The phone's theme: [CinemaTheme] from the colour theme [themeId] and the Look and Feel [styleId],
 * and Material 3 themed from it, so its components follow too.
 */
@Composable
fun FirstVideoPlayerTheme(
    themeId: String = "deep_night",
    styleId: String = "material",
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    val style = remember(styleId) { styleById(styleId) }

    // Set the global holders so non-composable code (re-export vals) can read them
    SideEffect {
        CinemaThemeHolder.current = palette
        UiStyleHolder.current = style
    }

    val typography = remember(style) { cinemaTypography(style.type) }
    ProvideCinemaTheme(palette, style, remember(typography) { cinemaTypeRoles(typography) }) {
        MaterialTheme(
            colorScheme = remember(palette) { cinemaMaterialColorScheme(CinemaColorRoles.from(palette)) },
            typography = typography,
            shapes = remember(style) { cinemaMaterialShapes(style) },
            content = content,
        )
    }
}
