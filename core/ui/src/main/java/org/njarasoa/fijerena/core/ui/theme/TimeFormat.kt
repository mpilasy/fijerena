package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import org.njarasoa.fijerena.core.player.model.TimeFormat as CoreTimeFormat

/**
 * Composable time formatting wrappers. Context is grabbed automatically —
 * callers just pass the value to format.
 */
object TimeFormat {
    @Composable
    fun formatTime(epochSeconds: Long): String = CoreTimeFormat.formatTime(LocalContext.current, epochSeconds)

    @Composable
    fun formatTimeRange(
        startEpochSeconds: Long,
        endEpochSeconds: Long,
    ): String = CoreTimeFormat.formatTimeRange(LocalContext.current, startEpochSeconds, endEpochSeconds)

    @Composable
    fun formatClockTime(date: Date): String = CoreTimeFormat.formatClockTime(LocalContext.current, date)
}
