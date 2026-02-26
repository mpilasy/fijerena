package org.njarasoa.fijerena.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.model.PlayerMetadata

@Stable
class PlayerScreenState(
    context: Context,
    initialMetadata: PlayerMetadata = PlayerMetadata()
) {
    // UI visibility states
    var showControls by mutableStateOf(false)
    var showStats by mutableStateOf(false)
    var showAudioTrackSelector by mutableStateOf(false)
    var showSubtitleSelector by mutableStateOf(false)
    var showQualitySelector by mutableStateOf(false)
    var showStreamInfo by mutableStateOf(false)
    var showCategoryOverlay by mutableStateOf(false)
    var showLastWatchedOverlay by mutableStateOf(false)
    var showChapterSelector by mutableStateOf(false)
    var showTopOfHourClock by mutableStateOf(false)
    var showControlHints by mutableStateOf(false)

    // Data states
    var clockTick by mutableLongStateOf(0L)
    var livePosition by mutableLongStateOf(0L)
    var liveDuration by mutableLongStateOf(0L)
    var displayedMetadata by mutableStateOf(initialMetadata)
    var previousMetadataTitle by mutableStateOf<String?>(null)
    var isInitialLoad by mutableStateOf(true)

    // Configuration
    val focusRequester = FocusRequester()
    val appSettings = AppSettings(context.applicationContext)
    val isDeveloperMode = appSettings.isDevMode
    val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    init {
        // Initialize showControlHints based on preferences
        showControlHints = false
    }

    fun dismissControlHints() {
        showControlHints = false
    }

    fun markHintsDismissed() {
        prefs.edit().putBoolean("hints_dismissed", true).apply()
        showControlHints = false
    }
}

@Composable
fun rememberPlayerScreenState(
    context: Context,
    initialMetadata: PlayerMetadata = PlayerMetadata()
): PlayerScreenState {
    return remember {
        PlayerScreenState(context, initialMetadata)
    }
}
