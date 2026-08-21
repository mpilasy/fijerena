package org.njarasoa.fijerena.core.network.tmdb

import java.text.Normalizer

/** A catalogue or TMDB title reduced to a comparable form, with any year it carried pulled out. */
data class NormalizedTitle(
    /**
     * Comparable text. A bracketed year is gone; a bare trailing one stays, because there is no
     * telling "Dune 2021" from "Blade Runner 2049" by shape alone.
     */
    val text: String,
    /** [text] with a bare trailing year removed too; equal to [text] when there was none. */
    val textWithoutYear: String,
    val year: Int?,
)

/**
 * Decides whether a catalogue item is the same title TMDB just recommended.
 *
 * Substring matching is deliberately not offered: "Dune" contains-matching "Dune: Part Two" is a
 * plausible-looking wrong answer, and a recommendation row is short enough that recall matters
 * less than not lying about what the provider carries.
 */
object TitleMatcher {
    /** Leading junk providers prepend, e.g. `[VIP] `, `(FR) `, `|EN| `. */
    private val LEADING_BRACKET = Regex("""^\s*[\[(|{][^\])|}]{0,12}[\])|}]\s*""")

    /**
     * Language/quality tags providers put before a spaced dash, e.g. `EN - `, `4K - `. The space is
     * required: without it "X-Men" loses its "X", and a colon is never a separator here because
     * titles use one ("Dune: Part Two").
     */
    private val LEADING_TAG =
        Regex("""^\s*([a-z]{2,3}|4k|uhd|fhd|vip|multi|vostfr|vost)\s+-\s+""")

    /** An unambiguous year at the end: `Dune (2021)`, `Dune [2021]`. */
    private val BRACKETED_YEAR = Regex("""\s*[(\[]((?:19|20)\d{2})[)\]]\s*$""")

    /** A year at the end with nothing marking it as one — may equally be part of the title. */
    private val BARE_YEAR = Regex("""\s+((?:19|20)\d{2})\s*$""")

    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")

    fun normalize(raw: String): NormalizedTitle {
        var text =
            Normalizer
                .normalize(raw, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase()

        // Providers stack these, so keep stripping until nothing leading is left: "EN - 4K - Dune".
        while (true) {
            val stripped = text.replace(LEADING_BRACKET, "").replace(LEADING_TAG, "")
            if (stripped == text) break
            text = stripped
        }

        // Years come out before punctuation does, while "(2021)" is still recognisable as one.
        var year = BRACKETED_YEAR.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (year != null) text = text.replace(BRACKETED_YEAR, "")

        var withoutYear = text
        if (year == null) {
            val bare = BARE_YEAR.find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (bare != null) {
                year = bare
                withoutYear = text.replace(BARE_YEAR, "")
            }
        }

        return NormalizedTitle(
            text = clean(text),
            textWithoutYear = clean(withoutYear),
            year = year,
        )
    }

    private fun clean(text: String): String = text.replace(NON_ALPHANUMERIC, " ").trim()

    /**
     * True when [catalogueTitle] is the same work as [tmdbTitle]. Titles must be equal once
     * normalized; years only have to agree within a year, and only when both sides have one —
     * providers and TMDB disagree on release versus air year often enough that demanding equality
     * throws away good matches.
     *
     * The exception is a match that only holds after dropping a bare trailing year, which then
     * requires both years: otherwise "Blade Runner 2049" would answer to "Blade Runner".
     *
     * [catalogueYear] is the year the provider stated in its metadata, if any; a year embedded in
     * the title is used when it is absent.
     */
    fun matches(
        catalogueTitle: String,
        catalogueYear: Int?,
        tmdbTitle: String,
        tmdbYear: Int?,
    ): Boolean {
        val catalogue = normalize(catalogueTitle)
        val tmdb = normalize(tmdbTitle)
        if (catalogue.text.isBlank() || tmdb.text.isBlank()) return false

        val exact = catalogue.text == tmdb.text
        val withoutYear = catalogue.textWithoutYear == tmdb.textWithoutYear
        if (!exact && !withoutYear) return false

        val left = catalogueYear ?: catalogue.year
        val right = tmdbYear ?: tmdb.year
        if (left == null || right == null) return exact
        return kotlin.math.abs(left - right) <= 1
    }
}
