package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context

/**
 * Searches the locally-cached XMLTV file by programme title.
 * All I/O is local — no network calls.
 */
class XmltvSearchService(private val context: Context) {

    /**
     * Search programme titles in the local EPG file.
     *
     * @param query Case-insensitive substring to match
     * @return [XmltvSearchResult] or null if no local EPG file is available
     */
    fun search(query: String): XmltvSearchResult? {
        val file = EpgFileManager.getInstance(context).getEpgFile()
            ?: fallbackFile()
            ?: return null
        val now = System.currentTimeMillis() / 1000L
        val pastOneDay = now - 86400L
        val futureSixDays = now + 6 * 86400L
        val timeWindow = Pair(pastOneDay, futureSixDays)

        return file.inputStream().buffered().use { stream ->
            XmltvParser.searchByTitle(
                inputStream = stream,
                query = query,
                timeWindowSeconds = timeWindow
            )
        }
    }

    private fun fallbackFile(): java.io.File? {
        val f = java.io.File(context.cacheDir, "xmltv_global.xml")
        return if (f.exists() && f.length() > 0) f else null
    }
}
