package org.njarasoa.fijerena.core.network.sports

import java.text.Normalizer
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The date and time in a PPV or event channel's name ("US (Peacock 022) | Liverpool v. Arsenal
 * (2026-11-01 10:30:00)", "Bayern München vs Dortmund @ Oct 31 6:20 PM :Viaplay DK", "NEXT |
 * BUNDESLIGA BAYERN MÜNCHEN - DORTMUND | Sat 31 Oct 18:20 CET (DK)"), checked against the game, so
 * that a channel named for another game of the same teams, or for a replay, does not match.
 *
 * The time zone of the name is not known: it can be the game's, GMT, or the channel's country (an
 * AU channel gives the next day). So:
 * - a date and a time with no zone agree when the time of the name, read as UTC, is from
 *   [LOOSE_BEFORE_SEC] before to [LOOSE_AFTER_SEC] after the start of the game (every real zone,
 *   and a pre-show of up to 1 hour);
 * - a date and a time with a zone ("GMT", "CET", "8:30pm ET") agree from [ZONED_BEFORE_SEC] before
 *   to [ZONED_AFTER_SEC] after the start;
 * - a date with no time agrees within 1 day of the date of the game (UTC);
 * - a time with no date counts only with a zone, on the nearest day;
 * - with no date, a day name ("Sat") must be the day of the game or next to it.
 * A name with no date and no time agrees.
 */
internal object ChannelNameDates {
    private const val DATE_SLACK_DAYS = 1L
    private const val HOUR_SEC = 60 * 60L
    private const val DAY_SEC = 24 * HOUR_SEC
    const val LOOSE_BEFORE_SEC = 15 * HOUR_SEC
    const val LOOSE_AFTER_SEC = 15 * HOUR_SEC
    const val ZONED_BEFORE_SEC = 3 * HOUR_SEC
    const val ZONED_AFTER_SEC = HOUR_SEC

    // "2026-11-01", "2026-11-01 10:30:00", "2026/11/01".
    private val ISO_DATE =
        Regex("(?<!\\d)(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})(?:[ t](\\d{1,2}):(\\d{2})(?::\\d{2})?)?(?!\\d)")

    // "31-10-2026", "31/10/26", "31.10.2026": day and month either way round, with a year.
    private val NUMERIC_YEAR_DATE = Regex("(?<![\\d/.-])(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{4}|\\d{2})(?![\\d/.-])")

