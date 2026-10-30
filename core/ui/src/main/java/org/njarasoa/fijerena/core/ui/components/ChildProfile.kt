package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.AppSettings

/**
 * Whether the profile in use is a child's. A child's film and show pages show no technical rows
 * (Video, Audio, Subtitles, Container, Stream Name, bitrate, TMDB id) and no language tags on
 * their cards (UX-D5); an adult's show them all.
 */
@Composable
fun rememberChildProfile(): Boolean {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppSettings(context).let { it.isChildProfile(it.activeProfileId) } }
}
