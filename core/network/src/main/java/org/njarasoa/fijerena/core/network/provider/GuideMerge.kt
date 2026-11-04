package org.njarasoa.fijerena.core.network.provider

import org.njarasoa.fijerena.core.network.xmltv.GuideUrls

/**
 * How [SettingsDatabase.MIGRATION_19_20] makes one guide source of the copies one URL had, one per
 * media source (the one guide source per URL plan → Design M, OG-D4 to OG-D7, OG-Q1, OG-Q2, OG-Q7).
 */
internal object GuideMerge {
    /** One `epg_source` row of v19, with what the merge needs to know about it. */
    data class Row(
        val id: Long,
        val url: String,
        val label: String,
        val providerId: Long,
        /** The row's media source is still on the device. */
        val providerExists: Boolean,
        /** The row is its Xtream source's automatic guide: never merged (OG-D3). */
        val auto: Boolean,
        val sourceKey: String,
        val offsetHours: Int,
        val intervalHours: Int?,
        val enabled: Boolean,
        val lastIngestedAtMs: Long,
        val addedAtMs: Long,
        /** The row's live sync version (`sync_version.hlc`); null when it has none. */
        val hlc: Long?,
    )

    /** One guide source made of a group of rows. */
    data class Guide(
        /** The row that keeps its id, its guide index rows and its stats (OG-D4). */
        val survivorId: Long,
        val sourceKey: String,
        val label: String,
        val offsetHours: Int,
        val intervalHours: Int?,
        val enabled: Boolean,
        val addedAtMs: Long,
        /** Media source id to `linked_at_ms`; the guide-key row's source is first. */
        val links: Map<Long, Long>,
        /** The group's other keys, each with its row's media source (OG-D6). */
        val aliases: Map<String, Long>,
    )

    /**
     * The groups of [rows] that become one guide each: an automatic guide alone, a row whose source
     * is gone alone (Shrink Database deletes it later), the others by [GuideUrls.matchKey].
     */
    fun groups(rows: List<Row>): List<List<Row>> =
        rows
            .groupBy { row ->
                when {
                    row.auto -> "auto:${row.id}"
                    !row.providerExists -> "gone:${row.id}"
                    else -> "url:${GuideUrls.matchKey(row.url)}"
                }
            }.values
            .toList()

    fun merge(group: List<Row>): Guide {
        val survivor = group.sortedWith(compareByDescending<Row> { it.lastIngestedAtMs }.thenBy { it.id }).first()
        val keyRow = group.minBy { it.sourceKey }
        // OG-Q1: the offset edited last, by live sync version; the guide-key row when none is newer.
        val newest = group.maxOf { it.hlc ?: Long.MIN_VALUE }
        val edited = group.filter { (it.hlc ?: Long.MIN_VALUE) == newest }
        val offsetRow = edited.singleOrNull() ?: keyRow
        // OG-Q2: the shortest interval that is on; off only when every row with one is off.
        val intervals = group.mapNotNull { it.intervalHours }
        val on = intervals.filter { it != EpgSourceEntity.REFRESH_OFF }
        val interval =
            when {
                on.isNotEmpty() -> on.min()
                intervals.isNotEmpty() -> EpgSourceEntity.REFRESH_OFF
                else -> null
            }
        // OG-Q7: on when one row is on, read by the sources whose row was on.
        val withSource = group.filter { it.providerExists }
        val onRows = withSource.filter { it.enabled }
        val linkRows = onRows.ifEmpty { withSource }
        val links = LinkedHashMap<Long, Long>()
        linkRows.sortedBy { if (it === keyRow) Long.MIN_VALUE else it.addedAtMs }.forEach { row ->
            links.putIfAbsent(row.providerId, if (row === keyRow) 0L else row.addedAtMs)
        }
        return Guide(
            survivorId = survivor.id,
            sourceKey = keyRow.sourceKey,
            label = keyRow.label,
            offsetHours = offsetRow.offsetHours,
            intervalHours = interval,
            enabled = group.any { it.enabled },
            addedAtMs = group.minOf { it.addedAtMs },
            links = links,
            aliases = group.filter { it !== keyRow }.associate { it.sourceKey to it.providerId },
        )
    }
}
