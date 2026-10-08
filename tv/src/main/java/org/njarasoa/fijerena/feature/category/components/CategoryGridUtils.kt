package org.njarasoa.fijerena.feature.category.components

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.isCategoryMarker

/**
 * Modifier that detects long-press of the D-pad center / Enter key on TV.
 * Calls [onLongPress] when the key has been held long enough.
 */
internal fun Modifier.tvLongPress(onLongPress: () -> Unit): Modifier =
    composed {
        var longPressDetected by remember { mutableStateOf(false) }
        this.onPreviewKeyEvent { event ->
            val keyCode = event.key.nativeKeyCode
            val isDpadCenter =
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER
            if (isDpadCenter &&
                event.type == KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount > 0 &&
                event.nativeKeyEvent.isLongPress &&
                !longPressDetected
            ) {
                longPressDetected = true
                true
            } else if (isDpadCenter && event.type == KeyEventType.KeyDown && longPressDetected) {
                // Consume repeated KeyDown events while held
                true
            } else if (isDpadCenter && event.type == KeyEventType.KeyUp && longPressDetected) {
                // Fire callback on release so the dialog opens after the key is up
                longPressDetected = false
                onLongPress()
                true
            } else {
                false
            }
        }
    }

/**
 * Scrolls a list only as far as it takes to show the focused row whole (TV UI audit #11). On
 * Android TV the default spec pivots every focused row to a third of the way down, so a list
 * opened on its third row came up part-scrolled, its first row cut at the top.
 */
@OptIn(ExperimentalFoundationApi::class)
internal val MinimalBringIntoView =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float {
            val trailing = offset + size
            val distance =
                when {
                    // Already whole on screen, or bigger than the viewport: leave it.
                    offset >= 0f && trailing <= containerSize -> 0f

                    offset < 0f && trailing > containerSize -> 0f

                    // Cut at the top: bring its top edge in; cut at the bottom: its bottom edge.
                    offset < 0f -> offset

                    else -> trailing - containerSize
                }
            return distance
        }
    }

/**
 * A provider's separator row in a channel list ("####### ETHIOPIA VIP #######"): a heading, never
 * focused, played or zapped to (TV UI audit #24). The same `#…#` rule the TV Guide drops them by.
 */
internal val MediaItem.isSeparatorRow: Boolean
    get() = mediaType == MediaType.LIVE_CHANNEL && isCategoryMarker
