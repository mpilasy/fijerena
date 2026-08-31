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
    // UI visibility states
    var showControls by mutableStateOf(false)
    var showStats by mutableStateOf(false)
    var showAudioTrackSelector by mutableStateOf(false)
    var showSubtitleSelector by mutableStateOf(false)
    var showQualitySelector by mutableStateOf(false)
    private var showStreamInfoState by mutableStateOf(false)

    // Bumped whenever showStreamInfo is set to true, so PlayerEffects' auto-hide LaunchedEffect
    // restarts its delay even when a second trigger (e.g. another channel zap) lands while
    // showStreamInfo is already true from a previous one — keying on showStreamInfo alone
    // wouldn't restart the coroutine since the value doesn't actually change.
    var showStreamInfoTick by mutableIntStateOf(0)
        private set

    var showStreamInfo: Boolean
        get() = showStreamInfoState
        set(value) {
            showStreamInfoState = value
            if (value) showStreamInfoTick++
        }
    var showCategoryOverlay by mutableStateOf(false)
    var showLastWatchedOverlay by mutableStateOf(false)
    var showChapterSelector by mutableStateOf(false)

    /**
     * True while anything that owns the D-pad is on screen. The player's key handler treats Up and
     * Down as channel change on live, so without this a track picker could be open and arrowing
     * through it would change channel instead of moving between its options.
     */
    val isModalOpen: Boolean
        get() = showCategoryOverlay ||
            showLastWatchedOverlay ||
            showAudioTrackSelector ||
            showSubtitleSelector ||
            showQualitySelector ||
            showChapterSelector
    var showTopOfHourClock by mutableStateOf(false)
    var showControlHints by mutableStateOf(false)

    // Interaction state
    var lastOkClickTime by mutableLongStateOf(0L)

    // Set when a Center/Enter KeyDown reveals the OSD (and moves focus onto a button —
    // Favourite on live, Play/Pause on VOD). Compose's clickable() fires on KeyUp of
    // Center/Enter, so the same physical press's KeyUp must be consumed too, or it activates
    // whatever focus just landed on — turning "open the OSD" into "open the OSD AND toggle
    // favourite / pause" from a single press. Not Compose state: read/written only inside
    // handlePlayerKeyEvent, never observed by composition.
    var suppressNextCenterKeyUp: Boolean = false

    // Fast-forward / rewind state
    var seekSpeedLabel by mutableStateOf<String?>(null)

    // Scrub cursor position for VOD: non-null while user is scrubbing with D-pad.
    // OK/Center commits the seek; Back cancels.
    var scrubPositionMs by mutableStateOf<Long?>(null)

    // Net channel hops accumulated from D-pad auto-repeat ticks (repeatCount >= 1) while a
    // direction key is held. The first tap/tick of every press fires immediately instead.
    var pendingChannelDelta by mutableIntStateOf(0)

    // Data states
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
    initialMetadata: PlayerMetadata = PlayerMetadata(),
): PlayerScreenState =
    remember {
        PlayerScreenState(context, initialMetadata)
    }
