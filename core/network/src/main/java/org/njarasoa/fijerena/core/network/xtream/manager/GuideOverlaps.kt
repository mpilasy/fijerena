package org.njarasoa.fijerena.core.network.xtream.manager

import org.njarasoa.fijerena.core.player.model.EpgResponse

/**
 * [EpgResponse] with each programme that runs past the next start ending there — the rule the guide
 * index applies at ingest ([org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDao.clipOverlaps]),
 * for the panel's own guide, which lists two schedules for one evening too. Programmes sharing a
 * start both end at the next later one. See the guide overlaps plan.
 */
fun EpgResponse.withOverlapsClipped(): EpgResponse {
    val starts = listings.mapNotNull { it.start.toLongOrNull() }.distinct().sorted()
    if (starts.size < 2) return this
    return EpgResponse(
        listings.map { program ->
            val start = program.start.toLongOrNull() ?: return@map program
            val end = program.end.toLongOrNull() ?: return@map program
            val i = starts.binarySearch(start)
            val next = starts.getOrNull(if (i >= 0) i + 1 else -i - 1)
            if (next != null && end > next) program.copy(end = next.toString()) else program
        },
    )
}
