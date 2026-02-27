package org.njarasoa.fijerena.core.network.xmltv

/**
 * Shared channel name normalization utility.
 * Strips language prefixes, quality suffixes, country codes, unicode superscripts,
 * and non-alphanumeric characters to produce a comparable key.
 */
object ChannelNameNormalizer {

    private val LANGUAGE_PREFIX_REGEX = Regex("^[A-Za-z]{2,3}:\\s*")
    private val QUALITY_SUFFIX_REGEX = Regex("\\b(fhd|uhd|hd|sd|4k|720p|1080p|1080i|hevc|h\\.?265|h\\.?264|avc|vp9|av1|mpeg[24]|hdr10?)\\b", RegexOption.IGNORE_CASE)
    private val COUNTRY_CODE_REGEX = Regex("\\s*[\\[(][A-Za-z]{2,3}[])]")
    private val UNICODE_SUPERSCRIPT_REGEX = Regex("[\u1D00-\u1DBF\u2070-\u209F\u2460-\u24FF]+")
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]")

    fun normalize(name: String): String {
        return name
            .replace(LANGUAGE_PREFIX_REGEX, "")
            .replace(QUALITY_SUFFIX_REGEX, "")
            .replace(COUNTRY_CODE_REGEX, "")
            .replace(UNICODE_SUPERSCRIPT_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, "")
    }
}
