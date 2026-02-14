package org.njarasoa.fijerena.core.network.xmltv

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Merges multiple XMLTV files into a single output file.
 *
 * Uses streaming XmlPullParser for reading and BufferedWriter for output,
 * keeping memory usage bounded regardless of file sizes.
 * Deduplicates <channel> elements by ID across files.
 */
object XmltvMerger {

    private const val TAG = "XmltvMerger"
    private const val BUFFER_SIZE = 65536

    /**
     * Merge multiple XMLTV files into a single output file.
     *
     * @param inputFiles List of XMLTV files to merge
     * @param outputFile The merged output file (will be overwritten)
     * @return true if merge succeeded, false on failure
     */
    fun merge(inputFiles: List<File>, outputFile: File): Boolean {
        if (inputFiles.isEmpty()) return false

        val validFiles = inputFiles.filter { it.exists() && it.length() > 0 }
        if (validFiles.isEmpty()) {
            Log.w(TAG, "No valid input files to merge")
            return false
        }

        // Single file — just copy, no merge needed
        if (validFiles.size == 1) {
            return try {
                validFiles.first().copyTo(outputFile, overwrite = true)
                Log.d(TAG, "Single file, copied directly: ${outputFile.length() / 1024}KB")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy single file", e)
                false
            }
        }

        val tmpFile = File(outputFile.parent, "${outputFile.name}.merge_tmp")

        try {
            val seenChannelIds = mutableSetOf<String>()
            var totalChannels = 0
            var totalProgrammes = 0

            BufferedWriter(FileWriter(tmpFile), BUFFER_SIZE).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<tv generator-name=\"fijerena-merger\">\n")

                for (file in validFiles) {
                    try {
                        val (channels, programmes) = parseAndWrite(file, writer, seenChannelIds)
                        totalChannels += channels
                        totalProgrammes += programmes
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing ${file.name}, skipping: ${e.message}")
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "OOM processing ${file.name}, skipping", e)
                        System.gc()
                    }
                }

                writer.write("</tv>\n")
                writer.flush()
            }

            // Atomic rename
            outputFile.delete()
            tmpFile.renameTo(outputFile)

            Log.d(TAG, "Merged ${validFiles.size} files: $totalChannels channels, " +
                "$totalProgrammes programmes, ${outputFile.length() / 1024}KB")
            return true

        } catch (e: Exception) {
            tmpFile.delete()
            Log.e(TAG, "Merge failed", e)
            return false
        } catch (e: OutOfMemoryError) {
            tmpFile.delete()
            Log.e(TAG, "OOM during merge", e)
            System.gc()
            return false
        }
    }

    /**
     * Parse a single XMLTV file and write its channels/programmes to the writer.
     * Returns (channelsWritten, programmesWritten).
     */
    private fun parseAndWrite(
        file: File,
        writer: BufferedWriter,
        seenChannelIds: MutableSet<String>
    ): Pair<Int, Int> {
        var channelsWritten = 0
        var programmesWritten = 0

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()

        file.inputStream().buffered(BUFFER_SIZE).use { stream ->
            parser.setInput(stream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val xml = readElementAsString(parser, "channel")
                            if (id != null && seenChannelIds.add(id)) {
                                writer.write("  ")
                                writer.write(xml)
                                writer.write("\n")
                                channelsWritten++
                            }
                        }
                        "programme" -> {
                            val xml = readElementAsString(parser, "programme")
                            writer.write("  ")
                            writer.write(xml)
                            writer.write("\n")
                            programmesWritten++
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        return channelsWritten to programmesWritten
    }

    /**
     * Read a complete XML element (including children) as a string.
     * The parser must be positioned at the START_TAG of the element.
     * After this call, the parser will be positioned after the END_TAG.
     */
    private fun readElementAsString(parser: XmlPullParser, elementName: String): String {
        val sb = StringBuilder()
        sb.append("<").append(elementName)

        // Write attributes
        for (i in 0 until parser.attributeCount) {
            sb.append(" ")
            sb.append(parser.getAttributeName(i))
            sb.append("=\"")
            sb.append(escapeXml(parser.getAttributeValue(i)))
            sb.append("\"")
        }

        var depth = 1
        val event = parser.next()

        if (event == XmlPullParser.END_TAG && depth == 1) {
            sb.append("/>")
            return sb.toString()
        }

        sb.append(">")

        var currentEvent = event
        while (depth > 0) {
            when (currentEvent) {
                XmlPullParser.START_TAG -> {
                    depth++
                    sb.append("<").append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        sb.append(" ")
                        sb.append(parser.getAttributeName(i))
                        sb.append("=\"")
                        sb.append(escapeXml(parser.getAttributeValue(i)))
                        sb.append("\"")
                    }
                    sb.append(">")
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth > 0) {
                        sb.append("</").append(parser.name).append(">")
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (text != null) {
                        sb.append(escapeXml(text))
                    }
                }
            }
            if (depth > 0) {
                currentEvent = parser.next()
            }
        }

        sb.append("</").append(elementName).append(">")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
