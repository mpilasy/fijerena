package org.njarasoa.fijerena.core.network.sports

import java.util.Locale

/**
 * The countries of a profile's live channels, for UEFA's broadcasters (the sports teams plan →
 * SP-D13): one UEFA call per country and week, so only these countries are asked.
 */
internal object ChannelCountries {
    /** The countries asked at most, so the calls stay few. */
    const val MAX_COUNTRIES = 30

    // Two-letter tags that IPTV lists use for an Indian language more often than for the country.
    private val LANGUAGE_TAGS = setOf("ML", "BN", "KN", "MR", "PA", "GU")

    // Country names that are also a common word or a US state, so they say nothing in a category.
    private val AMBIGUOUS_NAMES = setOf("georgia", "jordan", "chad", "jersey", "island", "man")

    // Names that the locale data does not give.
    private val EXTRA_NAMES =
        mapOf(
            "uk" to "GB",
            "england" to "GB",
            "scotland" to "GB",
            "wales" to "GB",
            "usa" to "US",
        )

    // The languages whose country names a category can be written in.
    private val NAME_LANGUAGES = listOf("en", "fr", "de", "es", "pt", "it", "nl")

    private val ISO = Locale.getISOCountries().toSet()

    private const val MAX_NAME_WORDS = 4

    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF

    /** Each country's name, folded ("united kingdom", "deutschland", "brasil"), to its code. */
    private val NAMES: Map<String, String> by lazy {
        val names = HashMap<String, String>()
        for (code in Locale.getISOCountries()) {
            val country = Locale.Builder().setRegion(code).build()
            for (language in NAME_LANGUAGES) {
                val name = SportsText.fold(country.getDisplayCountry(Locale.forLanguageTag(language)))
                if (name.isNotEmpty() && name !in AMBIGUOUS_NAMES) names.putIfAbsent(name, code)
            }
        }
        names + EXTRA_NAMES
    }

    /**
     * The countries (ISO 3166-1 alpha-2, upper case) of [channelNames] and [categoryNames], most
     * frequent first, [MAX_COUNTRIES] at most. A name gives its country by its tag ("FR: TF1",
     * "UK| Sky", "US - ESPN", "DE ▎ Sky", "BBC One (UK)") or its flag ("🇧🇷 SporTV"); a category
     * also by a country name ("France", "Deutschland"). A language or region tag ("EN", "AR",
     * "AFR") gives none: "AR" in a list is Arabic, not Argentina.
     */
    fun channelCountries(
        channelNames: List<String>,
        categoryNames: List<String>,
    ): Set<String> {
        val counts = HashMap<String, Int>()
        for (name in channelNames) countryOf(name, byName = false)?.let { counts.merge(it, 1, Int::plus) }
        for (name in categoryNames) countryOf(name, byName = true)?.let { counts.merge(it, 1, Int::plus) }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_COUNTRIES)
            .map { it.key }
            .toCollection(LinkedHashSet())
    }

    private fun countryOf(
        name: String,
        byName: Boolean,
    ): String? {
        val country = flagCountry(name) ?: BroadcasterChannels.parse(name).country ?: if (byName) namedCountry(name) else null
        return country?.takeIf { it !in LANGUAGE_TAGS }
    }

    // The country of the first flag in [text]: two regional indicator letters.
    private fun flagCountry(text: String): String? {
        val points = text.codePoints().toArray()
        val at = (0 until points.size - 1).firstOrNull { points[it] in REGIONAL_A..REGIONAL_Z && points[it + 1] in REGIONAL_A..REGIONAL_Z }
        val code = at?.let { "${'A' + (points[it] - REGIONAL_A)}${'A' + (points[it + 1] - REGIONAL_A)}" }
        return code?.takeIf { it in ISO }
    }

    // The country whose name is in [text] as whole words; the longest name wins.
    private fun namedCountry(text: String): String? {
        val words = SportsText.fold(text).split(' ').filter { it.isNotEmpty() }
        return (minOf(MAX_NAME_WORDS, words.size) downTo 1)
            .asSequence()
            .flatMap { size -> words.windowed(size).asSequence().map { it.joinToString(" ") } }
            .firstNotNullOfOrNull { NAMES[it] }
    }
}
