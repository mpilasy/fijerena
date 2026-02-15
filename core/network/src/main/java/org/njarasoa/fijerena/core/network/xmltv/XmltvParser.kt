package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgChannelEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgProgrammeEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmltvParser {

    private const val TAG = "XmltvParser"

    /**
     * Timezone offset override (hours) for XMLTV timestamps.
     * When non-zero, replaces the timezone offset from the XMLTV data.
     * Set from AppSettings.epgTimezoneOffsetHours at app startup.
     */
    @Volatile
    var timezoneOverrideHours: Int = 0

    /**
     * Parse XMLTV data with optional filtering to handle large files (500MB+).
     *
     * @param channelFilter Called after all channels are parsed (before programmes).
     *   Receives the channel map, returns the set of XMLTV channel IDs to keep.
     *   Programmes for other channels are skipped without allocating objects.
     * @param timeWindowSeconds If provided, programmes outside this (start, end) epoch
     *   range are discarded. Typically ±24h from now.
     */
    fun parse(
        inputStream: InputStream,
        channelFilter: ((Map<String, XmltvChannel>) -> Set<String>)? = null,
        timeWindowSeconds: Pair<Long, Long>? = null
    ): XmltvData {
        val channels = mutableMapOf<String, XmltvChannel>()
        val programmes = mutableMapOf<String, MutableList<XmltvProgramme>>()
        var wantedChannelIds: Set<String>? = null
        var filterResolved = channelFilter == null

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var skippedChannels = 0
        var skippedTime = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> {
                        val channel = parseChannel(parser)
                        if (channel != null) {
                            channels[channel.id] = channel
                        }
                    }
                    "programme" -> {
                        // Resolve channel filter on first programme element
                        // (XMLTV DTD guarantees all <channel> come before <programme>)
                        if (!filterResolved) {
                            wantedChannelIds = channelFilter!!.invoke(channels)
                            filterResolved = true
                            Log.d(TAG, "Filter: keeping ${wantedChannelIds.size} of ${channels.size} channels")
                        }

                        // Read attributes from current START_TAG (does not advance parser)
                        val channelId = parser.getAttributeValue(null, "channel")

                        // Skip unwanted channels entirely (no object allocation)
                        if (wantedChannelIds != null && channelId != null && channelId !in wantedChannelIds) {
                            skipElement(parser)
                            skippedChannels++
                        } else {
                            // Time window pre-check on attributes before full child parse
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            val outsideWindow = if (timeWindowSeconds != null && startStr != null && stopStr != null) {
                                val startEpoch = parseTimestamp(startStr)
                                val endEpoch = parseTimestamp(stopStr)
                                endEpoch < timeWindowSeconds.first || startEpoch > timeWindowSeconds.second
                            } else false

                            if (outsideWindow) {
                                skipElement(parser)
                                skippedTime++
                            } else {
                                val programme = parseProgramme(parser)
                                if (programme != null) {
                                    programmes.getOrPut(programme.channelId) { mutableListOf() }
                                        .add(programme)
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val kept = programmes.values.sumOf { it.size }
        Log.d(TAG, "Parse complete: kept $kept programmes, skipped $skippedChannels (channel) + $skippedTime (time)")

        return XmltvData(channels = channels, programmes = programmes)
    }

    /** Skip the current element and all its children without allocating strings. */
    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
    }

    private fun parseChannel(parser: XmlPullParser): XmltvChannel? {
        val id = parser.getAttributeValue(null, "id") ?: return null
        var displayName: String? = null
        var iconUrl: String? = null

        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "display-name" -> {
                            if (displayName == null) {
                                displayName = parser.nextText()
                                depth-- // nextText() consumes the end tag
                            }
                        }
                        "icon" -> {
                            if (iconUrl == null) {
                                iconUrl = parser.getAttributeValue(null, "src")
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        return XmltvChannel(
            id = id,
            displayName = displayName ?: id,
            iconUrl = iconUrl
        )
    }

    /** Max characters to keep from any single text field (title, desc, category). */
    private const val MAX_TEXT_LENGTH = 2000

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            val text = parser.nextText()
            if (text.length > MAX_TEXT_LENGTH) text.substring(0, MAX_TEXT_LENGTH) else text
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read text element", e)
            ""
        }
    }

    private fun parseProgramme(parser: XmlPullParser): XmltvProgramme? {
        val startStr = parser.getAttributeValue(null, "start") ?: return null
        val stopStr = parser.getAttributeValue(null, "stop") ?: return null
        val channelId = parser.getAttributeValue(null, "channel") ?: return null

        val startEpoch = parseTimestamp(startStr)
        val endEpoch = parseTimestamp(stopStr)
        if (startEpoch == 0L || endEpoch == 0L) return null

        var title: String? = null
        var description: String? = null
        var category: String? = null

        var depth = 1
        while (depth > 0) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "title" -> {
                            if (title == null) {
                                title = safeNextText(parser)
                                depth--
                            }
                        }
                        "desc" -> {
                            if (description == null) {
                                description = safeNextText(parser)
                                depth--
                            }
                        }
                        "category" -> {
                            if (category == null) {
                                category = safeNextText(parser)
                                depth--
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (title == null) return null

        return XmltvProgramme(
            channelId = channelId,
            startEpoch = startEpoch,
            endEpoch = endEpoch,
            title = title,
            description = description,
            category = category
        )
    }

    /**
     * Search programmes by title in a streaming fashion.
     * Scans the XMLTV file once, collecting programmes whose title contains [query]
     * (case-insensitive). Stops early once [maxResults] programmes are found.
     *
     * @param query Case-insensitive substring to match against programme titles
     * @param maxResults Maximum number of matching programmes before early termination
     * @param timeWindowSeconds Optional (start, end) epoch range to filter programmes
     * @return [XmltvSearchResult] with matched programmes and only channels that have matches
     */
    fun searchByTitle(
        inputStream: InputStream,
        query: String,
        maxResults: Int = 500,
        timeWindowSeconds: Pair<Long, Long>? = null
    ): XmltvSearchResult {
        val allChannels = mutableMapOf<String, XmltvChannel>()
        val matchedProgrammes = mutableListOf<XmltvProgramme>()
        val matchedChannelIds = mutableSetOf<String>()
        var totalScanned = 0
        var truncated = false
        val queryLower = query.lowercase(Locale.ROOT)

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val channel = parseChannel(parser)
                            if (channel != null) {
                                allChannels[channel.id] = channel
                            }
                        }
                        "programme" -> {
                            // Time window pre-check on attributes
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            val outsideWindow = if (timeWindowSeconds != null && startStr != null && stopStr != null) {
                                val startEpoch = parseTimestamp(startStr)
                                val endEpoch = parseTimestamp(stopStr)
                                endEpoch < timeWindowSeconds.first || startEpoch > timeWindowSeconds.second
                            } else false

                            if (outsideWindow) {
                                skipElement(parser)
                            } else {
                                val programme = parseProgramme(parser)
                                if (programme != null) {
                                    totalScanned++
                                    if (programme.title.lowercase(Locale.ROOT).contains(queryLower)) {
                                        matchedProgrammes.add(programme)
                                        matchedChannelIds.add(programme.channelId)
                                        if (matchedProgrammes.size >= maxResults) {
                                            truncated = true
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during search, returning partial results (${matchedProgrammes.size} found)", e)
            truncated = true
        } catch (e: Exception) {
            Log.e(TAG, "Parser error during search, returning partial results (${matchedProgrammes.size} found)", e)
            truncated = true
        }

        // Return only channels that have matching programmes
        val resultChannels = allChannels.filterKeys { it in matchedChannelIds }

        Log.d(TAG, "Search '$query': ${matchedProgrammes.size} matches from $totalScanned scanned, truncated=$truncated")
        return XmltvSearchResult(
            channels = resultChannels,
            programmes = matchedProgrammes,
            totalScanned = totalScanned,
            truncated = truncated
        )
    }

    /**
     * Parse a <channel> element and return a Room entity for indexing.
     * Reuses the same parsing logic as [parseChannel].
     */
    fun parseChannelForIndex(parser: XmlPullParser): EpgChannelEntity? {
        val channel = parseChannel(parser) ?: return null
        return EpgChannelEntity(
            xmltvId = channel.id,
            displayName = channel.displayName,
            iconUrl = channel.iconUrl
        )
    }

    /**
     * Parse a <programme> element and return a Room entity for indexing.
     * Reuses the same parsing logic as [parseProgramme].
     */
    fun parseProgrammeForIndex(parser: XmlPullParser, sourceId: Long = 0): EpgProgrammeEntity? {
        val programme = parseProgramme(parser) ?: return null
        return EpgProgrammeEntity(
            channelId = programme.channelId,
            title = programme.title,
            titleLowercase = programme.title.lowercase(Locale.ROOT),
            description = programme.description,
            category = programme.category,
            startEpoch = programme.startEpoch,
            endEpoch = programme.endEpoch,
            sourceId = sourceId
        )
    }

    fun parseTimestamp(ts: String): Long {
        return try {
            // XMLTV format: "20260206180000 +0000" or "20260206180000"
            val trimmed = ts.trim()

            // Split into datetime part and optional timezone
            val spaceIndex = trimmed.indexOf(' ')
            val datePart: String
            if (spaceIndex > 0) {
                datePart = trimmed.substring(0, spaceIndex)
            } else {
                datePart = trimmed
            }

            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

            // If user has configured a timezone override, use it instead of
            // the XMLTV-provided offset. This fixes sources that encode local
            // times (e.g. UTC+8) but mislabel them as UTC (+0000).
            val overrideHours = timezoneOverrideHours
            if (overrideHours != 0) {
                val sign = if (overrideHours >= 0) "+" else "-"
                val absHours = kotlin.math.abs(overrideHours)
                format.timeZone = TimeZone.getTimeZone("GMT${sign}${"%02d".format(absHours)}00")
            } else {
                val tzPart = if (spaceIndex > 0) trimmed.substring(spaceIndex + 1).trim() else null
                if (tzPart != null) {
                    val normalizedTz = tzPart.replace(":", "")
                    format.timeZone = TimeZone.getTimeZone("GMT$normalizedTz")
                } else {
                    format.timeZone = TimeZone.getTimeZone("UTC")
                }
            }

            val date = format.parse(datePart)
            (date?.time ?: 0L) / 1000L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse XMLTV timestamp: $ts", e)
            0L
        }
    }
}
