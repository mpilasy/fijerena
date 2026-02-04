package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Provides the active provider ID to the composition tree.
 * Default 0L means legacy single-provider mode (uses "xtream_cache").
 */
val LocalActiveProviderId = compositionLocalOf { 0L }
