package org.njarasoa.fijerena.core.player.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared EPG utility functions used by both TV and Mobile EPG screens.
 */
object EpgUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isCurrentProgram(program: EpgProgram): Boolean {
        val now = System.currentTimeMillis() / 1000
        return now in program.startTime..program.endTime
    }

    fun formatTime(timestampSeconds: Long): String {
        val instant = Instant.ofEpochSecond(timestampSeconds)
        val localTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return localTime.format(timeFormatter)
    }

    fun formatTimeRange(start: Long, end: Long): String {
        return "${formatTime(start)} - ${formatTime(end)}"
    }
}
