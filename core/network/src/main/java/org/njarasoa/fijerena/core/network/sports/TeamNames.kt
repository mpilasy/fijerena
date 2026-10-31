package org.njarasoa.fijerena.core.network.sports

import java.text.Normalizer
import kotlin.math.abs

/**
 * Team names across sources (the sports teams plan → Teams across sources): ESPN says "Slavia
 * Prague", UEFA "Slavia Praha", FIFA "SK Slavia Praha". [normalize] makes one form of a name, and
 * [ALIASES] joins the forms that still differ. [sameGame] finds one game in two sources.
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
     * Normalized name → the form used for all of them, for the misses found in tests. Keys and
     * values are normalized ([normalize] without aliases).
     */
    private val ALIASES: Map<String, String> =
        listOf(
            "manchester united" to listOf("man utd", "man united", "manchester utd"),
            "manchester city" to listOf("man city"),
            "tottenham hotspur" to listOf("tottenham", "spurs"),
            "wolverhampton wanderers" to listOf("wolves", "wolverhampton"),
            "brighton and hove albion" to listOf("brighton", "brighton hove albion"),
            "newcastle united" to listOf("newcastle"),
            "west ham united" to listOf("west ham"),
            "nottingham forest" to listOf("nottm forest", "nott m forest"),
            "sheffield united" to listOf("sheffield utd", "sheff utd"),
            "leeds united" to listOf("leeds"),
            "inter" to listOf("internazionale", "inter milan", "inter milano"),
            "paris saint germain" to listOf("psg", "paris sg", "paris st germain"),
            "bayern munchen" to listOf("bayern", "bayern munich"),
            "borussia dortmund" to listOf("b dortmund", "dortmund", "bvb"),
            "borussia monchengladbach" to listOf("b monchengladbach", "monchengladbach", "gladbach"),
            "bayer leverkusen" to listOf("leverkusen"),
            "rb leipzig" to listOf("leipzig", "rasenballsport leipzig"),
            "atletico madrid" to listOf("atletico", "atl madrid", "club atletico madrid"),
            "real betis" to listOf("betis", "real betis balompie"),
            "athletic club" to listOf("athletic bilbao"),
            "sporting cp" to listOf("sporting", "sporting lisbon", "sporting clube portugal"),
            "slavia praha" to listOf("slavia prague"),
            "sparta praha" to listOf("sparta prague"),
            "shakhtar donetsk" to listOf("shakhtar"),
            "lyon" to listOf("olympique lyonnais", "olympique lyon"),
            "marseille" to listOf("olympique marseille"),
            "psv eindhoven" to listOf("psv"),
            "brugge" to listOf("bruges"),
            "crvena zvezda" to listOf("red star belgrade", "red star"),
            "salzburg" to listOf("red bull salzburg", "rb salzburg"),
            "copenhagen" to listOf("kobenhavn"),
            "aek athens" to listOf("aek"),
            "olympiacos" to listOf("olympiakos", "olympiacos piraeus"),
            "union saint gilloise" to listOf("union sg", "royale union saint gilloise", "union st gilloise"),
            "young boys" to listOf("bern young boys"),
        ).flatMap { (canonical, others) -> (others + canonical).map { it to canonical } }.toMap()

    /**
     * One form of a team name: no accents, lower case, words only, without club-form words
     * ([NOISE]) and numbers ("1. FC Köln" → "koln", "Brighton & Hove Albion" → "brighton and hove
     * albion"), then the alias form when there is one.
     */
    fun normalize(name: String): String {
        val plain = words(name)
        return ALIASES[plain] ?: plain
    }

    private fun words(name: String): String {
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
