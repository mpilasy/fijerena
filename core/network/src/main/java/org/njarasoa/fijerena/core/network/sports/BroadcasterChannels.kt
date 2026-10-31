package org.njarasoa.fijerena.core.network.sports

import java.util.Locale

/**
 * Matches a broadcaster ("Canal+ Sport", "beIN Sports", "SuperSport") to the profile's live channels
 * by name ("FR: CANAL+ SPORT 360 FHD", "beIN 1", "SS Premier League HD"). A channel's country tag
 * ("FR:", "UK|", "US -", "DE ▎", "(FR)") must agree with the broadcaster's country when both are known.
 */
internal object BroadcasterChannels {
    /**
     * A channel name without its country tag and quality words, as words, and the country of the tag.
     * Not [org.njarasoa.fijerena.core.network.xmltv.ChannelNameNormalizer]: it drops "+", so "ESPN+"
     * (a streaming service) would be "ESPN", and it joins the words, so "Canal+" would start "Canal J".
     */
    data class ParsedChannel(
        val country: String?,
        val words: List<String>,
    ) {
        /** [words] joined with no space ("bbcone"), to match "BBCOne" to "BBC One". */
        val joined: String = words.joinToString("")
    }

    private val PREFIX = Regex("^[^\\p{L}\\p{N}]*([A-Za-z]{2,4})\\s*(?:[:|\\]\\)▎│┃]+|\\s[-–]|-)\\s*")
    private val BRACKET = Regex("[\\[(]([A-Za-z]{2,4})[\\])]")
    private val LETTER_DIGIT = Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")

    private val QUALITY =
        setOf(
            "fhd",
            "uhd",
            "hd",
            "sd",
            "4k",
            "8k",
            "hevc",
            "h265",
            "h264",
            "1080p",
            "1080i",
            "720p",
            "50fps",
            "60fps",
            "hdr",
            "hdr10",
            "raw",
            "backup",
            "vip",
            "lq",
            "hq",
        )

    // Tags that name a language or a region, not a country.
    private val NO_COUNTRY = setOf("EN", "AR", "ARB", "ARA", "AFR", "LAT", "LATAM", "EXYU", "EU", "VIP", "TV", "HD", "SD")

    private val THREE_LETTER =
        mapOf(
            "UK" to "GB",
            "GBR" to "GB",
            "USA" to "US",
            "FRA" to "FR",
            "GER" to "DE",
            "DEU" to "DE",
            "ESP" to "ES",
            "ITA" to "IT",
            "POR" to "PT",
            "PRT" to "PT",
            "NED" to "NL",
            "NLD" to "NL",
            "BEL" to "BE",
            "SUI" to "CH",
            "CAN" to "CA",
            "MEX" to "MX",
            "BRA" to "BR",
            "ARG" to "AR",
            "AUS" to "AU",
            "RSA" to "ZA",
            "ZAF" to "ZA",
            "MDG" to "MG",
            "IRL" to "IE",
            "POL" to "PL",
            "TUR" to "TR",
            "KSA" to "SA",
        )

    private val ISO = Locale.getISOCountries().toSet()

    /** Word sequences that one channel family writes several ways, and the one spelling used here. */
    private val SPELLINGS =
        listOf(
            listOf("c", "plus") to listOf("canal", "plus"),
            listOf("cplus") to listOf("canal", "plus"),
            listOf("canalplus") to listOf("canal", "plus"),
            listOf("ss") to listOf("supersport"),
            listOf("super", "sport") to listOf("supersport"),
            listOf("skysp") to listOf("sky", "sport"),
            listOf("sky", "sp") to listOf("sky", "sport"),
            listOf("bt", "sport") to listOf("tnt", "sport"),
            listOf("euro", "sport") to listOf("eurosport"),
            listOf("la", "chaine", "lequipe") to listOf("lequipe"),
        )

    // Words of a channel that shows sport, to choose among a family's channels when no programme is known.
    private val SPORT_WORDS =
        setOf(
            "sport",
            "supersport",
            "eurosport",
            "foot",
            "football",
            "fussball",
            "futbol",
            "soccer",
            "nfl",
            "nba",
            "nhl",
            "mlb",
            "liga",
            "league",
            "premier",
            "arena",
            "action",
            "events",
            "golf",
            "racing",
            "cricket",
            "rugby",
            "tennis",
            "f1",
        )

