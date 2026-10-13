package org.njarasoa.fijerena.core.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.AppSettings

/** Whether source badges show: only while the profile in use has Expert mode (developer mode) on. */
val LocalShowSourceBadges = compositionLocalOf { false }

/** Provides [LocalShowSourceBadges] to [content], following profile switches and the profile's Expert mode. */
@Composable
fun ProvideSourceBadges(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val show by remember(context) { AppSettings(context).observeDevMode().map { (_, on) -> on } }.collectAsState(initial = false)
    CompositionLocalProvider(LocalShowSourceBadges provides show, content = content)
}
