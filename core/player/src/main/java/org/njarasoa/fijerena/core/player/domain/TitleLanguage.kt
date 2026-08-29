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
private val PREFIX_CODE = Regex("^([A-Z]{2,4})\\s*[:\\-]\\s*")
private val SUFFIX_CODE = Regex("\\s*\\(([A-Z]{2,4})\\)\\s*$")

/**
 * Strips a leading `EN -`/`NP:` style prefix or a trailing `(US)` style suffix off [raw] and
 * returns the cleaned title plus the code as a badge. When both are present, the prefix wins —
 * it is the more common shape and carries the language, the more useful of the two. Returns
 * [raw] verbatim (trimmed) with a null badge when neither pattern matches.
 */
fun parseDisplayTitle(raw: String): ParsedTitle {
    var text = raw
    var badge: String? = null

    PREFIX_CODE.find(text)?.let { match ->
        badge = match.groupValues[1]
        text = text.substring(match.range.last + 1)
    }
    SUFFIX_CODE.find(text)?.let { match ->
        if (badge == null) badge = match.groupValues[1]
        text = text.removeRange(match.range)
    }

    return ParsedTitle(title = text.trim(), badge = badge)
}
