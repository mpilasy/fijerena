package org.njarasoa.fijerena.core.ui.theme

/**
 * Opacity / Alpha Constants
 * Centralizes all alpha values used across the app for consistent transparency.
 */
object CinemaAlpha {
    // Text
    const val textHigh = 0.87f         // Primary readable text
    const val textMedium = 0.7f        // Secondary labels
    const val textLow = 0.6f           // Descriptions, metadata
    const val textDisabled = 0.5f      // Disabled/placeholder text
    const val textFaint = 0.4f         // Disabled content

    // Surfaces & Overlays - Made slightly more transparent as requested
    const val overlayHeavy = 0.85f     // Stats overlay, dialogs (was 0.95f)
    const val overlayMedium = 0.7f     // Medium overlay background (was 0.85f)
    const val glass = 0.65f            // Glassmorphism background (was 0.75f)
    const val scrim = 0.4f             // Standard scrim (was 0.5f)
    const val tint = 0.3f              // Progress track, selected items
    const val focusedTint = 0.2f       // Focused container background
    const val glassBorder = 0.15f      // Glass border
    const val divider = 0.1f           // Divider opacity
    const val ghost = 0.05f            // Barely visible tints

    // Image overlays
    const val imageOverlay = 0.7f      // Gradient over poster for text legibility (was 0.8f)
    const val imageOverlayLight = 0.3f // Lighter partial overlay (was 0.4f)
    const val cardElevationShadow = 0.2f // Shadow around focused cards (was 0.3f)

    // Focus/Interaction
    const val focusedGlow = 0.4f       // Glow elevation color
}
