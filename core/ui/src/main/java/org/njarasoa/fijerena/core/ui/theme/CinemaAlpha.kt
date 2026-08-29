package org.njarasoa.fijerena.core.ui.theme

/**
 * Opacity / Alpha Constants
 * Centralizes all alpha values used across the app for consistent transparency.
 */
object CinemaAlpha {
    // Text
    const val textHigh = 0.87f // Primary readable text
    const val textMedium = 0.7f // Secondary labels
    const val textLow = 0.6f // Descriptions, metadata
    const val textDisabled = 0.5f // Disabled/placeholder text
    const val textFaint = 0.4f // Disabled content

    // Surfaces & Overlays
    const val overlayHeavy = 0.85f // Stats overlay, dialogs
    const val overlayMedium = 0.8f // Medium overlay background
    const val glass = 0.75f // Glassmorphism background
    const val scrim = 0.5f // Standard scrim
    const val tint = 0.3f // Progress track, selected items
    const val focusedTint = 0.2f // Focused container background
    const val glassBorder = 0.15f // Glass border
    const val divider = 0.1f // Divider opacity
    const val cardHairline = 0.12f // Faint top-highlight border on list-row cards
    const val ghost = 0.05f // Barely visible tints

    // Image overlays
    const val imageOverlay = 0.8f // Gradient over poster for text legibility
    const val imageOverlayLight = 0.4f // Lighter partial overlay
    const val cardElevationShadow = 0.3f // Shadow around focused cards

    // Focus/Interaction
    const val focusedGlow = 0.4f // Glow elevation color

    // Hero cards (Content Type Selection)
    const val heroSheen = 0.12f // Diagonal gloss highlight over a hero card's gradient fill
    const val heroChipBackground = 0.18f // Frosted chip behind a hero card's icon or count
}
