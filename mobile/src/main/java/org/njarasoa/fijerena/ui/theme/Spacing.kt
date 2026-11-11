package org.njarasoa.fijerena.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * Re-exports shared spacing values and adds mobile-specific safe margins. The steps follow the Look
 * and Feel's density ([CinemaSpacing]); code outside composition uses [CinemaSpacing.Base].
 */
object Spacing {
    // Base spacing (8dp grid), from the shared core.
    val none: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.none

    val xxxs: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.xxxs

    val xxs: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.xxs

    val xs: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.xs

    val sm: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.sm

    val md: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.md

    val lg: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.lg

    val xl: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.xl

    val xxl: Dp
        @Composable @ReadOnlyComposable
        get() = CinemaSpacing.xxl

    // Mobile safe margins (standard Material padding).
    val mobileSafeMarginHorizontal: Dp = MobileDimensions.safeMarginHorizontal
    val mobileSafeMarginVertical: Dp = MobileDimensions.safeMarginVertical

    // Component spacing.
    val cardPadding: Dp
        @Composable @ReadOnlyComposable
        get() = md

    val buttonPadding: Dp
        @Composable @ReadOnlyComposable
        get() = md

    val listSpacing: Dp
        @Composable @ReadOnlyComposable
        get() = sm

    val sectionSpacing: Dp
        @Composable @ReadOnlyComposable
        get() = lg
}
