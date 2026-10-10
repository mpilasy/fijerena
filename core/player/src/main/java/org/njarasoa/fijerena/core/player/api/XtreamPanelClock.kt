package org.njarasoa.fijerena.core.player.api

import org.njarasoa.fijerena.core.player.model.XtreamServerInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The panel's wall clock, in which a timeshift `start` is read (bears: Europe/Amsterdam, while its
 * guide gives UTC). Measured at each login from `server_info`: `time_now` (panel wall clock) minus
 * `timestamp_now` (UTC epoch) is the panel's real offset, even when the `timezone` name is missing
 * or wrong. The name is kept when it agrees with that offset, so a DST change inside the archive
 * window is still right. See docs/plans/archive/20261010_catchup-plan.md → "Detected per source".
 */
class XtreamPanelClock internal constructor(
    val zone: ZoneId,
) {
    /** [epochSec] as the panel's timeshift `start`: `YYYY-MM-DD:HH-MM` in the panel's time. */
    fun timeshiftStart(epochSec: Long): String = timeshiftFormat.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    companion object {
        private const val OFFSET_STEP_SEC = 15 * 60L
        private const val MAX_OFFSET_SEC = 18 * 60 * 60L
        private val timeshiftFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        private val panelDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** UTC: what a panel that says nothing about its clock is assumed to use. */
        val UTC = XtreamPanelClock(ZoneOffset.UTC)

        fun from(serverInfo: XtreamServerInfo?): XtreamPanelClock {
            val named =
                serverInfo?.timezone?.takeIf { it.isNotBlank() }?.let {
                    try {
                        ZoneId.of(it.trim())
                    } catch (e: Exception) {
                        // cancellation-ok: non-suspend
                        null
                    }
                }
            val measured = serverInfo?.let(::measuredOffsetSec)
            val zone =
                when {
                    measured == null -> named
                    named != null && namedOffsetSec(named, serverInfo.timestampNow) == measured -> named
                    abs(measured) <= MAX_OFFSET_SEC -> ZoneOffset.ofTotalSeconds(measured.toInt())
                    else -> named
                }
            return zone?.let(::XtreamPanelClock) ?: UTC
        }

        /** `time_now` − `timestamp_now`, rounded to a quarter hour (the two are read a moment apart). */
        private fun measuredOffsetSec(info: XtreamServerInfo): Long? {
            val now = info.timestampNow
            val wall =
                info.timeNow?.let {
                    try {
                        LocalDateTime.parse(it.trim(), panelDateTime).toEpochSecond(ZoneOffset.UTC)
                    } catch (e: Exception) {
                        // cancellation-ok: non-suspend
                        null
                    }
                }
            return if (now == null || wall == null) null else ((wall - now).toDouble() / OFFSET_STEP_SEC).roundToLong() * OFFSET_STEP_SEC
        }

        private fun namedOffsetSec(
            zone: ZoneId,
            epochSec: Long?,
        ): Long? =
            epochSec?.let {
                zone.rules
                    .getOffset(Instant.ofEpochSecond(it))
                    .totalSeconds
                    .toLong()
            }
    }
}
