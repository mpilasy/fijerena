package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.deviceinfo.hasAudioPassthrough

/** Whether Night Audio is on, following every change (the player menu, This Device). */
@Composable
fun rememberNightAudioOn(): Boolean {
    val settings = rememberAppSettings()
    val on by remember(settings) { settings.observeNightAudio() }.collectAsStateWithLifecycle(settings.nightAudio)
    return on
}

/** Sets Night Audio. Anyone may, from the player: it is no Admin setting there (no PIN). */
@Composable
fun rememberNightAudioSetter(): (Boolean) -> Unit {
    val settings = rememberAppSettings()
    return remember(settings) { { on: Boolean -> settings.nightAudio = on } }
}

/**
 * The switch's summary, and on a device with passthrough the note that surround sound is decoded
 * while it is on (the Night Audio plan, NA-D5).
 */
@Composable
fun nightAudioDescription(): String {
    val context = LocalContext.current
    val summary = stringResource(R.string.night_audio_summary)
    val note = stringResource(R.string.night_audio_passthrough_note)
    return remember(context, summary, note) {
        if (hasAudioPassthrough(context.applicationContext)) "$summary\n$note" else summary
    }
}

@Composable
private fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember(context) { AppSettings(context.applicationContext) }
}
