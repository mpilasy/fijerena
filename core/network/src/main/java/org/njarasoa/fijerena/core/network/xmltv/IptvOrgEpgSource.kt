package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log

/**
 * Utility for working with iptv-org EPG sources.
 *
 * iptv-org provides free XMLTV EPG data at well-known URLs:
 * - Per-site guides: https://iptv-org.github.io/epg/guides/{lang}/{site}.xml
 * - Combined guides: https://iptv-org.github.io/epg/guides/{lang}.xml
 *
 * The XMLTV format is identical to standard XMLTV, so the existing [XmltvParser]
 * handles parsing. This class provides URL detection, validation, and helper
 * methods for the iptv-org ecosystem.
 */
object IptvOrgEpgSource {

    private const val TAG = "IptvOrgEpgSource"
    private const val IPTV_ORG_HOST = "iptv-org.github.io"
    private const val IPTV_ORG_EPG_BASE = "https://iptv-org.github.io/epg"

    /**
     * Check whether a URL points to an iptv-org EPG source.
     */
    fun isIptvOrgUrl(url: String): Boolean {
        return try {
            val normalized = url.trim().lowercase()
            normalized.contains(IPTV_ORG_HOST) && normalized.contains("/epg/")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Build an iptv-org guide URL for a specific language and site.
     *
     * @param language ISO 639-1 language code (e.g., "en", "fr", "zh")
     * @param site Site identifier (e.g., "directv.us", "sky.com")
     * @return Full URL to the XMLTV guide
     */
    fun buildGuideUrl(language: String, site: String): String {
        return "$IPTV_ORG_EPG_BASE/guides/${language.lowercase()}/${site.lowercase()}.xml"
    }

    /**
     * Build an iptv-org combined guide URL for a specific language.
     *
     * @param language ISO 639-1 language code
     * @return Full URL to the combined XMLTV guide for that language
     */
    fun buildCombinedGuideUrl(language: String): String {
        return "$IPTV_ORG_EPG_BASE/guides/${language.lowercase()}.xml"
    }

    /**
     * Extract the language code from an iptv-org URL, if present.
     * Returns null if the URL is not a recognized iptv-org format.
     */
    fun extractLanguage(url: String): String? {
        return try {
            val path = url.substringAfter("/guides/", "")
            if (path.isEmpty()) return null
            val firstSegment = path.substringBefore("/").substringBefore(".")
            if (firstSegment.length in 2..3) firstSegment else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract language from URL: $url", e)
            null
        }
    }

    /**
     * Describes the ingestion strategy for iptv-org sources.
     *
     * iptv-org guides are regenerated daily and contain 1-2 days of future data.
     * The "Clear and Load" strategy is optimal:
     * 1. Delete programmes older than 24 hours (they won't appear in the next refresh)
     * 2. Insert new data with REPLACE to upsert overlapping programmes
     * 3. This keeps the database bounded on storage-constrained devices (Shield)
     */
    data class IngestionConfig(
        val staleCutoffHours: Int = 24,
        val refreshIntervalHours: Int = 12,
        val clearBeforeLoad: Boolean = true
    ) {
        val staleCutoffEpochSeconds: Long
            get() = (System.currentTimeMillis() / 1000L) - (staleCutoffHours * 3600L)
    }

    /**
     * Get the recommended ingestion config for a URL.
     * iptv-org sources use aggressive cleanup; generic XMLTV sources use standard config.
     */
    fun getIngestionConfig(url: String): IngestionConfig {
        return if (isIptvOrgUrl(url)) {
            IngestionConfig(
                staleCutoffHours = 24,
                refreshIntervalHours = 12,
                clearBeforeLoad = true
            )
        } else {
            IngestionConfig(
                staleCutoffHours = 48,
                refreshIntervalHours = 24,
                clearBeforeLoad = false
            )
        }
    }
}
