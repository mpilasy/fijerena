package org.njarasoa.fijerena.core.network.sports

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** Text folding for name matching: lower case, no accents, words split on anything that is not a letter or a digit. */
internal object SportsText {
    private val MARKS = Regex("\\p{Mn}+")
    private val APOSTROPHES = Regex("['’‘`´]")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SPECIAL =
        mapOf('ø' to "o", 'æ' to "ae", 'œ' to "oe", 'ß' to "ss", 'ł' to "l", 'đ' to "d", 'ı' to "i", 'þ' to "th")

    // One spelling for words that guides write two ways.
    private val WORDS = mapOf("st" to "saint", "ste" to "sainte", "vs" to "v")

    /** [text] folded to words joined by one space ("Paris Saint-Germain" → "paris saint germain"). */
    fun fold(text: String): String {
        val lower = text.lowercase()
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(SPECIAL[c] ?: c)
        return Normalizer
            .normalize(sb, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(APOSTROPHES, "")
            .replace("&", " and ")
            .replace("+", " plus ")
            .split(NOT_WORD)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { WORDS[it] ?: it }
    }

    /** [text] folded, with a space on each side, for whole-word [has] checks. */
    fun padded(text: String?): String = if (text.isNullOrBlank()) " " else " ${fold(text)} "

    /** Whether the [padded] text has the folded [name] as whole words. */
    fun has(
        padded: String,
        name: String,
    ): Boolean = padded.contains(" $name ")
}

/**
 * The names a team can have in a guide title, split into distinctive ones, which may name the team
 * alone, and weak ones (a short or common word such as "Inter", "City", "PSG", or a city name such
 * as "Kansas City"), which count only next to the other team and only in a title.
 */
internal class TeamNameForms(
    val distinctive: Set<String>,
    val weak: Set<String>,
) {
    enum class Hit { NONE, WEAK, DISTINCTIVE }

    fun hit(
        title: String,
        description: String,
    ): Hit =
        when {
            distinctive.any { SportsText.has(title, it) || SportsText.has(description, it) } -> Hit.DISTINCTIVE
            weak.any { SportsText.has(title, it) } -> Hit.WEAK
            else -> Hit.NONE
        }

    val all: Set<String> get() = distinctive + weak

    companion object {
        fun of(team: SportsTeam): TeamNameForms {
            val raw =
                team.allNames
                    .map(SportsText::fold)
                    .filter { it.isNotEmpty() }
                    .distinct()
            val distinctive = LinkedHashSet<String>()
            val weak = LinkedHashSet<String>()
            for (name in raw) {
                // A name that starts another name of the team is its city ("Kansas City" of "Kansas City Chiefs").
                val isCity = raw.any { it != name && it.startsWith("$name ") }
                for (form in forms(name, withAliases = !isCity)) {
                    if (isCity || isWeak(form)) weak += form else distinctive += form
                }
            }
            weak.removeAll(distinctive)
            return TeamNameForms(distinctive, weak)
        }

        private fun isWeak(name: String): Boolean = ' ' !in name && (name.length <= 3 || name in SportsAliases.COMMON_WORDS)

        /** [name] and its other spellings: without club words, with a short city, with an initial, and its aliases. */
        private fun forms(
            name: String,
            withAliases: Boolean,
        ): Set<String> {
            val forms = LinkedHashSet<String>()
            forms += name
            forms += stripClubWords(name)
            for (form in forms.toList()) {
                for ((long, short) in SportsAliases.CITY_SHORT) {
                    if (form.startsWith("$long ")) forms += short + form.removePrefix(long)
                    if (form.startsWith("$short ")) forms += long + form.removePrefix(short)
                }
            }
            for (form in forms.toList()) {
                val words = form.split(' ')
                // "Real Madrid" → "R Madrid", "Crystal Palace" → "C Palace", as guides shorten them.
                if (words.size == 2 && words[0].length >= 3 && words[0].all(Char::isLetter) && words[1].length >= 4) {
                    forms += "${words[0][0]} ${words[1]}"
                }
            }
            if (withAliases) {
                for (form in forms.toList()) {
                    SportsAliases.teamGroup(form)?.forEach { alias ->
                        forms += alias
                        forms += stripClubWords(alias)
                    }
                }
            }
            return forms.filter { it.isNotEmpty() }.toSet()
        }

        /** [name] without leading and trailing club words and numbers ("1. FC Köln" → "koln"), keeping one word at least. */
        fun stripClubWords(name: String): String {
            val words = name.split(' ').toMutableList()

            fun strip(word: String) = word in SportsAliases.CLUB_WORDS || (word.length <= 4 && word.all(Char::isDigit))
            while (words.size > 1 && strip(words.first())) words.removeAt(0)
            while (words.size > 1 && strip(words.last())) words.removeAt(words.size - 1)
            return words.joinToString(" ")
        }
    }
}

/** The names of a competition in guide titles ("English Premier League" → "premier league", "pl"…). */
internal object CompetitionForms {
    fun of(league: SportsLeague): Set<String> {
        val folded = SportsText.fold(league.name)
        if (folded.isEmpty()) return emptySet()
        val forms = LinkedHashSet<String>()
        forms += folded
        var words = folded.split(' ')
        while (words.size > 1 && words.first() in SportsAliases.COMPETITION_PREFIXES) {
            words = words.drop(1)
            forms += words.joinToString(" ")
        }
        for (form in forms.toList()) SportsAliases.competitionGroup(form)?.let { forms += it }
        return forms
    }
}

/** What the matcher needs to know of one game's text: its team and competition names, and how a programme compares. */
internal class GameText(
    game: SportsGame,
) {
    private val start = game.startEpochSec
    private val home = TeamNameForms.of(game.home)
    private val away = game.away?.let(TeamNameForms::of)
    private val competition = CompetitionForms.of(game.competition)
    private val year = Instant.ofEpochSecond(game.startEpochSec).atOffset(ZoneOffset.UTC).year

    /** Every team name, for the guide search. */
    fun searchTerms(): Set<String> = home.all + away?.all.orEmpty()

    /** On a broadcaster's channel: a distinctive name of either team, or names of both. */
    fun isGameOnBroadcaster(p: GuideProgramme): Boolean {
        if (isReplay(p.title)) return false
        val (h, a) = hits(p)
        return h == TeamNameForms.Hit.DISTINCTIVE || a == TeamNameForms.Hit.DISTINCTIVE || bothTeams(h, a)
    }

    /** On any channel: names of both teams, or a distinctive name of one team and the competition. */
    fun isGameAnywhere(p: GuideProgramme): Boolean {
        if (isReplay(p.title)) return false
        val (h, a) = hits(p)
        if (bothTeams(h, a)) return true
        val oneTeam = h == TeamNameForms.Hit.DISTINCTIVE || a == TeamNameForms.Hit.DISTINCTIVE
        // One team is weaker evidence: a highlights show says so in its description ("Kop 10").
        return oneTeam && hasCompetition(p) && !hasReplayWord(p.description)
    }

    /** Higher is better: close to the start, live, not a "coming up" filler, both teams named. */
    fun score(p: GuideProgramme): Int {
        val title = SportsText.padded(p.title)
        var score = 1000 - (abs(p.startSec - start) / 60).toInt()
        if (SportsAliases.LIVE_WORDS.any { SportsText.has(title, it) }) score += 30
        if (SportsAliases.FILLER_WORDS.any { SportsText.has(title, it) }) score -= 200
        val (h, a) = hits(p)
        if (bothTeams(h, a)) score += 10
        return score
    }

    private fun hits(p: GuideProgramme): Pair<TeamNameForms.Hit, TeamNameForms.Hit> {
        val title = SportsText.padded(p.title)
        val description = SportsText.padded(p.description)
        return home.hit(title, description) to (away?.hit(title, description) ?: TeamNameForms.Hit.NONE)
    }

    private fun bothTeams(
        h: TeamNameForms.Hit,
        a: TeamNameForms.Hit,
    ) = away != null && h != TeamNameForms.Hit.NONE && a != TeamNameForms.Hit.NONE

    private fun hasCompetition(p: GuideProgramme): Boolean {
        val texts = listOf(SportsText.padded(p.title), SportsText.padded(p.description), SportsText.padded(p.category))
        return competition.any { name -> texts.any { SportsText.has(it, name) } }
    }

    private fun hasReplayWord(text: String?): Boolean {
        val padded = SportsText.padded(text)
        return SportsAliases.REPLAY_WORDS.any { SportsText.has(padded, it) }
    }

    /** Highlights, a replay, a classic, or a season or year that is not the game's. */
    fun isReplay(title: String): Boolean {
        if (hasReplayWord(title)) return true
        SEASON.findAll(title).forEach { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[2].toInt()
            if ((first + 1) % 100 == second % 100) {
                val startYear = if (first < 100) 2000 + first else first
                if (startYear != year && startYear != year - 1) return true
            }
        }
        YEAR.findAll(title).forEach { m ->
            if (abs(m.value.toInt() - year) > 1) return true
        }
        return false
    }

    private companion object {
        // "17/18", "2008/09": a season.
        val SEASON = Regex("(?<![\\d/])(\\d{2}|\\d{4})\\s?/\\s?(\\d{2}|\\d{4})(?![\\d/])")

        // "2008 UCL Final": a year on its own.
        val YEAR = Regex("(?<![\\d/])(19|20)\\d{2}(?![\\d/])")
    }
}
