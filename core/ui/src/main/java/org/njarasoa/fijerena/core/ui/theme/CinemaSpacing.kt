package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The factor that multiplies every [CinemaSpacing] step: the Look and Feel's density
 * ([UiStyle.spacing]). The phone's theme provides it. The TV does not, so its spacing stays at 1.0:
 * TV layouts have fixed sizes.
 */
val LocalSpacingScale = staticCompositionLocalOf { 1f }

/**
 * Base Spacing Grid (8dp)
 * Shared spacing values used across TV and Mobile modules. Each step is the [Base] step times the
 * Look and Feel's density ([LocalSpacingScale]), so a Look can be denser or roomier without a change
 * to the screens. Code that runs outside composition (shapes, drawing, a defaults object) uses the
 * [Base] steps.
 */
object CinemaSpacing {
    /** The steps at density 1.0, for code outside composition. */
    object Base {
        val none: Dp = 0.dp
        val xxxs: Dp = 2.dp
        val xxs: Dp = 4.dp
        val xs: Dp = 8.dp // Base unit (8dp grid)
        val sm: Dp = 12.dp
        val md: Dp = 16.dp // Standard spacing (most common)
        val lg: Dp = 24.dp // Section spacing
        val xl: Dp = 32.dp
        val xxl: Dp = 48.dp
    }

    val none: Dp
        @Composable @ReadOnlyComposable
        get() = Base.none

    val xxxs: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.xxxs)

    val xxs: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.xxs)

    val xs: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.xs)

    val sm: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.sm)

    val md: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.md)

    val lg: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.lg)

    val xl: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.xl)

    val xxl: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(Base.xxl)

    /** The gap between the parts of a dialog's body (the Look's [UiDialogTokens.bodyGap]). */
    val dialogBody: Dp
        @Composable @ReadOnlyComposable
        get() = scaled(LocalUiStyle.current.dialog.bodyGap)

    /** [base] times the Look and Feel's density; [base] itself at 1.0. */
    @Composable
    @ReadOnlyComposable
    fun scaled(base: Dp): Dp {
        val factor = LocalSpacingScale.current
        return if (factor == 1f) base else base * factor
    }
}
