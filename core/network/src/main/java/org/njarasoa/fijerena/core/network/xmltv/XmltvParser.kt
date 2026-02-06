package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmltvParser {

    private const val TAG = "XmltvParser"

    fun parse(inputStream: InputStream): XmltvData {
        val channels = mutableMapOf<String, XmltvChannel>()
        val programmes = mutableMapOf<String, MutableList<XmltvProgramme>>()

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
                            channels[channel.id] = channel
                        }
                    }
                    "programme" -> {
                        val programme = parseProgramme(parser)
                        if (programme != null) {
                            programmes.getOrPut(programme.channelId) { mutableListOf() }
                                .add(programme)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return XmltvData(channels = channels, programmes = programmes)
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
                                title = parser.nextText()
                                depth--
                            }
                        }
                        "desc" -> {
                            if (description == null) {
                                description = parser.nextText()
                                depth--
                            }
                        }
                        "category" -> {
                            if (category == null) {
                                category = parser.nextText()
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

    fun parseTimestamp(ts: String): Long {
        return try {
            // XMLTV format: "20260206180000 +0000" or "20260206180000"
            val trimmed = ts.trim()

            // Split into datetime part and optional timezone
            val spaceIndex = trimmed.indexOf(' ')
            val datePart: String
            val tzPart: String?
            if (spaceIndex > 0) {
                datePart = trimmed.substring(0, spaceIndex)
                tzPart = trimmed.substring(spaceIndex + 1).trim()
            } else {
                datePart = trimmed
                tzPart = null
            }

            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            if (tzPart != null) {
                // Normalize timezone: "+0000" or "+00:00" → standard offset
                val normalizedTz = tzPart.replace(":", "")
                format.timeZone = TimeZone.getTimeZone("GMT$normalizedTz")
            } else {
                format.timeZone = TimeZone.getTimeZone("UTC")
            }

            val date = format.parse(datePart)
            (date?.time ?: 0L) / 1000L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse XMLTV timestamp: $ts", e)
            0L
        }
    }
}
