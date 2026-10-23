package org.njarasoa.fijerena.core.player.api

/**
 * The provider answered `[]` for [itemId] — its way of saying it has no such series or movie.
 * Nearly always a catalogue id that changed since the local catalogue was synced, so callers can
 * treat it as "re-resolve this id" rather than as a genuine empty result.
 */
class XtreamItemUnavailableException(
    val itemId: Int,
    val action: String,
) : Exception("Provider has no $action for id $itemId")
