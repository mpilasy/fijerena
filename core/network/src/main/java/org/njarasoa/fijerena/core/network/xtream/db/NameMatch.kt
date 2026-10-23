package org.njarasoa.fijerena.core.network.xtream.db

/** A film, channel or series found by name: its id, `LIVE`/`VOD`/`SERIES`, its category and name. */
data class NameMatch(
    val id: Int,
    val type: String,
    val categoryId: String?,
    val name: String,
)
