package org.njarasoa.fijerena.core.network.sports

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The date and time in a PPV or event channel's name ("PPV | Bundesliga 04: FC Bayern - Ajax (Sat
 * 01/11 20:30)", "NFL 05 | Chiefs @ Bills Nov 2 8:30pm ET"), checked against the game, so that a
 * channel still named for an older game of the same teams does not match.
 *
 * The channel's time zone is not known, so a date may be one day off the game's date (UTC), and a
 * time counts only with a zone ("8:30pm ET", "20:00 CET"). A name with no date or time agrees.
 */
internal object ChannelNameDates {
    private const val DATE_SLACK_DAYS = 1L
    private const val TIME_SLACK_MIN = 120
    private const val DAY_MIN = 24 * 60

    // "01/11", "1/11/2026", "11/01/26": day and month either way round. Not "24/7"-style parts of a longer number.
    private val NUMERIC_DATE = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?(?![\\d/])")

    // "8:30pm ET", "20:15 CET", "8pm ET": a time with its zone.
    private val ZONED_TIME = Regex("(?<![\\d:])(\\d{1,2})(?:[:h](\\d{2}))?\\s*(am|pm)?\\s*([a-z]{2,4})\\b")

    private val ORDINAL = Regex("^(\\d{1,2})(?:st|nd|rd|th|er|e)?$")

    private val MONTHS =
        mapOf(
            "jan" to 1,
            "january" to 1,
            "janv" to 1,
            "janvier" to 1,
            "feb" to 2,
            "february" to 2,
            "fev" to 2,
            "fevr" to 2,
            "fevrier" to 2,
            "mar" to 3,
            "march" to 3,
            "mars" to 3,
            "apr" to 4,
            "april" to 4,
            "avr" to 4,
            "avril" to 4,
            "may" to 5,
            "mai" to 5,
            "jun" to 6,
            "june" to 6,
            "juin" to 6,
            "jul" to 7,
            "july" to 7,
            "juil" to 7,
            "juillet" to 7,
            "aug" to 8,
            "august" to 8,
            "aout" to 8,
            "sep" to 9,
            "sept" to 9,
            "september" to 9,
            "septembre" to 9,
            "oct" to 10,
            "october" to 10,
            "octobre" to 10,
            "nov" to 11,
            "november" to 11,
            "novembre" to 11,
            "dec" to 12,
            "december" to 12,
            "decembre" to 12,
        )

    private val WEEKDAYS =
        mapOf(
            "mon" to DayOfWeek.MONDAY,
            "monday" to DayOfWeek.MONDAY,
            "lundi" to DayOfWeek.MONDAY,
            "tue" to DayOfWeek.TUESDAY,
            "tues" to DayOfWeek.TUESDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "mardi" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "mercredi" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY,
            "thur" to DayOfWeek.THURSDAY,
            "thurs" to DayOfWeek.THURSDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "jeudi" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY,
            "friday" to DayOfWeek.FRIDAY,
            "vendredi" to DayOfWeek.FRIDAY,
            "sat" to DayOfWeek.SATURDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "samedi" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY,
            "sunday" to DayOfWeek.SUNDAY,
            "dimanche" to DayOfWeek.SUNDAY,
        )

    private val ZONES =
        mapOf(
            "et" to "America/New_York",
            "est" to "America/New_York",
            "edt" to "America/New_York",
            "ct" to "America/Chicago",
            "cst" to "America/Chicago",
            "cdt" to "America/Chicago",
            "mt" to "America/Denver",
            "mst" to "America/Denver",
            "mdt" to "America/Denver",
            "pt" to "America/Los_Angeles",
            "pst" to "America/Los_Angeles",
            "pdt" to "America/Los_Angeles",
            "gmt" to "UTC",
            "utc" to "UTC",
            "uk" to "Europe/London",
            "bst" to "Europe/London",
            "cet" to "Europe/Paris",
            "cest" to "Europe/Paris",
        )

