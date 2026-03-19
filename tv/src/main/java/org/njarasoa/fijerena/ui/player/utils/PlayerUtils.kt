package org.njarasoa.fijerena.ui.player.utils

import android.content.Context
import org.njarasoa.fijerena.core.player.model.TimeFormat

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatEpochTime(
    context: Context,
    epochSeconds: Long,
): String = TimeFormat.formatTime(context, epochSeconds)

fun formatBitrate(bitrate: Int): String =
    if (bitrate > 0) {
        val kbps = bitrate / 1000
        if (kbps > 1000) {
            String.format("%.1f Mbps", kbps / 1000f)
        } else {
            "$kbps Kbps"
        }
    } else {
        "Unknown"
    }
