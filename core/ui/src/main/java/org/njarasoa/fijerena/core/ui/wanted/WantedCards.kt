package org.njarasoa.fijerena.core.ui.wanted

import android.content.res.Resources
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.tmdb.TmdbApiService
import org.njarasoa.fijerena.core.network.tmdb.tmdbListTitle
import org.njarasoa.fijerena.core.network.wanted.CatalogueHit
import org.njarasoa.fijerena.core.network.wanted.GoneFavorite
import org.njarasoa.fijerena.core.network.wanted.ReleaseInfo
import org.njarasoa.fijerena.core.network.wanted.ReleaseStage
import org.njarasoa.fijerena.core.network.wanted.ReleaseStatus
import org.njarasoa.fijerena.core.network.wanted.TmdbTitle
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.network.xtream.db.WantedTitleEntity
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One title of the Wanted list as Home's row and the Wanted lists show it (wanted titles plan →
 * Wanted on Home and in the sections). [copy] is the first source copy, null while no source has
 * the title.
 */
data class WantedCard(
    val entity: WantedTitleEntity,
    val copy: CatalogueHit?,
    /** The copy's source name, while the profile has more than one source (wishlist UX plan, Phase 5). */
    val sourceName: String? = null,
) {
    /** Unique in the list: the kind and the TMDB id. */
    val key: String get() = "${entity.contentType}:${entity.tmdbId}"

    val wantedKey: WantedTitles.Key get() = WantedTitles.Key(entity.contentType, entity.tmdbId)

    val isAvailable: Boolean get() = copy != null

    /** Available and not opened since: what the badge counts. */
    val isNew: Boolean get() = copy != null && entity.seenAt == null

    /**
     * The name the card shows ([tmdbListTitle]): the original title in Latin letters, else the
     * stored title. That one is in the app language, so it is English only in an English app.
     */
    val listTitle: String get() = tmdbListTitle(entity.originalTitle, entity.title) ?: entity.title

    val posterUrl: String? get() = TmdbApiService.posterUrl(entity.posterPath, TmdbApiService.POSTER_SIZE_W342)

    /** Where the card goes: the source copy's page when a source has it, else the TMDB page. */
    val route: Screen get() = copy?.route(entity.contentType) ?: TmdbTitle.of(entity).route()

    /** Where the title is in its release (wishlist UX plan, Phase 3); [ReleaseStage.OUT] when TMDB told nothing yet. */
    fun release(today: LocalDate = LocalDate.now()): ReleaseStatus =
        ReleaseStatus.of(
            entity.contentType,
            ReleaseInfo(entity.releaseDate, entity.digitalDate, entity.inCinemas, entity.showStatus),
            today,
        )
}

/** The short line under a title no source has: Coming 14 Mar 2027, In cinemas, Out on digital… */
fun ReleaseStatus.label(resources: Resources): String {
    val day = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    return when (stage) {
        ReleaseStage.COMING -> {
            resources.getString(R.string.wanted_coming, day)
        }

        ReleaseStage.STARTS -> {
            resources.getString(R.string.wanted_starts, day)
        }

        ReleaseStage.IN_CINEMAS -> {
            day?.let { resources.getString(R.string.wanted_in_cinemas_digital, it) }
                ?: resources.getString(R.string.wanted_in_cinemas)
        }

        ReleaseStage.OUT_ON_DIGITAL -> {
            resources.getString(R.string.wanted_out_on_digital)
        }

        ReleaseStage.OUT -> {
            resources.getString(R.string.wanted_not_in_sources)
        }
    }
}

/**
 * A card's state line: Available, with the copy's source (several sources) and language
 * ("Available · panel-a · FR", wishlist UX plan, Phase 5), or where the title is in its release.
 */
fun WantedCard.statusLabel(resources: Resources): String =
    copy?.let {
        listOfNotNull(resources.getString(R.string.wanted_available), sourceName, parseDisplayTitle(it.name).badge).joinToString(" · ")
    } ?: release().label(resources)

/**
 * The list's cards of [contentType] (every kind when null), in the Wishlist's order (U-D2):
 * available ones first; then the ones not out yet, soonest first; then the rest, newest added
 * first (the order [WantedTitles.State.titles] already has).
 */
fun WantedTitles.State.cards(
    contentType: String? = null,
    today: LocalDate = LocalDate.now(),
): List<WantedCard> {
    val cards =
        titles
            .filter { contentType == null || it.contentType == contentType }
            .map { title ->
                val copy = available[WantedTitles.Key(title.contentType, title.tmdbId)]?.firstOrNull()
                WantedCard(title, copy, copy?.let { sourceNames[it.providerId] })
            }

    fun upcoming(card: WantedCard) = card.release(today).takeIf { !card.isAvailable && it.stage in UPCOMING }?.date
    return cards.filter { it.isAvailable } +
        cards.filter { upcoming(it) != null }.sortedBy { upcoming(it) } +
        cards.filter { !it.isAvailable && upcoming(it) == null }
}

private val UPCOMING = setOf(ReleaseStage.COMING, ReleaseStage.STARTS)

/** How many titles of [contentType] (every kind when null) are available and not opened yet. */
fun WantedTitles.State.newCount(contentType: String? = null): Int = cards(contentType).count { it.isNew }

/** The available titles no banner has told the user about yet, on any device. */
fun WantedTitles.State.toAnnounce(): List<WantedCard> = cards().filter { it.isAvailable && it.entity.notifiedAt == null }

/** [providerId]'s favourite [itemId] of [contentType] when its source no longer lists it, else null. */
fun WantedTitles.State.gone(
    providerId: Long,
    itemId: String,
    contentType: String,
): GoneFavorite? = goneFavorites.firstOrNull { it.providerId == providerId && it.itemId == itemId && it.contentType == contentType }
