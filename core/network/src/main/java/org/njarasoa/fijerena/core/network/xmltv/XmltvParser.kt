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
     * Default timezone offset override (hours) for the parse functions: when not 0, it replaces the
     * offset in the XMLTV data. Nothing sets it; the guide index passes each source's own offset.
     */
    @Volatile
    var timezoneOverrideHours: Int = 0

    /**
     * Parses XMLTV data, with optional filters for large files (500 MB+).
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
        timeWindowSeconds: Pair<Long, Long>? = null,
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
                            val outsideWindow =
                                if (timeWindowSeconds != null && startStr != null && stopStr != null) {
                                    val startEpoch = parseTimestamp(startStr)
                                    val endEpoch = parseTimestamp(stopStr)
                                    endEpoch < timeWindowSeconds.first || startEpoch > timeWindowSeconds.second
                                } else {
                                    false
                                }

                            if (outsideWindow) {
                                skipElement(parser)
                                skippedTime++
                            } else {
                                val programme = parseProgramme(parser)
                                if (programme != null) {
                                    programmes
                                        .getOrPut(programme.channelId) { mutableListOf() }
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

                XmlPullParser.END_TAG -> {
                    depth--
                }

                XmlPullParser.END_DOCUMENT -> {
                    break
                }
            }
        }

        return XmltvChannel(
            id = id,
            displayName = displayName ?: id,
            iconUrl = iconUrl,
        )
    }

    /** Max characters to keep from any single text field (title, desc, category). */
    private const val MAX_TEXT_LENGTH = 2000

    private fun safeNextText(parser: XmlPullParser): String =
        try {
            val text = parser.nextText()
            if (text.length > MAX_TEXT_LENGTH) text.substring(0, MAX_TEXT_LENGTH) else text
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read text element", e)
            ""
        }

    private fun parseProgramme(
        parser: XmlPullParser,
        timezoneOverrideHours: Int = this.timezoneOverrideHours,
    ): XmltvProgramme? {
        val startStr = parser.getAttributeValue(null, "start") ?: return null
        val stopStr = parser.getAttributeValue(null, "stop") ?: return null
        val channelId = parser.getAttributeValue(null, "channel") ?: return null

        val startEpoch = parseTimestamp(startStr, timezoneOverrideHours)
        val endEpoch = parseTimestamp(stopStr, timezoneOverrideHours)
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

                XmlPullParser.END_TAG -> {
                    depth--
                }

                XmlPullParser.END_DOCUMENT -> {
                    break
                }
            }
        }

        if (title == null) return null

        return XmltvProgramme(
            channelId = channelId,
            startEpoch = startEpoch,
            endEpoch = endEpoch,
            title = title,
            description = description,
            category = category,
        )
    }

    /**
     * Parse a <channel> element and return a Room entity for indexing.
     * Reuses the same parsing logic as [parseChannel].
     */
    fun parseChannelForIndex(
        parser: XmlPullParser,
        sourceId: Long = 0,
    ): EpgChannelEntity? {
        val channel = parseChannel(parser) ?: return null
        return EpgChannelEntity(
            xmltvId = channel.id,
            displayName = channel.displayName,
            iconUrl = channel.iconUrl,
            sourceId = sourceId,
        )
    }

    /**
     * Parse a <programme> element and return a Room entity for indexing.
     * Reuses the same parsing logic as [parseProgramme].
     */
    fun parseProgrammeForIndex(
        parser: XmlPullParser,
        sourceId: Long = 0,
        timezoneOverrideHours: Int = this.timezoneOverrideHours,
    ): EpgProgrammeEntity? {
        val programme = parseProgramme(parser, timezoneOverrideHours) ?: return null
        return EpgProgrammeEntity(
            channelId = programme.channelId,
            title = programme.title,
            titleLowercase = programme.title.lowercase(Locale.ROOT),
            description = programme.description,
            category = programme.category,
            startEpoch = programme.startEpoch,
            endEpoch = programme.endEpoch,
            sourceId = sourceId,
        )
    }

    /**
     * Cached SimpleDateFormat and TimeZone objects, for less GC: a large XMLTV file has 200,000+
     * programmes with 2 times each.
     */
    private val dateFormatThreadLocal =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        }

    private val timeZoneCache = java.util.concurrent.ConcurrentHashMap<String, TimeZone>()

    /**
     * The UTC offset, in minutes, of an XMLTV time's zone part ("+0800", "-05:30"): 0 for a time
     * with no zone part (read as UTC), null for a zone part it cannot read.
     */
    fun zoneMinutes(zone: String): Int? {
        if (zone.isEmpty()) return 0
        val match = zoneRegex.matchEntire(zone) ?: return null
        val (sign, hours, minutes) = match.destructured
        val total = hours.toInt() * 60 + minutes.toInt()
        return if (sign == "-") -total else total
    }

    private val zoneRegex = Regex("""([+-])(\d{2}):?(\d{2})""")

    /**
     * An XMLTV time split into its digits, padded to 14, and its zone part ("" when it has none).
     * XMLTV lets a feed leave out digits from the right ("202611111800" has no seconds), and some
     * feeds put no space before the zone ("20261111180000+0300"). Null without a full date (fewer
     * than 8 digits) or with more than 14 digits.
     */
    fun splitTimestamp(ts: String): Pair<String, String>? {
        val trimmed = ts.trim()
        val digits = trimmed.takeWhile { it.isDigit() }
        return if (digits.length in 8..14) digits.padEnd(14, '0') to trimmed.substring(digits.length).trim() else null
    }

    fun parseTimestamp(
        ts: String,
        timezoneOverrideHours: Int = this.timezoneOverrideHours,
    ): Long =
        try {
            val (datePart, zone) = requireNotNull(splitTimestamp(ts)) { "Not an XMLTV time" }
            val tzPart = zone.ifEmpty { null }

            val format = dateFormatThreadLocal.get()!!

            // A timezone override replaces the XMLTV offset, for sources that label local times
            // (UTC+8) as UTC (+0000).
            val overrideHours = timezoneOverrideHours
            val tzKey = if (overrideHours != 0) "OVERRIDE_$overrideHours" else tzPart ?: "UTC"

            val timeZone =
                timeZoneCache.getOrPut(tzKey) {
                    if (overrideHours != 0) {
                        val sign = if (overrideHours >= 0) "+" else "-"
                        val absHours = kotlin.math.abs(overrideHours)
                        TimeZone.getTimeZone("GMT${sign}${"%02d".format(absHours)}00")
                    } else if (tzPart != null) {
                        val normalizedTz = tzPart.replace(":", "")
                        TimeZone.getTimeZone("GMT$normalizedTz")
                    } else {
                        TimeZone.getTimeZone("UTC")
                    }
                }
            format.timeZone = timeZone

            val date = format.parse(datePart)
            (date?.time ?: 0L) / 1000L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse XMLTV timestamp: $ts", e)
            0L
        }
}
