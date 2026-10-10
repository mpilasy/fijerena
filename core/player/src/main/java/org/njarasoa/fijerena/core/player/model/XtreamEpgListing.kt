package org.njarasoa.fijerena.core.player.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * A panel's own guide answer (`get_simple_data_table`, `get_short_epg`) as it comes: `start` / `end`
 * are text ("2026-10-10 15:15:00", UTC on the panels seen so far), the epoch is in
 * `start_timestamp` / `stop_timestamp`, and title and description are base64. [toEpgResponse]
 * turns it into the app's [EpgResponse] (epoch seconds, plain text). See
 * docs/plans/archive/20261010_catchup-plan.md → "Facts measured".
 */
@Serializable
internal data class XtreamEpgAnswer(
    @SerialName("epg_listings") val listings: List<XtreamEpgListing> = emptyList(),
)

@Serializable
internal data class XtreamEpgListing(
    @SerialName("id") val id: String,
    @SerialName("epg_id") val epgId: String? = null,
    @SerialName("title") val title: String = "",
    @SerialName("lang") val language: String? = null,
    @SerialName("start") val start: String = "",
    @SerialName("end") val end: String = "",
    @SerialName("description") val description: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("start_timestamp") val startTimestamp: String? = null,
    @SerialName("stop_timestamp") val stopTimestamp: String? = null,
    @SerialName("has_archive") val hasArchive: Int? = null,
)

internal fun XtreamEpgAnswer.toEpgResponse(): EpgResponse =
    EpgResponse(
        listings =
            listings.map {
                EpgProgram(
                    id = it.id,
                    epgId = it.epgId,
                    title = xtreamText(it.title),
                    language = it.language,
                    start = epochText(it.startTimestamp, it.start),
                    end = epochText(it.stopTimestamp, it.end),
                    description = it.description?.let(::xtreamText),
                    channelId = it.channelId,
                    hasArchive = it.hasArchive,
                )
            },
    )

private val panelDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** The epoch seconds as text: the timestamp when the panel sent one, else [dateTime] read as UTC. */
private fun epochText(
    timestamp: String?,
    dateTime: String,
): String =
    timestamp?.toLongOrNull()?.toString()
        ?: dateTime.toLongOrNull()?.toString()
        ?: try {
            LocalDateTime.parse(dateTime, panelDateTime).toEpochSecond(ZoneOffset.UTC).toString()
        } catch (e: Exception) {
            // cancellation-ok: non-suspend
            "0"
        }

/** [raw] base64-decoded when it is base64 of UTF-8 text (how panels send it), else as it is. */
internal fun xtreamText(raw: String): String {
    val decoded =
        try {
            val bytes = Base64.getDecoder().decode(raw.trim())
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: IllegalArgumentException) {
            // cancellation-ok: non-suspend
            null
        } catch (e: CharacterCodingException) {
            // cancellation-ok: non-suspend
            null
        }
    return decoded?.takeIf { raw.isNotBlank() && it.none { c -> c.isISOControl() && c != '\n' && c != '\r' && c != '\t' } } ?: raw
}
