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
        // Most channel names are plain ASCII: no letter to fold or accent to strip.
        val noMarks =
            if (lower.all { it.code < 128 }) {
                lower
            } else {
                val sb = StringBuilder(lower.length)
                for (c in lower) sb.append(SPECIAL[c] ?: c)
                Normalizer.normalize(sb, Normalizer.Form.NFD).replace(MARKS, "")
            }
        return noMarks
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

    // The first year of the game's season when a league plays from autumn to spring: 2026 for November 2026.
    private val seasonYear =
        Instant.ofEpochSecond(game.startEpochSec).atOffset(ZoneOffset.UTC).let { date ->
            if (date.monthValue >= SEASON_START_MONTH) date.year else date.year - 1
        }

    /** Every team name, for the guide search. */
    fun searchTerms(): Set<String> = home.all + away?.all.orEmpty()

    /** Every word of the team names: a date word such as "Sun" in one is not read as a date ([ChannelNameDates]). */
    val nameWords: Set<String> by lazy { searchTerms().flatMap { it.split(' ') }.toSet() }

    /**
     * The first word of every name of each team: a channel name that names the game has one word of
     * each list. The matcher looks these up in its word index before the full check ([namesGame]).
     */
    val homeFirstWords: Set<String> get() = home.all.mapTo(HashSet()) { it.substringBefore(' ') }

    /** See [homeFirstWords]; null for an event with no second team. */
    val awayFirstWords: Set<String>? get() = away?.all?.mapTo(HashSet()) { it.substringBefore(' ') }

    /**
     * Whether a channel name names the game, for a PPV or event channel ("UK| PPV 3: Bayern v Ajax",
     * "DAZN EVENT 2 - Real Madrid vs Barcelona"): names of both teams, a weak name ("Inter", "KC")
     * only next to the other team, and not a replay. [padded] is [name] as [SportsText.padded] gives
     * it. A club's own channel ("FC Bayern TV") or a competition's ("UK: EPL ARSENAL") names one
     * team only, so it does not match by name; its guide programme can (steps 1 and 2).
     */
    fun namesGame(
        name: String,
        padded: String,
    ): Boolean {
        val away = away ?: return false
        val h = home.hit(padded, " ")
        if (h == TeamNameForms.Hit.NONE) return false
        val a = away.hit(padded, " ")
        if (a == TeamNameForms.Hit.NONE) return false
        if (h == TeamNameForms.Hit.WEAK && !nextTo(padded, home.weak, away.all)) return false
        if (a == TeamNameForms.Hit.WEAK && !nextTo(padded, away.weak, home.all)) return false
        return !isReplay(name, seasons = false)
    }

    /** On a broadcaster's channel: a distinctive name of either team, or names of both; not one team against another side. */
    fun isGameOnBroadcaster(p: GuideProgramme): Boolean {
        if (isReplay(p.title)) return false
        val (h, a) = hits(p)
        if (bothTeams(p, h, a)) return true
        return (h == TeamNameForms.Hit.DISTINCTIVE || a == TeamNameForms.Hit.DISTINCTIVE) && !facesAnother(p.title)
    }

    /**
     * On any channel: names of both teams, or a distinctive name of one team and the competition,
     * when the title does not set that team against another side ("Arsenal v Hull City").
     */
    fun isGameAnywhere(p: GuideProgramme): Boolean {
        if (isReplay(p.title)) return false
        val (h, a) = hits(p)
        if (bothTeams(p, h, a)) return true
        val oneTeam = h == TeamNameForms.Hit.DISTINCTIVE || a == TeamNameForms.Hit.DISTINCTIVE
        // One team is weaker evidence: a highlights show says so in its description ("Kop 10").
        return oneTeam && hasCompetition(p) && !hasReplayWord(p.description) && !facesAnother(p.title)
    }

    // Whether a name of [weak] is next to a name of [other] ("inter milan", "kc v buf", "kc at buf").
    private fun nextTo(
        padded: String,
        weak: Set<String>,
        other: Set<String>,
    ): Boolean =
        weak.any { w ->
            SportsText.has(padded, w) &&
                other.any { o -> PAIR_WORDS.any { sep -> padded.contains(" $w$sep$o ") || padded.contains(" $o$sep$w ") } }
        }

    /**
     * Whether [title] names one of the teams against a side that is not the other team: a pair
     * "X v Y", "X vs Y", "X - Y", "X @ Y" or "X at Y" where one side names a team of the game and
     * the other side names neither the other team nor the competition.
     */
    private fun facesAnother(title: String): Boolean {
        val away = away ?: return false
        for (segment in title.split(SEGMENT)) {
            val m = PAIR.find(segment) ?: continue
            val left = SportsText.padded(segment.substring(0, m.range.first))
            val right = SportsText.padded(segment.substring(m.range.last + 1))
            for ((ours, theirs) in listOf(left to right, right to left)) {
                val team =
                    when {
                        home.all.any { SportsText.has(ours, it) } -> away
                        away.all.any { SportsText.has(ours, it) } -> home
                        else -> continue
                    }
                val other = theirs.trim()
                if (other.isEmpty() || team.all.any { SportsText.has(theirs, it) }) continue
                if (competition.any { SportsText.has(theirs, it) }) continue
                if (other.split(' ').all { it in SportsAliases.LIVE_WORDS }) continue
                return true
            }
        }
        return false
    }

    /** Higher is better: close to the start, live, not a "coming up" filler, both teams named. */
    fun score(p: GuideProgramme): Int {
        val title = SportsText.padded(p.title)
        var score = 1000 - (abs(p.startSec - start) / 60).toInt()
        if (SportsAliases.LIVE_WORDS.any { SportsText.has(title, it) }) score += 30
        if (SportsAliases.FILLER_WORDS.any { SportsText.has(title, it) }) score -= 200
        val (h, a) = hits(p)
        if (bothTeams(p, h, a)) score += 10
        return score
    }

    private fun hits(p: GuideProgramme): Pair<TeamNameForms.Hit, TeamNameForms.Hit> {
        val title = SportsText.padded(p.title)
        val description = SportsText.padded(p.description)
        return home.hit(title, description) to (away?.hit(title, description) ?: TeamNameForms.Hit.NONE)
    }

    /**
     * Names of both teams in [p]; a weak name only next to the other team's, as in a channel name
     * ([namesGame]): "Minnesota v San Jose" is the game, "Minnesota Wild – San Jose Sharks" is not.
     */
    private fun bothTeams(
        p: GuideProgramme,
        h: TeamNameForms.Hit,
        a: TeamNameForms.Hit,
    ): Boolean {
        val away = away ?: return false
        if (h == TeamNameForms.Hit.NONE || a == TeamNameForms.Hit.NONE) return false
        if (h != TeamNameForms.Hit.WEAK && a != TeamNameForms.Hit.WEAK) return true
        val title = SportsText.padded(p.title)
        return (h != TeamNameForms.Hit.WEAK || nextTo(title, home.weak, away.all)) &&
            (a != TeamNameForms.Hit.WEAK || nextTo(title, away.weak, home.all))
    }

    private fun hasCompetition(p: GuideProgramme): Boolean {
        val texts = listOf(SportsText.padded(p.title), SportsText.padded(p.description), SportsText.padded(p.category))
        return competition.any { name -> texts.any { SportsText.has(it, name) } }
    }

    private fun hasReplayWord(text: String?): Boolean {
        val padded = SportsText.padded(text)
        return SportsAliases.REPLAY_WORDS.any { SportsText.has(padded, it) }
    }

    /**
     * Highlights, a replay ("Replay", "Re-air", "Rediffusion", "(R)"), a classic, or a season or
     * year that is not the game's. A channel name ([seasons] false) is read only for long seasons
     * ("2026/2027"): its "10/11" is a date.
     */
    fun isReplay(
        title: String,
        seasons: Boolean = true,
    ): Boolean {
        if (hasReplayWord(title) || RERUN_MARK.containsMatchIn(title)) return true
        (if (seasons) SEASON else LONG_SEASON).findAll(title).forEach { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[2].toInt()
            if ((first + 1) % 100 == second % 100) {
                val startYear = if (first < 100) 2000 + first else first
                // A channel name is named for one game: its season must be the game's.
                if (if (seasons) startYear != year && startYear != year - 1 else startYear != seasonYear) return true
            }
        }
        YEAR.findAll(title).forEach { m ->
            if (abs(m.value.toInt() - year) > 1) return true
        }
        return false
    }

    private companion object {
        // July: an autumn-to-spring season starts then.
        const val SEASON_START_MONTH = 7

        // "17/18", "2008/09": a season.
        val SEASON = Regex("(?<![\\d/])(\\d{2}|\\d{4})\\s?/\\s?(\\d{2}|\\d{4})(?![\\d/])")

        // "2026/2027", "2025/26": a season, as a channel name writes it.
        val LONG_SEASON = Regex("(?<![\\d/])(\\d{4})\\s?/\\s?(\\d{2}|\\d{4})(?![\\d/])")

        // "(R)": a rerun.
        val RERUN_MARK = Regex("\\(\\s*[rR]\\s*\\)")

        // The words that set two sides against each other in a title.
        val PAIR = Regex("\\s(?:v|vs|v\\.|vs\\.|at|@|-|–|—)\\s", RegexOption.IGNORE_CASE)

        // The parts of a title that each hold one thing ("Premier League: Arsenal v Hull City | Live").
        val SEGMENT = Regex("[|:()\\[\\]]")

        // What may join two team names in a folded name: nothing, "v" ("vs" folds to "v") or "at".
        val PAIR_WORDS = listOf(" ", " v ", " at ")

        // "2008 UCL Final": a year on its own.
        val YEAR = Regex("(?<![\\d/])(19|20)\\d{2}(?![\\d/])")
    }
}
