package org.njarasoa.fijerena.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.njarasoa.fijerena.core.ui.theme.CinemaBorders
import org.njarasoa.fijerena.core.ui.theme.CinemaColorRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaShapeRoles
import org.njarasoa.fijerena.core.ui.theme.CinemaThemeHolder
import org.njarasoa.fijerena.core.ui.theme.CinemaTypeRoles
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaBorders
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaShapes
import org.njarasoa.fijerena.core.ui.theme.LocalCinemaType
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.core.ui.theme.ProvideCinemaTheme
import org.njarasoa.fijerena.core.ui.theme.UiStyle
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
 * [content] drawn in the Look and Feel [style], whatever Look is in use — its type, corners, borders
 * and focus effect: each row of the Look picker shows how that Look would look. Colours stay.
 */
@Composable
fun ProvideLook(
    style: UiStyle,
    content: @Composable () -> Unit,
) {
    val type = remember(style) { cinemaTypeRoles(cinemaTypography(style.type)) }
    CompositionLocalProvider(
        LocalUiStyle provides style,
        LocalCinemaType provides type,
        LocalCinemaShapes provides remember(style) { CinemaShapeRoles.from(style) },
        LocalCinemaBorders provides remember(style) { CinemaBorders.from(style) },
        content = content,
    )
}

/**
 * The phone's theme: [CinemaTheme] from the colour theme [themeId] and the Look and Feel [styleId],
 * and Material 3 themed from it, so its components follow too.
 */
@Composable
fun FirstVideoPlayerTheme(
    themeId: String = "deep_night",
    styleId: String = "modern",
    content: @Composable () -> Unit,
) {
    val palette = remember(themeId) { paletteById(themeId) }
    val style = remember(styleId) { styleById(styleId) }

    // Set the global holders so non-composable code (re-export vals) can read them
    SideEffect {
        CinemaThemeHolder.current = palette
        UiStyleHolder.current = style
    }
    // System-bar icons follow the app's theme, not the system's dark mode: dark icons on a light
    // theme, light icons on a dark one. Set again at each change of orientation: the system sets dark
    // icons while the full-screen player is in landscape and does not set them back.
    val view = LocalView.current
    val orientation = LocalConfiguration.current.orientation
    if (!view.isInEditMode) {
        LaunchedEffect(palette.isLight, orientation) {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = palette.isLight
                    isAppearanceLightNavigationBars = palette.isLight
                }
            }
        }
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