    /**
     * Whether the date and time in channel [name] agree with a game that starts at [startSec] (UTC).
     * [ignore]: words of the team names, never read as a day ("Sun" of the Connecticut Sun).
     */
    fun agrees(
        name: String,
        startSec: Long,
        ignore: Set<String> = emptySet(),
    ): Boolean {
        val start = Instant.ofEpochSecond(startSec)
        val gameDate = start.atZone(ZoneOffset.UTC).toLocalDate()
        val dates = dates(name, gameDate, ignore)
        if (dates.isNotEmpty() && dates.none { abs(it.toEpochDay() - gameDate.toEpochDay()) <= DATE_SLACK_DAYS }) return false
        if (dates.isEmpty()) {
            val days = weekdays(name, ignore)
            val near = (-DATE_SLACK_DAYS..DATE_SLACK_DAYS).map { gameDate.plusDays(it).dayOfWeek }
            if (days.isNotEmpty() && days.none { it in near }) return false
        }
        return timeAgrees(name.lowercase(), start)
    }

    /** Every date [name] can mean; a date with no year is taken in the year nearest [near]. */
    private fun dates(
        name: String,
        near: LocalDate,
        ignore: Set<String>,
    ): List<LocalDate> {
        val found = ArrayList<LocalDate>()
        for (m in NUMERIC_DATE.findAll(name)) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val year =
                m.groupValues[3]
                    .takeIf { it.isNotEmpty() }
                    ?.toInt()
                    ?.let { if (it < 100) 2000 + it else it }
            found += date(year, b, a, near) + date(year, a, b, near)
        }
        val words = SportsText.fold(name).split(' ')
        for ((i, word) in words.withIndex()) {
            val month = MONTHS[word] ?: continue
            if (word in ignore) continue
            // "Nov 1", "1 Nov", "1st November", "1er novembre".
            val day =
                listOfNotNull(words.getOrNull(i + 1), words.getOrNull(i - 1))
                    .firstNotNullOfOrNull {
                        ORDINAL
                            .matchEntire(it)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                    }
            if (day != null) found += date(null, month, day, near)
        }
        return found
    }

    private fun date(
        year: Int?,
        month: Int,
        day: Int,
        near: LocalDate,
    ): List<LocalDate> =
        (if (year != null) listOf(year) else listOf(near.year - 1, near.year, near.year + 1))
            .mapNotNull { y ->
                try {
                    LocalDate.of(y, month, day)
                } catch (_: DateTimeException) {
                    null
                }
            }.let { all -> if (year != null) all else listOfNotNull(all.minByOrNull { abs(it.toEpochDay() - near.toEpochDay()) }) }

    private fun weekdays(
        name: String,
        ignore: Set<String>,
    ): List<DayOfWeek> =
        SportsText
            .fold(name)
            .split(' ')
            .filter { it !in ignore }
            .mapNotNull { WEEKDAYS[it] }

    /** Whether each time with a zone in [lower] is within 2 hours of the game's start in that zone. */
    private fun timeAgrees(
        lower: String,
        start: Instant,
    ): Boolean =
        ZONED_TIME.findAll(lower).all { m ->
            val zone = ZONES[m.groupValues[4]] ?: return@all true
            val minutesText = m.groupValues[2]
            val half = m.groupValues[3]
            // "3 uk" in "PPV 3 UK" is not a time: a time has minutes or am/pm.
            if (minutesText.isEmpty() && half.isEmpty()) return@all true
            var hour = m.groupValues[1].toInt()
            val minute = minutesText.ifEmpty { "0" }.toInt()
            if (hour > 23 || minute > 59 || (half.isNotEmpty() && hour !in 1..12)) return@all true
            if (half == "pm" && hour < 12) hour += 12
            if (half == "am" && hour == 12) hour = 0
            val local = start.atZone(ZoneId.of(zone))
            val diff = abs(hour * 60 + minute - (local.hour * 60 + local.minute))
            minOf(diff, DAY_MIN - diff) <= TIME_SLACK_MIN
        }
}
