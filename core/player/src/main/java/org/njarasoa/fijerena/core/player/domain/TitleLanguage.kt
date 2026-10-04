package org.njarasoa.fijerena.core.player.domain

/**
 * Provider metadata routinely bakes a language/region code into the title string itself —
 * `"EN - Breaking Bad"`, `"NP:Kantipur"`, `"Breaking Bad (US)"` — instead of sending it as
 * separate metadata. Parsed once here and consumed by every surface that renders
 * [MediaItem.name] (mobile's `StreamCard`, TV's `StreamList`, both platforms'
 * `RelatedTitlesRow`) so the title shown to the user is clean and the code becomes its own small
 * badge instead of visual noise baked into the title.
 */
data class ParsedTitle(
    val title: String,
    val badge: String?,
)

// Requires the code to be all-uppercase letters so ordinary title casing ("A-Team") never
// matches — real prefixes/suffixes from provider feeds are consistently shouted like "EN", "FR".
// D+/A+ (Disney+/Apple TV+) are the exception: accepted in either case, badged uppercase.
private const val CODE = "[A-Z]{2,4}|[DdAa]\\+"
private val PREFIX_CODE = Regex("^($CODE)\\s*[:\\-]\\s*")
private val SUFFIX_CODE = Regex("\\s*\\(($CODE)\\)\\s*$")

// The 4K catalogues stack a quality tag on the code: "4K-NF - …", "4K-D+ - …", "4K-FR-HDR - …",
// "4K-OSN+ - …" (a few in lower case, some with a space before the dash). The whole tag is the
// badge. Only before " - ", so a title that merely starts with "4K-" keeps it.
private val PREFIX_4K = Regex("^(4K(?:-[A-Z]{1,4}\\+?)+)\\s*-\\s+", RegexOption.IGNORE_CASE)

// Live channels carry it with a colon: "4K: BEIN SPORTS ᵁᴴᴰ".
private val PREFIX_4K_COLON = Regex("^(4K)\\s*:\\s*", RegexOption.IGNORE_CASE)

/**
 * Strips a leading `EN -`/`NP:`/`4K-NF -`/`4K:` style prefix or a trailing `(US)` style suffix off [raw] and
 * returns the cleaned title plus the code as a badge. When both are present, the prefix wins —
 * it is the more common shape and carries the language, the more useful of the two. Returns
 * [raw] verbatim (trimmed) with a null badge when neither pattern matches.
 */
fun parseDisplayTitle(raw: String): ParsedTitle {
    var text = raw
    var badge: String? = null

    (PREFIX_4K.find(text) ?: PREFIX_4K_COLON.find(text) ?: PREFIX_CODE.find(text))?.let { match ->
        badge = match.groupValues[1].uppercase()
        text = text.substring(match.range.last + 1)
    }
    SUFFIX_CODE.find(text)?.let { match ->
        if (badge == null) badge = match.groupValues[1].uppercase()
        text = text.removeRange(match.range)
    }

    return ParsedTitle(title = text.trim(), badge = badge)
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
