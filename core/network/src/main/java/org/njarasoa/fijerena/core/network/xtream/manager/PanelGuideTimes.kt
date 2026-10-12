package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.player.model.EpgResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * A panel's own guide (`get_simple_data_table`) that stamps its local wall time as UTC: bears gives
 * a 20:45 Amsterdam start as `start_timestamp` 20:45 UTC, two hours late, while its `xmltv.php`
 * says 18:45 UTC and the archive agrees. Detected per source by comparing with that `xmltv.php`,
 * corrected by the panel's own zone ([XtreamPanelClock]). See
 * docs/plans/20261012_panel-guide-local-time-plan.md.
 */
object PanelGuideTimes {
    /** Fewest exact matches for either reading to win. */
    const val MIN_MATCHES = 20

    /** How many times more matches the winning reading needs than the other. */
    const val DOMINANCE = 4

    /** A programme as both guides can name it: the XMLTV channel id, the title, the start. Build with [listing]. */
    data class Listing(
        val channelId: String,
        val title: String,
        val startSec: Long,
    )

    /** A [Listing] with the id and title in one case and the title trimmed, as the two guides differ there. */
    fun listing(
        channelId: String,
        title: String,
        startSec: Long,
    ): Listing = Listing(channelId.lowercase(Locale.ROOT), title.trim().lowercase(Locale.ROOT), startSec)

    /** [labelledSec], an epoch the panel computed from its wall time as if it were UTC, as the real epoch. */
    fun realEpoch(
        labelledSec: Long,
        zone: ZoneId,
    ): Long = LocalDateTime.ofEpochSecond(labelledSec, 0, ZoneOffset.UTC).atZone(zone).toEpochSecond()

    /** [epg] with every start and end read as the panel's wall time in [zone]. */
    fun corrected(
        epg: EpgResponse,
        zone: ZoneId,
    ): EpgResponse =
        EpgResponse(
            epg.listings.map { program ->
                program.copy(
                    start = program.start.toLongOrNull()?.let { realEpoch(it, zone).toString() } ?: program.start,
                    end = program.end.toLongOrNull()?.let { realEpoch(it, zone).toString() } ?: program.end,
                )
            },
        )

    /**
     * Whether the panel guide's [panel] listings label local time as UTC, judged against [index]
     * (the same panel's `xmltv.php`): true when they match the index once read as wall time in
     * [zone], false when they match it as they are, null when neither reading clearly wins (too few
     * matches, or a mix) — the caller then keeps what it knew. Exact matches only: both guides come
     * from the panel's one database. A listing where [zone] is at UTC can't tell the readings apart
     * and is skipped.
     */
    fun labelsLocalAsUtc(
        panel: List<Listing>,
        index: Set<Listing>,
        zone: ZoneId,
    ): Boolean? {
        var asIs = 0
        var asWallTime = 0
        for (listing in panel) {
            val wall = realEpoch(listing.startSec, zone)
            if (wall == listing.startSec) continue
            if (listing in index) asIs++
            if (listing.copy(startSec = wall) in index) asWallTime++
        }
        return when {
            asWallTime >= MIN_MATCHES && asWallTime >= DOMINANCE * asIs -> true
            asIs >= MIN_MATCHES && asIs >= DOMINANCE * asWallTime -> false
            else -> null
        }
    }
}
