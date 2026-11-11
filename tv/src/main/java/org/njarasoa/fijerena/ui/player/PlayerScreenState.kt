package org.njarasoa.fijerena.ui.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    initialMetadata: PlayerMetadata = PlayerMetadata(),
) {
    var showControls by mutableStateOf(false)
    var showStats by mutableStateOf(false)
    var showAudioTrackSelector by mutableStateOf(false)
    var showSubtitleSelector by mutableStateOf(false)
    var showQualitySelector by mutableStateOf(false)
    private var showStreamInfoState by mutableStateOf(false)

    // Bumped each time showStreamInfo is set to true, so PlayerEffects' auto-hide restarts its delay
    // when a second trigger (another zap) lands while showStreamInfo is already true.
    var showStreamInfoTick by mutableIntStateOf(0)
        private set

    var showStreamInfo: Boolean
        get() = showStreamInfoState
        set(value) {
            showStreamInfoState = value
            if (value) showStreamInfoTick++
        }

    // Live TV's channel panel (LT3), open over the video. Left or Right opens it; Back closes it.
    var showChannelPanel by mutableStateOf(false)
    var showChapterSelector by mutableStateOf(false)

    /**
     * True while anything that owns the D-pad is on screen. On live the key handler zaps on Up/Down,
     * so without this, arrowing through an open track picker would change channel.
     */
    val isModalOpen: Boolean
        get() =
            showChannelPanel ||
                showAudioTrackSelector ||
                showSubtitleSelector ||
                showQualitySelector ||
                showChapterSelector
    var showTopOfHourClock by mutableStateOf(false)

    // Bumped on every key press while the OSD is up, so PlayerEffects' auto-hide counts its 5 s from the
    // last key (LT4).
    var controlsKeyTick by mutableIntStateOf(0)

    var lastOkClickTime by mutableLongStateOf(0L)

    // Set when a Center/Enter KeyDown reveals the OSD (and moves focus to a button: Channels on live,
    // Play/Pause on VOD). clickable() fires on KeyUp, so the same press's KeyUp must be consumed too, or
    // one press would open the OSD and also activate that button. Not Compose state: only
    // handlePlayerKeyEvent reads and writes it.
    var suppressNextCenterKeyUp: Boolean = false

    // VOD scrub cursor position: non-null while the user scrubs with the D-pad. Releasing Left/Right (or
    // OK/Center) commits the seek; Back cancels.
    var scrubPositionMs by mutableStateOf<Long?>(null)

    // Net channel hops from D-pad auto-repeat ticks (repeatCount >= 1) while a direction key is held.
    // The first tick of each press fires at once instead.
    var pendingChannelDelta by mutableIntStateOf(0)

    var livePosition by mutableLongStateOf(0L)
    var liveDuration by mutableLongStateOf(0L)
    var displayedMetadata by mutableStateOf(initialMetadata)
    var previousMetadataTitle by mutableStateOf<String?>(null)
    var isInitialLoad by mutableStateOf(true)

    val focusRequester = FocusRequester()
    val appSettings = AppSettings(context.applicationContext)
    val isNerd = appSettings.isNerdMode
}

@Composable
fun rememberPlayerScreenState(
    context: Context,
    initialMetadata: PlayerMetadata = PlayerMetadata(),
): PlayerScreenState =
    remember {
        PlayerScreenState(context, initialMetadata)
    }
