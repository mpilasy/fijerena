package org.njarasoa.fijerena.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.isSpecified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.components.CinemaCodeText
import org.njarasoa.fijerena.core.ui.components.DialogKeys
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.ui.KeyInputLog
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Show Key Input's list of keys ([KeyInputLog]) at the top right of every screen, below the clock
 * (the player's too), while [AppSettings.keyInputUntil] lasts; then it turns Show Key Input off. It
 * takes no focus and no key: the keys go to the screen as before.
 */
@Composable
fun KeyInputOverlay(appSettings: AppSettings) {
    val until by remember { appSettings.observeKeyInputUntil() }.collectAsStateWithLifecycle(initialValue = appSettings.keyInputUntil)
    LaunchedEffect(until) {
        val left = until - System.currentTimeMillis()
        KeyInputLog.on = left > 0
        // A dialog's keys too: a dialog is a window of its own.
        DialogKeys.onKey = if (left > 0) KeyInputLog::record else null
        if (left > 0) {
            delay(left)
            appSettings.keyInputUntil = 0L
        } else {
            KeyInputLog.clear()
            if (until != 0L) appSettings.keyInputUntil = 0L
        }
    }
    // Each line goes after 30 s: wait for the oldest, remove it (and any as old), then the next.
    val oldest = KeyInputLog.lines.firstOrNull()
    LaunchedEffect(oldest) {
        oldest ?: return@LaunchedEffect
        delay(oldest.addedAtMs + KeyInputLog.LINE_LIFE_MS - SystemClock.uptimeMillis())
        KeyInputLog.removeOld()
    }
    if (until <= System.currentTimeMillis() || KeyInputLog.lines.isEmpty()) return
    // Below the clock's line: the screens' clock and the player's are both one Heading line,
    // Spacing.xl from the top; full screen the screens' clock is hidden, but the player's is there.
    val clockLine =
        with(
            LocalDensity.current,
        ) { textStyleOf(TextRole.Heading).let { if (it.lineHeight.isSpecified) it.lineHeight else it.fontSize }.toDp() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = Spacing.tvSafeMarginHorizontal,
                    end = Spacing.tvSafeMarginHorizontal,
                    top =
                        Spacing.xl + clockLine + Spacing.sm,
                ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier =
                Modifier
                    .focusProperties { canFocus = false }
                    .clearAndSetSemantics {}
                    .panel(PanelFill.Dim, PanelCorner.Chip)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            KeyInputLog.lines.forEach { CinemaCodeText(it.text, role = TextRole.Caption, maxLines = 1) }
        }
    }
}
