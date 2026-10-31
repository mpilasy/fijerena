package org.njarasoa.fijerena.core.network.sports

import java.text.Normalizer
import kotlin.math.abs

/**
 * Team names across sources (the sports teams plan → Data sources): [normalize] makes one form of
 * a name, and [sameGame] finds one game in two sources (an ESPN game in the NHL's or MLB's
 * schedule) or in two of ESPN's lists (a followed team's and a followed league's games).
 */
object TeamNames {
    /** Two sources' start times of one game differ by this much at most: 2 hours. */
    const val SAME_GAME_TOLERANCE_SEC = 2 * 60 * 60L

    // Club-form words that sources add or leave out ("FC Barcelona", "AC Milan", "1. FC Köln").
    private val NOISE =
        setOf(
            "fc",
            "sc",
            "cf",
            "ac",
            "afc",
            "cfc",
            "ssc",
            "ss",
            "as",
            "us",
            "fk",
            "sk",
            "bk",
            "nk",
            "gnk",
            "if",
            "sv",
            "cd",
            "sd",
            "ud",
            "rc",
            "rcd",
            "ca",
            "sl",
            "bsc",
            "club",
            "de",
            "the",
            "calcio",
            "football",
            "futbol",
            "futebol",
        )

    // Letters that NFD does not split into a base letter and a mark.
    private val LETTERS = mapOf('ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ß' to "ss", 'đ' to "d", 'ł' to "l", 'ı' to "i", 'þ' to "th")

    private val MARKS = Regex("\\p{Mn}+")
    private val NOT_WORD = Regex("[^a-z0-9]+")

    /**
     * One form of a team name: no accents, lower case, words only, without club-form words
     * ([NOISE]) and numbers ("1. FC Köln" → "koln", "Brighton & Hove Albion" → "brighton and hove
     * albion").
     */
    fun normalize(name: String): String {
        val lower = name.lowercase().replace("&", " and ")
        val folded = buildString { lower.forEach { append(LETTERS[it] ?: it) } }
        val noMarks = MARKS.replace(Normalizer.normalize(folded, Normalizer.Form.NFD), "")
        return noMarks
            .split(NOT_WORD)
            .filter { it.isNotEmpty() && it !in NOISE && !it.all(Char::isDigit) }
            .joinToString(" ")
    }

    /**
     * Whether [a] and [b] are one team: a name of one (its name or another name, not its
     * abbreviation) is a name of the other once normalized, or all the words of one name are in a
     * name of the other ("Arsenal" and "Arsenal London"); the shorter name needs a word of 4
     * letters or more.
     */
    fun sameTeam(
        a: SportsTeam,
        b: SportsTeam,
    ): Boolean {
        val namesA = matchNames(a)
        val namesB = matchNames(b)
        return namesA.any { it in namesB } || namesA.any { x -> namesB.any { y -> contains(x, y) || contains(y, x) } }
    }

    /** Whether [a] and [b] are one game: starts within 2 hours, and both teams the same either way round. */
    fun sameGame(
        a: SportsGame,
        b: SportsGame,
        toleranceSec: Long = SAME_GAME_TOLERANCE_SEC,
    ): Boolean {
        if (abs(a.startEpochSec - b.startEpochSec) > toleranceSec) return false
        val awayA = a.away
        val awayB = b.away
        return when {
            awayA == null || awayB == null -> awayA == null && awayB == null && sameTeam(a.home, b.home)
            else -> (sameTeam(a.home, b.home) && sameTeam(awayA, awayB)) || (sameTeam(a.home, awayB) && sameTeam(awayA, b.home))
        }
    }

    private fun matchNames(team: SportsTeam): Set<String> =
        (listOf(team.name) + team.altNames).map(::normalize).filter { it.isNotEmpty() }.toSet()

    // Whether every word of [short] is in [long], [short] having a word of 4 letters or more.
    private fun contains(
        long: String,
        short: String,
    ): Boolean {
        val shortWords = short.split(' ')
        val longWords = long.split(' ').toSet()
        return shortWords.any { it.length >= MIN_WORD } && longWords.containsAll(shortWords)
    }

    private const val MIN_WORD = 4
}