    // Words that make another network of a network's name: "CBS Sports Network" is not "CBS", "ESPN Deportes" is not "ESPN".
    private val OTHER_NETWORK_WORDS = setOf("sport", "network", "news", "deportes", "college", "extra", "xtra", "classic")

    /** The country code of a channel tag, or null for no tag, an unknown tag or a language tag. */
    private fun country(tag: String): String? {
        val upper = tag.uppercase()
        if (upper in NO_COUNTRY) return null
        THREE_LETTER[upper]?.let { return it }
        return upper.takeIf { it.length == 2 && it in ISO }
    }

    private fun isTag(tag: String): Boolean {
        val upper = tag.uppercase()
        return upper in NO_COUNTRY || upper in THREE_LETTER || (upper.length == 2 && upper in ISO)
    }

    fun parse(name: String): ParsedChannel {
        var rest = name
        var country: String? = null
        PREFIX.find(rest)?.let { m ->
            val tag = m.groupValues[1]
            if (isTag(tag) && m.range.last + 1 < rest.length) {
                country = country(tag)
                rest = rest.substring(m.range.last + 1)
            }
        }
        BRACKET.findAll(rest).firstOrNull { isTag(it.groupValues[1]) }?.let { m ->
            if (country == null) country = country(m.groupValues[1])
            rest = rest.removeRange(m.range)
        }
        return ParsedChannel(country, words(rest))
    }

    fun broadcasterTokens(name: String): List<String> = words(name)

    /** [text] as words: folded, without quality words, letters and digits apart, in one spelling. */
    private fun words(text: String): List<String> {
        val split =
            SportsText
                .fold(text)
                .split(' ')
                .filter { it.isNotEmpty() && it !in QUALITY }
                .flatMap { it.split(LETTER_DIGIT) }
                .map { if (it == "sports") "sport" else it }
        return respell(split)
    }

    private fun respell(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size + 1)
        var i = 0
        while (i < words.size) {
            val rule = SPELLINGS.firstOrNull { (from, _) -> words.startsWith(from, i) }
            if (rule != null) {
                out += rule.second
                i += rule.first.size
                continue
            }
            out += words[i]
            // "beIN 1", "beIN MAX 4" → "bein sport 1", "bein sport max 4".
            if (words[i] == "bein" && words.getOrNull(i + 1) != "sport") out += "sport"
            i++
        }
        return out
    }

    private fun List<String>.startsWith(
        prefix: List<String>,
        at: Int,
    ): Boolean = at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }

    /** Whether [channel] is one of [broadcaster]'s channels; [tokens] are [broadcasterTokens] of its name. */
    fun matches(
        broadcaster: Broadcaster,
        tokens: List<String>,
        channel: ParsedChannel,
    ): Boolean {
        if (broadcaster.country != null && channel.country != null && broadcaster.country != channel.country) return false
        val joined = tokens.joinToString("")
        if (joined.length < 2) return false
        // "BBCOne" is "BBC One".
        if (channel.joined == joined) return true
        if (!channel.words.startsWith(tokens, 0)) return false
        val next = channel.words.getOrNull(tokens.size) ?: return true
        return next in tokens || next !in OTHER_NETWORK_WORDS
    }

    /**
     * Of [channels] (all [broadcaster]'s), the ones worth listing with no programme: a channel with the
     * broadcaster's exact name or that name and a number ("ESPN 2"), and its sport channels; all of
     * them when there are none of these.
     */
    fun likelyChannels(
        broadcaster: Broadcaster,
        channels: List<LiveChannel>,
        parsed: Map<LiveChannel, ParsedChannel>,
    ): List<LiveChannel> {
        val tokens = broadcasterTokens(broadcaster.name)
        val likely =
            channels.filter { channel ->
                val words = parsed[channel]?.words ?: return@filter false
                val numbered = words.size == tokens.size + 1 && words.startsWith(tokens, 0) && words.last().all(Char::isDigit)
                words == tokens || numbered || words.any { it in SPORT_WORDS }
            }
        return likely.ifEmpty { channels }
    }
}
