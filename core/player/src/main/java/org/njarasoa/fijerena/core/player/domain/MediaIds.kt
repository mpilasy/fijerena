package org.njarasoa.fijerena.core.player.domain

import kotlinx.serialization.Serializable

/**
 * A show's id, as the active provider names it.
 *
 * Series and episode ids live in different namespaces but were both `String`, so passing one where
 * the other belonged compiled and failed on the provider — a series screen asking for the episodes
 * of an episode. These two exist so that mistake stops compiling.
 *
 * `@JvmInline` means no object at runtime: a `SeriesId` is the `String` it wraps. Ids stay raw at
 * the database and HTTP edges, where the provider's own types take over — call [raw] there.
 */
@JvmInline
@Serializable
value class SeriesId(val raw: String)

/** One episode's id, in the provider's episode namespace. See [SeriesId]. */
@JvmInline
@Serializable
value class EpisodeId(val raw: String)