    // "31/10", "10/31": day and month either way round. Not part of a longer number ("2026/2027").
    private val NUMERIC_DATE = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])")

    // "31 Oct", "1st November", "1er novembre".
    private val DAY_MONTH = Regex("(?<![\\p{L}\\d])(\\d{1,2})(?:st|nd|rd|th|er|e)?\\.?\\s+(\\p{L}{3,9})\\.?(?!\\p{L})")

    // "Oct 31", "Nov 1st"; not "Oct 18:20", where the number is a time.
    private val MONTH_DAY = Regex("(?<!\\p{L})(\\p{L}{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?![\\d:h.])")

    // "18:20", "6:20 PM", "20h30", "17:30:00".
    private val CLOCK = Regex("(?<![\\d:])(\\d{1,2})[:h](\\d{2})(?::\\d{2})?(?!\\d)(?:\\s*([ap])\\.?m\\b\\.?)?")

    // "8pm", "8 pm".
    private val HOUR_ONLY = Regex("(?<![\\d:])(\\d{1,2})\\s*([ap])\\.?m\\b\\.?")

    // A zone after a time: "18:20 CET", "17:30 (GMT)", "8:30pm ET".
    private val ZONE_AFTER = Regex("^\\s*\\(?\\s*([a-z]{2,5})\\b")

    private val MARKS = Regex("\\p{Mn}+")

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

    // Zones named after a time. A summer or a winter name is its fixed offset; a short US or UK name follows the season.
    private val ZONES: Map<String, ZoneId> =
        mapOf(
            "et" to ZoneId.of("America/New_York"),
            "ct" to ZoneId.of("America/Chicago"),
            "mt" to ZoneId.of("America/Denver"),
            "pt" to ZoneId.of("America/Los_Angeles"),
            "est" to ZoneOffset.ofHours(-5),
            "edt" to ZoneOffset.ofHours(-4),
            "cst" to ZoneOffset.ofHours(-6),
            "cdt" to ZoneOffset.ofHours(-5),
            "mst" to ZoneOffset.ofHours(-7),
            "mdt" to ZoneOffset.ofHours(-6),
            "pst" to ZoneOffset.ofHours(-8),
            "pdt" to ZoneOffset.ofHours(-7),
            "gmt" to ZoneOffset.UTC,
            "utc" to ZoneOffset.UTC,
            "wet" to ZoneOffset.UTC,
            "uk" to ZoneId.of("Europe/London"),
            "bst" to ZoneOffset.ofHours(1),
            "west" to ZoneOffset.ofHours(1),
            "cet" to ZoneOffset.ofHours(1),
            "cest" to ZoneOffset.ofHours(2),
            "eet" to ZoneOffset.ofHours(2),
            "eest" to ZoneOffset.ofHours(3),
            "sast" to ZoneOffset.ofHours(2),
            "msk" to ZoneOffset.ofHours(3),
            "awst" to ZoneOffset.ofHours(8),
            "aest" to ZoneOffset.ofHours(10),
            "aedt" to ZoneOffset.ofHours(11),
            "brt" to ZoneOffset.ofHours(-3),
        )

    /** A date the name can mean, with the time written in the same date-time (ISO), if any. */
    private data class NameDate(
        val date: LocalDate,
        val time: LocalTime? = null,
    )

    /** A time in the name, and its zone when one follows it. */
    private data class NameTime(
        val time: LocalTime,
        val zone: ZoneId?,
    )

    /**
     * Whether the date and time in channel [name] agree with a game that starts at [startSec] (UTC).
     * [ignore]: words of the team names, never read as a day or a month ("Sun" of the Connecticut Sun).
     */
    fun agrees(
        name: String,
        startSec: Long,
        ignore: Set<String> = emptySet(),
    ): Boolean {
        val gameDate = Instant.ofEpochSecond(startSec).atZone(ZoneOffset.UTC).toLocalDate()
        val text = StringBuilder(fold(name))
        val dates = dates(text, gameDate, ignore)
        val time = time(text)
        if (dates.isNotEmpty()) return dates.any { dateAgrees(it, time, gameDate, startSec) }
        val days = weekdays(text.toString(), ignore)
        val near = (-DATE_SLACK_DAYS..DATE_SLACK_DAYS).map { gameDate.plusDays(it).dayOfWeek }
        if (days.isNotEmpty() && days.none { it in near }) return false
        val zone = time?.zone ?: return true
        // A time with a zone and no date: on the day nearest the game.
        val offset = gameDate.atTime(time.time).atZone(zone).toEpochSecond() - startSec
        val diff = Math.floorMod(offset + DAY_SEC / 2, DAY_SEC) - DAY_SEC / 2
        return diff in -ZONED_BEFORE_SEC..ZONED_AFTER_SEC
    }

    private fun dateAgrees(
        date: NameDate,
        time: NameTime?,
        gameDate: LocalDate,
        startSec: Long,
    ): Boolean {
        val zone = if (date.time == null) time?.zone else null
        val clock = date.time ?: time?.time
        return when {
            clock == null -> {
                abs(date.date.toEpochDay() - gameDate.toEpochDay()) <= DATE_SLACK_DAYS
            }

            zone != null -> {
                (
                    date.date
                        .atTime(clock)
                        .atZone(zone)
                        .toEpochSecond() - startSec
                ) in -ZONED_BEFORE_SEC..ZONED_AFTER_SEC
            }

            else -> {
                (date.date.atTime(clock).toEpochSecond(ZoneOffset.UTC) - startSec) in -LOOSE_BEFORE_SEC..LOOSE_AFTER_SEC
            }
        }
    }

    /** [name] in lower case without accents, its punctuation kept. */
    private fun fold(name: String): String = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    /**
     * Every date [text] can mean; a date with no year is taken in the year nearest [near]. Each
     * date found is blanked out of [text], so that its numbers are not read again as a time.
     */
    private fun dates(
        text: StringBuilder,
        near: LocalDate,
        ignore: Set<String>,
    ): List<NameDate> {
        val found = ArrayList<NameDate>()
        for (m in ISO_DATE.findAll(text.toString()).toList()) {
            val time = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { clock(it.toInt(), m.groupValues[5].toInt(), null) }
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), near).forEach {
                found += NameDate(it, time)
            }
            blank(text, m.range)
        }
        for (m in NUMERIC_YEAR_DATE.findAll(text.toString()).toList()) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[3].toInt()
            val year = m.groupValues[4].toInt().let { if (it < 100) 2000 + it else it }
            (date(year, b, a, near) + date(year, a, b, near)).forEach { found += NameDate(it) }
            blank(text, m.range)
        }
        for (m in NUMERIC_DATE.findAll(text.toString()).toList()) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            (date(null, b, a, near) + date(null, a, b, near)).forEach { found += NameDate(it) }
            blank(text, m.range)
        }
        for (m in DAY_MONTH.findAll(text.toString()).toList()) {
            val word = m.groupValues[2]
            val month = MONTHS[word]?.takeIf { word !in ignore } ?: continue
            date(null, month, m.groupValues[1].toInt(), near).forEach { found += NameDate(it) }
            blank(text, m.range)
        }
        for (m in MONTH_DAY.findAll(text.toString()).toList()) {
            val word = m.groupValues[1]
            val month = MONTHS[word]?.takeIf { word !in ignore } ?: continue
            date(null, month, m.groupValues[2].toInt(), near).forEach { found += NameDate(it) }
            blank(text, m.range)
        }
        return found
    }

    private fun blank(
        text: StringBuilder,
        range: IntRange,
    ) {
        for (i in range) text.setCharAt(i, ' ')
    }

    /** The first time in [text] ("18:20", "6:20 PM", "8pm") and the zone after it, if any. */
    private fun time(text: StringBuilder): NameTime? {
        val s = text.toString()
        val clockMatch = CLOCK.find(s)
        val hourMatch = HOUR_ONLY.find(s)
        val m = listOfNotNull(clockMatch, hourMatch).minByOrNull { it.range.first } ?: return null
        val time =
            if (m === clockMatch) {
                clock(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { null })
            } else {
                clock(m.groupValues[1].toInt(), 0, m.groupValues[2])
            } ?: return null
        val zone = ZONE_AFTER.find(s.substring(m.range.last + 1))?.let { ZONES[it.groupValues[1]] }
        return NameTime(time, zone)
    }

    /** A clock time; [half] "a" or "p" for am or pm. Null for a time that cannot be. */
    private fun clock(
        hour: Int,
        minute: Int,
        half: String?,
    ): LocalTime? {
        if (minute > 59) return null
        val h =
            when (half) {
                null -> hour.takeIf { it <= 23 }
                "p" -> hour.takeIf { it in 1..12 }?.let { if (it == 12) 12 else it + 12 }
                else -> hour.takeIf { it in 1..12 }?.let { if (it == 12) 0 else it }
            } ?: return null
        return LocalTime.of(h, minute)
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
        text: String,
        ignore: Set<String>,
    ): List<DayOfWeek> =
        SportsText
            .fold(text)
            .split(' ')
            .filter { it !in ignore }
            .mapNotNull { WEEKDAYS[it] }
}
