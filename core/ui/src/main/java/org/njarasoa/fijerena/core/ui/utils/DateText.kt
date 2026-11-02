package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.content.res.Resources
import org.njarasoa.fijerena.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.njarasoa.fijerena.core.player.model.TimeFormat as CoreTimeFormat

/**
 * Days, times and dates as the app writes them, the same on TV and phone (consistency plan, C-D5).
 *
 * - Day headers in lists: [dayLabel] ("Today", "Tomorrow", "Yesterday", else the full date).
 * - Compact places (guide day tabs, "day · time" lines, chips): [shortDayLabel] (the same three
 *   words, else the short date "Tue 3 Nov").
 * - Times follow the device's 12/24-hour setting; a programme that ends on another day shows the
 *   end's day ([timeRange]).
 * - Timestamps (last refresh, last sync): [dateTime].
 *
 * The system has no Malagasy day or month names (it wrote "1978 Jan 20"), so Malagasy uses its own.
 *
 * The locale is always the app language of the Resources in use ([appLocale]), never the default
 * locale: the system sets the default back to the phone language on a configuration change, which
 * gave Malagasy dates in the French app.
 */
object DateText {
    /** "Today", "Tomorrow", "Yesterday", else the full date ("Tuesday, 3 November 2026"). For day headers in lists. */
    fun dayLabel(
        resources: Resources,
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
        locale: Locale = resources.appLocale(),
    ): String = relativeDay(resources, date, today) ?: fullDate(date, locale)

    /** "Today", "Tomorrow", "Yesterday", else the short date ("Tue 3 Nov"). For compact places. */
    fun shortDayLabel(
        resources: Resources,
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
        locale: Locale = resources.appLocale(),
    ): String = relativeDay(resources, date, today) ?: shortDate(date, locale)

    /**
     * "Tue 3 Nov · 14:00": [day] and [time] as one unit. Its spaces are no-break spaces, so a line
     * that wraps moves the unit to the next line and never leaves "·" at the end of a line.
     */
    fun dayAndTime(
        day: String,
        time: String,
    ): String = "$day · $time".replace(' ', NO_BREAK_SPACE)

    /** "Tuesday, 3 November 2026" in the app language. */
    fun fullDate(
        date: LocalDate,
        locale: Locale,
    ): String =
        if (locale.language == MALAGASY) {
            "${MALAGASY_DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MALAGASY_MONTHS[date.monthValue - 1]} ${date.year}"
        } else {
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        }

    /** "Tue 3 Nov" in the app language. */
    fun shortDate(
        date: LocalDate,
        locale: Locale,
    ): String =
        if (locale.language == MALAGASY) {
            "${MALAGASY_DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MALAGASY_MONTHS_SHORT[date.monthValue - 1]}"
        } else {
            date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        }

    /** "3 Nov 2026" in the app language. */
    fun mediumDate(
        date: LocalDate,
        locale: Locale,
    ): String =
        if (locale.language == MALAGASY) {
            "${date.dayOfMonth} ${MALAGASY_MONTHS[date.monthValue - 1]} ${date.year}"
        } else {
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }

    /** The local day of [epochSec]. */
    fun day(
        epochSec: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate()

    /** "14:30", or "2:30 PM" on a 12-hour clock. */
    fun time(
        context: Context,
        epochSec: Long,
    ): String = CoreTimeFormat.formatTime(context, epochSec)

    /**
     * "18:25 – 19:30", or "18:25 – Mon 01:00" when it ends on another day: guides list overnight
     * blocks and live events that run into the next day, and the end without its day reads as
     * ending before it started.
     */
    fun timeRange(
        context: Context,
        startEpochSec: Long,
        endEpochSec: Long,
    ): String = timeRange(startEpochSec, endEpochSec, ZoneId.systemDefault(), context.resources.appLocale()) { time(context, it) }

    internal fun timeRange(
        startEpochSec: Long,
        endEpochSec: Long,
        zone: ZoneId,
        locale: Locale,
        time: (Long) -> String,
    ): String {
        val endDay = endDayLabel(startEpochSec, endEpochSec, zone, locale)
        val end = if (endDay != null) "$endDay ${time(endEpochSec)}" else time(endEpochSec)
        return "${time(startEpochSec)} – $end"
    }

    /** The end's short weekday ("Mon") when [endEpochSec] falls on another day than [startEpochSec] in [zone], else null. */
    internal fun endDayLabel(
        startEpochSec: Long,
        endEpochSec: Long,
        zone: ZoneId,
        locale: Locale,
    ): String? {
        val end = day(endEpochSec, zone)
        if (day(startEpochSec, zone) == end) return null
        return if (locale.language == MALAGASY) {
            MALAGASY_DAYS[end.dayOfWeek.value - 1]
        } else {
            end.format(DateTimeFormatter.ofPattern("EEE", locale))
        }
    }

    /** "3 Nov 2026, 14:30" for a timestamp in milliseconds (last refresh, last sync). */
    fun dateTime(
        context: Context,
        epochMs: Long,
    ): String = dateTime(epochMs, ZoneId.systemDefault(), context.resources.appLocale()) { time(context, it) }

    internal fun dateTime(
        epochMs: Long,
        zone: ZoneId,
        locale: Locale,
        time: (Long) -> String,
    ): String = "${mediumDate(day(epochMs / 1000L, zone), locale)}, ${time(epochMs / 1000L)}"

    private fun relativeDay(
        resources: Resources,
        date: LocalDate,
        today: LocalDate,
    ): String? =
        when (date) {
            today -> resources.getString(R.string.epg_tab_today)
            today.plusDays(1) -> resources.getString(R.string.epg_tab_tomorrow)
            today.minusDays(1) -> resources.getString(R.string.epg_tab_yesterday)
            else -> null
        }

    private const val MALAGASY = "mg"

    private const val NO_BREAK_SPACE = '\u00A0'

    private val MALAGASY_DAYS = listOf("Alatsinainy", "Talata", "Alarobia", "Alakamisy", "Zoma", "Asabotsy", "Alahady")

    private val MALAGASY_MONTHS =
        listOf(
            "Janoary",
            "Febroary",
            "Martsa",
            "Aprily",
            "Mey",
            "Jona",
            "Jolay",
            "Aogositra",
            "Septambra",
            "Oktobra",
            "Novambra",
            "Desambra",
        )

    // The short month names of the Unicode CLDR data for Malagasy.
    private val MALAGASY_MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "Mey", "Jon", "Jol", "Aog", "Sep", "Okt", "Nov", "Des")
}
