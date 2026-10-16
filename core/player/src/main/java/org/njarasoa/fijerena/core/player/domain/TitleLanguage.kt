package org.njarasoa.fijerena.core.player.domain

/**
 * Provider metadata routinely bakes a language/region code into the title string itself —
 * `"EN - Breaking Bad"`, `"NP:Kantipur"`, `"DE| ZDF"`, `"Breaking Bad (US)"` — and more besides:
 * the picture format (`"Joker 4K (2019)"`, `"RTL HEVC"`, `"ᵁᴴᴰ"`), the audio or subtitles
 * (`"(FRENCH ENG-SUB)"`, `"(Ger Sub)"`, `"POLSKI"`), a timeshift (`"+6H"`) or an events-only
 * channel (`"[LIVE-EVENT]"`). Parsed once here and consumed by every surface that renders
 * [MediaItem.name] (mobile's `StreamCard`, TV's `StreamList`, both platforms' `RelatedTitlesRow`)
 * so the title shown to the user is clean and the code becomes its own small badge, the rest its
 * own [tags], instead of visual noise baked into the title.
 */
data class ParsedTitle(
    val title: String,
    val badge: String?,
    /**
     * Picture formats, one per kind ("4K", "HEVC", "HDR", "60 fps", "LQ"), then the provider's own
     * words for the audio, subtitles or timeshift ("FRENCH ENG-SUB", "Ger Sub", "POLSKI", "+6H").
     */
    val tags: List<String> = emptyList(),
    /** The channel only carries live events ("[LIVE-EVENT]", "(SOLO EVENTOS)"); the screen names it. */
    val eventsOnly: Boolean = false,
)

// Requires the code to be all-uppercase letters so ordinary title casing ("A-Team") never
// matches — real prefixes/suffixes from provider feeds are consistently shouted like "EN", "FR".
// D+/A+ (Disney+/Apple TV+) are the exception: accepted in either case, badged uppercase.
private const val CODE = "[A-Z]{2,4}|[DdAa]\\+"
private val PREFIX_CODE = Regex("^($CODE)\\s*-\\s*")

// Before a colon, only two letters ("NP:Kantipur", "SE: Kanal 5") or a league ("NHL: Bruins vs
// Leafs"): longer capitals there start a title ("TRON: Legacy", "DOA: Dead or Alive").
private val COLON_CODE = Regex("^([A-Z]{2}|[DdAa]\\+|NHL|NFL|NBA|MLB|ATP|WTA|SHL|UFC)\\s*:\\s*")
private val SUFFIX_CODE = Regex("\\s*\\(($CODE)\\)\\s*$")

// Live channels: whatever comes right before a "|" is the code, as the provider writes it —
// "DE|", "IN-PREM|", "UK-NOWTV|", "ES-C|", "PRIME|", "PLAY+|", "U.S|". Event lines put a space
// before theirs ("Live | Fluminense - Palmeiras | …") and are left alone.
private val PIPE_PREFIX = Regex("""^\|?([^\s|]{1,12})\|\s*""")

// Two words for one code: "BLURAY-DE - …", "WESTERN-DE - …", "IN-TA - …", "DE-TOP - …".
private val PAIR_PREFIX = Regex("""^([A-Z]{2,7}-[A-Z]{2,7})\s+-\s+""")

// A leading group holding a picture format: "4K-NF - …", "4K-FR-HDR - …", "4K-OSN+ - …", "4K: …",
// "|4K| …", "SD/CAM - …", "PL 4K - …", "[4K-DV-HDR] - …" (after "DE- "), "DE-4K-BLURAY-DV - …".
// Its other parts are the badge.
private val PREFIX_GROUP = Regex("""^\|?\s*\[?([A-Za-z0-9.+]+(?:[-/ ][A-Za-z0-9.+]+)*)]?\s*([-:|])(\s*)""")
private val GROUP_PART = Regex("[A-Z]{2,7}\\+?|[DdAa]\\+")
private val GROUP_SPLIT = Regex("[-/ ]")

