package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.player.model.TimeFormat as CoreTimeFormat
import java.util.Date

/**
 * Composable time formatting wrappers. Context is grabbed automatically —
 * callers just pass the value to format.
 */
object TimeFormat {

    @Composable
    fun formatTime(epochSeconds: Long): String {
        return CoreTimeFormat.formatTime(LocalContext.current, epochSeconds)
    }

    @Composable
    fun formatTimeRange(startEpochSeconds: Long, endEpochSeconds: Long): String {
        return CoreTimeFormat.formatTimeRange(LocalContext.current, startEpochSeconds, endEpochSeconds)
    }

    @Composable
    fun formatClockTime(date: Date): String {
        return CoreTimeFormat.formatClockTime(LocalContext.current, date)
    }
}
