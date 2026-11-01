package org.njarasoa.fijerena.core.ui.utils

import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.ui.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The shared day, time and date words (consistency plan, C-D5), in English, French and Malagasy. */
class DateTextTest {
    private val english = Locale.UK
    private val french = Locale.FRANCE
    private val malagasy = Locale.forLanguageTag("mg")

    private val today = LocalDate.of(2026, 11, 1)
    private val tuesday = LocalDate.of(2026, 11, 3)

    private fun resources(
        todayWord: String,
        tomorrowWord: String,
        yesterdayWord: String,
    ): Resources =
        mockk {
            every { getString(R.string.epg_tab_today) } returns todayWord
            every { getString(R.string.epg_tab_tomorrow) } returns tomorrowWord
            every { getString(R.string.epg_tab_yesterday) } returns yesterdayWord
        }

    private val englishWords = resources("Today", "Tomorrow", "Yesterday")
    private val frenchWords = resources("Aujourd'hui", "Demain", "Hier")
    private val malagasyWords = resources("Anio", "Rahampitso", "Omaly")

    @Test
    fun `today, tomorrow and yesterday use the app language's words`() {
        assertEquals("Today", DateText.dayLabel(englishWords, today, today, english))
        assertEquals("Tomorrow", DateText.dayLabel(englishWords, today.plusDays(1), today, english))
        assertEquals("Yesterday", DateText.dayLabel(englishWords, today.minusDays(1), today, english))
        assertEquals("Demain", DateText.shortDayLabel(frenchWords, today.plusDays(1), today, french))
        assertEquals("Omaly", DateText.shortDayLabel(malagasyWords, today.minusDays(1), today, malagasy))
    }

    @Test
    fun `other days get the full date in a day header`() {
        assertEquals("Tuesday, 3 November 2026", DateText.dayLabel(englishWords, tuesday, today, english))
        assertEquals("mardi 3 novembre 2026", DateText.dayLabel(frenchWords, tuesday, today, french))
        assertEquals("Talata 3 Novambra 2026", DateText.dayLabel(malagasyWords, tuesday, today, malagasy))
    }

    @Test
    fun `other days get the short date in a compact place`() {
        assertEquals("Tue 3 Nov", DateText.shortDayLabel(englishWords, tuesday, today, english))
        assertEquals("mar. 3 nov.", DateText.shortDayLabel(frenchWords, tuesday, today, french))
        assertEquals("Talata 3 Nov", DateText.shortDayLabel(malagasyWords, tuesday, today, malagasy))
    }

    @Test
    fun `the medium date has Malagasy month names`() {
        assertEquals("3 Nov 2026", DateText.mediumDate(tuesday, english))
        assertEquals("3 Desambra 2026", DateText.mediumDate(LocalDate.of(2026, 12, 3), malagasy))
    }

    private val chicago = ZoneId.of("America/Chicago")

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, chicago).toEpochSecond()

    private val clock24: (Long) -> String = {
        java.time.Instant
            .ofEpochSecond(it)
            .atZone(chicago)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    @Test
    fun `a programme ending the same day has no end day`() {
        assertNull(DateText.endDayLabel(at(11, 21, 50), at(11, 23, 45), chicago, english))
        assertEquals("21:50 – 23:45", DateText.timeRange(at(11, 21, 50), at(11, 23, 45), chicago, english, clock24))
    }

    @Test
    fun `a programme past midnight names the end's day`() {
        // Sunday 18:25 to Monday 15:00, a game in the guide that runs into the next day.
        assertEquals("18:25 – Mon 15:00", DateText.timeRange(at(11, 18, 25), at(12, 15), chicago, english, clock24))
        assertEquals("18:25 – lun. 15:00", DateText.timeRange(at(11, 18, 25), at(12, 15), chicago, french, clock24))
        assertEquals("23:30 – Alatsinainy 01:00", DateText.timeRange(at(11, 23, 30), at(12, 1), chicago, malagasy, clock24))
    }

    @Test
    fun `the day is the viewer's, not UTC's`() {
        // 18:00 to 23:00 in Chicago is the same day there, though it ends the next day in UTC.
        assertNull(DateText.endDayLabel(at(11, 18), at(11, 23), chicago, english))
    }

    @Test
    fun `a timestamp shows its date and time`() {
        assertEquals("11 Oct 2026, 18:25", DateText.dateTime(at(11, 18, 25) * 1000L, chicago, english, clock24))
        assertEquals("11 Oktobra 2026, 18:25", DateText.dateTime(at(11, 18, 25) * 1000L, chicago, malagasy, clock24))
    }
}