// Tags sit at the end: "ZDF HD", "Titanic (1997) HEVC", "TF1 HDR/UHD/4K", "Joker 4K (2019)",
// "Suits (FHD) (2011) (US)", "Zeta [UHD] (2026)", "NARUTO-HD ᴿᴬᵂ", "(2021) POLSKI 1080P". Walking
// back from the end, a year or code in brackets and a superscript or symbol word ("ᴿᴬᵂ", "◉") are
// stepped over and kept.
private val END_BRACKET = Regex("""[(\[]([^()\[\]]*)[)\]]$""")
private val END_WORD = Regex("""(^|[\s/-])([^\s/-]+)$""")
private val KEPT_BRACKET = Regex("\\d{4}|$CODE")
private val BRACKET_WORDS = Regex("[\\s/@-]+")

// Audio and subtitles in brackets, kept in the provider's words: "(FRENCH ENG-SUB)", "(MULTI SUB)",
// "(Ger Sub)", "(VOSTFR)", "(Svenskt Tal)", "(Svensk Text)", "[MULTI-AUDIO]", "(DUB)", "(БГ Аудио)",
// "(FRENCH ONLY)".
private val NOTE_BRACKET =
    Regex(
        """(?<![\p{L}\d])(SUBS?|ENG-SUB|MULTI-SUB|MULTI-AUDIO|MULTI|VOSTFR|DUB|DUBBED|AUDIO|АУДИО|TAL|TEXT|ONLY)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE,
    )
private const val NOTE_MAX_LENGTH = 30

// The same as a last word: "VOSTFR", Polish "POLSKI" / "DUBBING" / "NAPISY" (subtitles), and a
// live timeshift "+6H".
private val NOTE_WORDS = setOf("VOSTFR", "POLSKI", "DUBBING", "NAPISY")
private val TIMESHIFT = Regex("\\+\\d{1,2}H?", RegexOption.IGNORE_CASE)

// Channels that only carry live events: "[LIVE-EVENT]", "[LIVEEVENT]", "[EVENT]", "(EVENT ONLY)",
// "(LIVE DURING EVENTS ONLY)", "(DURING GAMES ONLY)", "(SOLO EVENTOS)",
// "(NUR WÄHREND DER LIVE SPIELE)", "… EVENTS ONLY".
private val EVENTS_BRACKET =
    Regex("""(?<!\p{L})(LIVE-?EVENTS?|EVENTS?|EVENTOS|SPIELE|GAMES)(?!\p{L})""", RegexOption.IGNORE_CASE)
private val EVENTS_END = Regex("\\s+EVENTS? ONLY$", RegexOption.IGNORE_CASE)

private enum class FormatKind { RESOLUTION, CODEC, COLOUR, FRAME_RATE, GRADE }

private class Format(
    val kind: FormatKind,
    val label: String,
    val rank: Int,
)

// Every spelling the catalogues use, upper case, superscripts read as letters ("ᵁᴴᴰ" is UHD).
// RAW, 3D and IMAX are left out: they are as often part of the title ("Saw 3D", "WWE Raw").
private val FORMATS: Map<String, Format> =
    buildMap {
        fun add(
            kind: FormatKind,
            label: String,
            rank: Int,
            vararg spellings: String,
        ) = spellings.forEach { put(it, Format(kind, label, rank)) }
        add(FormatKind.RESOLUTION, "8K", 6, "8K", "4320P")
        add(FormatKind.RESOLUTION, "4K", 5, "4K", "UHD", "2160P", "3840P")
        add(FormatKind.RESOLUTION, "QHD", 4, "QHD", "1440P")
        add(FormatKind.RESOLUTION, "FHD", 3, "FHD", "1080P", "1080I")
        add(FormatKind.RESOLUTION, "HD", 2, "HD", "720P")
        add(FormatKind.RESOLUTION, "SD", 1, "SD", "576P", "480P")
        add(FormatKind.CODEC, "HEVC", 1, "HEVC", "H265", "H.265", "X265")
        add(FormatKind.COLOUR, "DV", 2, "DV")
        add(FormatKind.COLOUR, "HDR", 1, "HDR", "HDR10", "HDR10+")
        add(FormatKind.FRAME_RATE, "60 fps", 2, "60FPS")
        add(FormatKind.FRAME_RATE, "50 fps", 1, "50FPS")
        add(FormatKind.GRADE, "HQ", 2, "HQ")
        add(FormatKind.GRADE, "LQ", 1, "LQ")
    }

private const val SUPERSCRIPTS = "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ⁰¹²³⁴⁵⁶⁷⁸⁹"
private const val SUPERSCRIPTS_READ = "ABDEGHIJKLMNOPRTUVWabcdefghijklmnoprstuvwxyz0123456789"

private fun format(word: String): Format? {
    val read = word.map { c -> SUPERSCRIPTS.indexOf(c).let { if (it < 0) c else SUPERSCRIPTS_READ[it] } }.joinToString("")
    return FORMATS[read.uppercase()]
}

private fun formatsOf(group: String): List<Format>? {
    val words = group.split(BRACKET_WORDS).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.map { format(it) ?: return null }
}

/** "ᴿᴬᵂ", "◉": stepped over at the end of a title, not read as its last word. */
private fun isMark(word: String): Boolean = word.all { !it.isLetterOrDigit() || it in SUPERSCRIPTS }

private class Prefix(
    val length: Int,
    val formats: List<Format>,
    val code: String?,
)

/** A leading group's formats and its other parts as the code, or null when it holds no format. */
private fun formatPrefix(text: String): Prefix? {
    val match = PREFIX_GROUP.find(text) ?: return null
    val (group, separator, space) = match.destructured
    // "4K-Ultra Nature" is a title: a dash only separates with a space after it.
    if (separator == "-" && space.isEmpty()) return null
    val parts = group.split(GROUP_SPLIT)
    val formats = parts.mapNotNull(::format)
    if (formats.isEmpty()) return null
    val codes = parts.filter { format(it) == null }
    if (!codes.all(GROUP_PART::matches)) return null
    return Prefix(match.range.last + 1, formats, codes.joinToString("-").uppercase().ifEmpty { null })
}

/** A leading code: "DE|", "IN-PREM|", "BLURAY-DE - ", "EN - ", "NP:". */
private fun codePrefix(text: String): Prefix? =
    (PIPE_PREFIX.find(text) ?: PAIR_PREFIX.find(text) ?: PREFIX_CODE.find(text) ?: COLON_CODE.find(text))?.let {
        Prefix(it.range.last + 1, emptyList(), it.groupValues[1].uppercase())
    }

private class Ending(
    val text: String,
    val formats: List<Format>,
    val notes: List<String>,
    val eventsOnly: Boolean,
)

/** [text] without the formats, notes and events mark at its end. */
private fun stripEnding(text: String): Ending {
    var rest = text.trimEnd()
    var kept = ""
    val formats = mutableListOf<Format>()
    val notes = mutableListOf<String>()
    var eventsOnly = false
    EVENTS_END.find(rest)?.let {
        if (it.range.first > 0) {
            eventsOnly = true
            rest = rest.substring(0, it.range.first).trimEnd()
        }
    }
    while (true) {
        val bracket = END_BRACKET.find(rest)
        if (bracket != null) {
            val content = bracket.groupValues[1].trim()
            val found = formatsOf(content)
            val before = rest.substring(0, bracket.range.first).trimEnd()
            when {
                before.isEmpty() -> break
                found != null -> formats += found
                content.length <= NOTE_MAX_LENGTH && EVENTS_BRACKET.containsMatchIn(content) -> eventsOnly = true
                content.length <= NOTE_MAX_LENGTH && NOTE_BRACKET.containsMatchIn(content) -> notes += content
                KEPT_BRACKET.matches(content) -> kept = "${bracket.value} $kept"
                else -> break
            }
            rest = before
            continue
        }
        val word = END_WORD.find(rest) ?: break
        val last = word.groupValues[2]
        val before = rest.substring(0, word.range.first).trimEnd(' ', '-', '/')
        val found = format(last)
        when {
            before.isBlank() -> break
            found != null -> formats += found
            last.uppercase() in NOTE_WORDS || TIMESHIFT.matches(last) -> notes += last
            isMark(last) -> kept = "$last $kept"
            else -> break
        }
        rest = before
    }
    if (formats.isEmpty() && notes.isEmpty() && !eventsOnly) return Ending(text, emptyList(), emptyList(), false)
    return Ending("$rest $kept".trim(), formats, notes.reversed(), eventsOnly)
}

/** One per kind, the highest ("UHD 8K" is 8K), in kind order. */
private fun labels(formats: List<Format>): List<String> =
    formats
        .groupBy { it.kind }
        .toSortedMap()
        .map { (_, same) -> same.maxBy { it.rank }.label }

/**
 * Strips a leading `EN -`/`NP:`/`DE|`/`IN-PREM|`/`4K-NF -`/`4K:` style prefix or a trailing `(US)`
 * style suffix off [raw] and returns the cleaned title plus the code as a badge; picture formats at
 * the start or the end, and the audio, subtitle and timeshift words at the end, become
 * [ParsedTitle.tags]. When both a prefix and a suffix code are present, the prefix wins — it is the
 * more common shape and carries the language, the more useful of the two. Returns [raw] verbatim
 * (trimmed) with a null badge and no tags when nothing matches.
 */
fun parseDisplayTitle(raw: String): ParsedTitle {
    var text = raw
    var badge: String? = null
    val formats = mutableListOf<Format>()

    // Up to two: "DE- [4K-DV-HDR] - …" is a code then formats, "EN - EN - …" the code twice.
    repeat(2) {
        val prefix = formatPrefix(text) ?: codePrefix(text)?.takeIf { (badge == null && formats.isEmpty()) || it.code == badge }
        if (prefix == null || (badge != null && prefix.code != null && prefix.code != badge)) return@repeat
        formats += prefix.formats
        if (badge == null) badge = prefix.code
        text = text.substring(prefix.length)
    }
    val ending = stripEnding(text)
    text = ending.text
    formats += ending.formats
    SUFFIX_CODE.find(text)?.let { match ->
        if (badge == null) badge = match.groupValues[1].uppercase()
        text = text.removeRange(match.range)
    }

    return ParsedTitle(
        title = text.trim(),
        badge = badge,
        tags = labels(formats) + ending.notes,
        eventsOnly = ending.eventsOnly,
    )
}

/**
 * [episodeTitle] without its series' raw name in front: providers title episodes
 * "EN - The King of Queens - S01E25", and a card that already shows the series keeps "S01E25".
 * Returned unchanged when it doesn't start with "[seriesName] - ".
 */
fun episodeTitleWithoutSeries(
    episodeTitle: String,
    seriesName: String,
): String = episodeTitle.removePrefix("$seriesName - ").ifBlank { episodeTitle }

// "EN - The King of Queens - S01E22", "4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do":
// anything before the episode number, the number, then the episode's own title if there is one.
private val NUMBERED_EPISODE_TITLE = Regex("""^(?:.*?\s-\s)?S\d{1,3}\s?E\d{1,4}(?:\s-\s(.*))?$""", RegexOption.IGNORE_CASE)

/**
 * The episode's own title out of a provider's episode title: "Whatever You Do" from
 * "4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do", and "" from
 * "EN - The King of Queens - S01E22", which only repeats the show and the number the screen
 * already shows. A title without an "S01E22" number ("The Body in the Library") is returned as is.
 */
fun episodeOwnTitle(raw: String): String {
    val text = raw.trim()
    val match = NUMBERED_EPISODE_TITLE.matchEntire(text) ?: return text
    return match.groupValues[1].trim()
}

/**
 * The episode name the player shows under its series title: [episodeOwnTitle] when the title
 * carries an "S01E22" number ("" when nothing follows it), else the last " - " segment, since
 * providers that embed the show name without a number still put the episode name last.
 */
fun playerEpisodeName(raw: String): String {
    val own = episodeOwnTitle(raw)
    return if (own != raw.trim()) own else raw.substringAfterLast(" - ").trim()
}
