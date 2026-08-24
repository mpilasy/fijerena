package org.njarasoa.fijerena.ui.theme

import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

/**
 * Mobile Spacing System - Deep Night Design System
 * Re-exports shared spacing values and adds mobile-specific safe margins.
 */
object Spacing {
    // Base spacing (8dp grid) — delegated to shared core
    val none: Dp = CinemaSpacing.none
    val xxxs: Dp = CinemaSpacing.xxxs
    val xxs: Dp = CinemaSpacing.xxs
    val xs: Dp = CinemaSpacing.xs
    val sm: Dp = CinemaSpacing.sm
    val md: Dp = CinemaSpacing.md
    val lg: Dp = CinemaSpacing.lg
    val xl: Dp = CinemaSpacing.xl
    val xxl: Dp = CinemaSpacing.xxl

    // Mobile-specific safe margins (standard Material padding)
    val mobileSafeMarginHorizontal: Dp = MobileDimensions.safeMarginHorizontal
    val mobileSafeMarginVertical: Dp = MobileDimensions.safeMarginVertical

    // Component-specific spacing
    val cardPadding: Dp = md
    val buttonPadding: Dp = md
    val listSpacing: Dp = sm
    val sectionSpacing: Dp = lg
}
