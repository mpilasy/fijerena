package org.njarasoa.fijerena.core.network.wanted

import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.TmdbReleaseDatesResponse
import org.njarasoa.fijerena.core.player.domain.ContentType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A title's release as TMDB gives it, kept on its Wishlist row: ISO days, or null when unknown. */
data class ReleaseInfo(
    val releaseDate: String? = null,
    val digitalDate: String? = null,
    val inCinemas: Boolean = false,
    val showStatus: String? = null,
)

/** Where a title no source has is in its release (wishlist UX plan, Phase 3). */
enum class ReleaseStage {
    /** A film not out yet: [ReleaseStatus.date] is its first release. */
    COMING,

    /** A show not started yet: [ReleaseStatus.date] is its first air date. */
    STARTS,

    /** A film in cinemas, not yet on digital or disc: [ReleaseStatus.date] is its digital date when known. */
    IN_CINEMAS,

    /** A film out on digital or disc in the last [ReleaseStatus.RECENT_DAYS] days. */
    OUT_ON_DIGITAL,

    /** Out for longer, or nothing known: nothing to wait for. */
    OUT,
}

data class ReleaseStatus(
    val stage: ReleaseStage,
    val date: LocalDate? = null,
) {
    companion object {
        /** How long after its digital or disc release a film counts as just out (U-D1). */
        const val RECENT_DAYS = 90L

        /** A cinema release older than this with no digital date known is just out, not in cinemas. */
        const val CINEMA_DAYS = 365L

        private val TV = setOf(ContentType.TV_SHOWS)

        fun of(
            contentType: String,
            info: ReleaseInfo,
            today: LocalDate,
        ): ReleaseStatus {
            val release = day(info.releaseDate)
            val digital = day(info.digitalDate)
            return when {
                contentType in TV && release != null && release.isAfter(today) -> {
                    ReleaseStatus(ReleaseStage.STARTS, release)
                }

                contentType in TV -> {
                    ReleaseStatus(ReleaseStage.OUT)
                }

                release != null && release.isAfter(today) -> {
                    ReleaseStatus(ReleaseStage.COMING, release)
                }

                digital != null && !digital.isAfter(today) -> {
                    val stage =
                        if (ChronoUnit.DAYS.between(digital, today) <=
                            RECENT_DAYS
                        ) {
                            ReleaseStage.OUT_ON_DIGITAL
                        } else {
                            ReleaseStage.OUT
                        }
                    ReleaseStatus(stage)
                }

                info.inCinemas && release != null && ChronoUnit.DAYS.between(release, today) <= CINEMA_DAYS -> {
                    ReleaseStatus(ReleaseStage.IN_CINEMAS, digital)
                }

                else -> {
                    ReleaseStatus(ReleaseStage.OUT)
                }
            }
        }

        /**
         * A film's release from TMDB `release_dates`: the dates of [region] when TMDB has any there,
         * else of every country. First release: the earliest; digital: the earliest digital (4) or
         * disc (5) one; in cinemas: a premiere or cinema release (1–3) is known.
         */
        fun ofMovie(
            response: TmdbReleaseDatesResponse,
            region: String,
        ): ReleaseInfo {
            val all = response.results.flatMap { it.releaseDates }
            val regional = response.results.filter { it.country.equals(region, ignoreCase = true) }.flatMap { it.releaseDates }
            val entries = regional.ifEmpty { all }

            fun earliest(types: Set<Int>?) =
                entries
                    .filter { types == null || it.type in types }
                    .mapNotNull { it.releaseDate?.take(DAY_LENGTH) }
                    .minOrNull()
            return ReleaseInfo(
                releaseDate = earliest(null),
                digitalDate = earliest(setOf(DIGITAL, DISC)),
                inCinemas = entries.any { it.type in CINEMA },
            )
        }

        /** A show's release from its TMDB details: first air date and status. */
        fun ofShow(details: TmdbRecommendation): ReleaseInfo =
            ReleaseInfo(releaseDate = details.firstAirDate?.takeIf { it.isNotBlank() }?.take(DAY_LENGTH), showStatus = details.status)

        private const val DAY_LENGTH = 10
        private const val DIGITAL = 4
        private const val DISC = 5
        private val CINEMA = setOf(1, 2, 3)

        private fun day(iso: String?): LocalDate? =
            try {
                iso?.let(LocalDate::parse)
            } catch (e: java.time.format.DateTimeParseException) {
                // cancellation-ok: non-suspend
                null
            }
    }
}
