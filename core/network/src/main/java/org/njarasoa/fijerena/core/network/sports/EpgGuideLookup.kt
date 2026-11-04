package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.suspendRunCatching
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDao
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgSearchResultRow

/**
 * [GuideLookup] on the guide index (`epg_index.db`), for the guide sources of [scope]. Both reads
 * use the existing bounded queries (`end > from AND start <= to`), then keep the programmes that
 * start in the window. A stale FTS index falls back to the title `LIKE` scan, one term at a time,
 * as Search the guide does. A failed or timed-out read returns nothing: the game then shows its
 * broadcasters' channels without a programme.
 */
class EpgGuideLookup(
    private val dao: EpgIndexDao,
    private val scope: GuideScope,
    private val isIndexed: () -> Boolean = { true },
    private val isFtsStale: () -> Boolean = { false },
) : GuideLookup {
    companion object {
        private const val TAG = "EpgGuideLookup"
        private const val TIMEOUT_MS = 10_000L

        /** The lookup of the profile in use. */
        suspend fun load(context: Context): EpgGuideLookup {
            val indexer = EpgIndexer.getInstance(context)
            return EpgGuideLookup(
                dao = EpgIndexDatabase.getInstance(context).epgIndexDao(),
                scope = GuideScope.load(context),
                isIndexed = { indexer.state.value !is EpgIndexState.NotIndexed },
                isFtsStale = indexer::isFtsStale,
            )
        }

        /** [terms] as one FTS query: each a quoted phrase, joined by OR (one level, read alike by both FTS query syntaxes). */
        internal fun ftsQuery(terms: Collection<String>): String =
            terms
                .map { SportsText.fold(it) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" OR ") { "\"$it\"" }
    }

    override suspend fun programmesOnChannels(
        epgChannelIds: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): List<GuideProgramme> {
        val sourceIds = scope.sourceIds
        if (epgChannelIds.isEmpty() || sourceIds.isEmpty() || !isIndexed()) return emptyList()
        val rows = read { dao.getProgrammesForChannels(epgChannelIds.toList(), sourceIds, fromSec, toSec) }
        return rows.toProgrammes(fromSec, toSec)
    }

    override suspend fun searchTitles(
        terms: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): List<GuideProgramme> {
        val sourceIds = scope.sourceIds
        if (terms.isEmpty() || sourceIds.isEmpty() || !isIndexed()) return emptyList()
        val rows =
            if (isFtsStale()) {
                terms
                    // Folded terms have no `\`, `%` or `_` to escape.
                    .map(SportsText::fold)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .flatMap { pattern -> read { dao.searchByTitleLike(pattern, sourceIds, fromSec, toSec) } }
                    .distinctBy { it.id }
            } else {
                val query = ftsQuery(terms)
                if (query.isEmpty()) emptyList() else read { dao.searchByTitleFts(query, sourceIds, fromSec, toSec) }
            }
        return rows.toProgrammes(fromSec, toSec)
    }

    private suspend fun read(query: suspend () -> List<EpgSearchResultRow>): List<EpgSearchResultRow> =
        suspendRunCatching { withTimeoutOrNull(TIMEOUT_MS) { query() } }
            .onFailure { Log.w(TAG, "Guide read failed", it) }
            .getOrNull()
            .orEmpty()

    private fun List<EpgSearchResultRow>.toProgrammes(
        fromSec: Long,
        toSec: Long,
    ): List<GuideProgramme> =
        // A guide source read by several sources gives one programme per source, each matched on
        // that source's channels.
        filter { it.startEpoch in fromSec..toSec }.flatMap { row ->
            scope.providerIdsOf(row.sourceId).ifEmpty { listOf(0L) }.map { providerId -> row.toProgramme(providerId) }
        }

    private fun EpgSearchResultRow.toProgramme(providerId: Long): GuideProgramme =
        GuideProgramme(
            providerId = providerId,
            channelId = channelId,
            channelName = channelDisplayName,
            title = title,
            description = description,
            category = category,
            startSec = startEpoch,
            endSec = endEpoch,
        )
}
