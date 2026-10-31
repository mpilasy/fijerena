package org.njarasoa.fijerena.core.network.tmdb

/**
 * The people of a film or show that the Cast tab shows (docs/plans/20261030_cast-tab-plan.md):
 * the crew lines at the top, then the cast cards. Made by [titleCredits]. [cast] is TMDB's full
 * cast in billing order; the tab shows the first [MAX_CAST_CARDS] until the user asks for more.
 */
data class TitleCredits(
    val cast: List<CastMember>,
    val crew: List<CrewLine>,
) {
    val isEmpty: Boolean get() = cast.isEmpty() && crew.isEmpty()
}

/** One cast card. [character] is the role; for a show, the role in the most episodes. */
data class CastMember(
    val personId: String,
    val name: String,
    val character: String?,
    val photoUrl: String?,
)

/** One crew line: a role and the people in it, in TMDB's order. */
data class CrewLine(
    val role: CrewRole,
    val people: List<CrewPerson>,
)

data class CrewPerson(
    val personId: String,
    val name: String,
)

/** The crew lines (CT-D4): Director for a film, Created by for a show, and Writers for both. */
enum class CrewRole { DIRECTOR, CREATOR, WRITER }

/** CT-D2: the cast cards the Cast tab shows before its More card. */
const val MAX_CAST_CARDS = 10

/**
 * The actors each press of the More card adds. A long show's cast runs to thousands
 * (Law & Order: over 7,600): only the cards asked for are ever built.
 */
const val CAST_MORE_STEP = 30

/** The actors a More card adds when [shown] of [total] are on screen. */
fun castMoreCount(
    shown: Int,
    total: Int,
): Int = (total - shown).coerceIn(0, CAST_MORE_STEP)

/** The most people on the Writers line. */
const val MAX_WRITERS = 5

/** A film's writing jobs that put a person on the Writers line. */
private val FILM_WRITING_JOBS = setOf("Screenplay", "Writer", "Story", "Original Story", "Novel", "Author", "Book", "Teleplay")

private const val DIRECTOR = "Director"
private const val WRITING = "Writing"

/**
 * The Cast tab's people from a details response of [getDetailsWithCredits][TmdbApiService.getDetailsWithCredits].
 * A film's from `credits`; a show's from `aggregate_credits` and `created_by`. A person without a
 * TMDB id or a name is left out, and a line with nobody is not given.
 */
fun titleCredits(
    details: TmdbRecommendation,
    movie: Boolean,
): TitleCredits {
    val credits = details.anyCredits ?: TmdbCredits()
    val cast =
        credits.cast
            .mapNotNull { member ->
                val id = member.id ?: return@mapNotNull null
                val name = member.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val character =
                    if (movie) {
                        member.character
                    } else {
                        member.roles.maxByOrNull { it.episodeCount }?.character ?: member.character
                    }
                CastMember(id.toString(), name, character?.takeIf { it.isNotBlank() }, TmdbApiService.profileUrl(member.profilePath))
            }.distinctBy { it.personId }
    val crew =
        if (movie) {
            listOf(
                CrewLine(CrewRole.DIRECTOR, credits.crew.filter { it.job == DIRECTOR }.people()),
                CrewLine(
                    CrewRole.WRITER,
                    credits.crew
                        .filter { it.job in FILM_WRITING_JOBS }
                        .people()
                        .take(MAX_WRITERS),
                ),
            )
        } else {
            val creators =
                details.createdBy.mapNotNull { creator ->
                    val id = creator.id ?: return@mapNotNull null
                    creator.name?.takeIf { it.isNotBlank() }?.let { CrewPerson(id.toString(), it) }
                }
            val writers =
                credits.crew
                    .filter { it.department == WRITING }
                    .sortedByDescending { member -> member.totalEpisodeCount ?: member.jobs.sumOf { it.episodeCount } }
                    .people()
                    .take(MAX_WRITERS)
            listOf(CrewLine(CrewRole.CREATOR, creators.distinctBy { it.personId }), CrewLine(CrewRole.WRITER, writers))
        }
    return TitleCredits(cast, crew.filter { it.people.isNotEmpty() })
}

/**
 * The guest stars of TMDB's [episode] as cast cards, in billing order; a person without a TMDB id
 * or a name is left out. Empty for no episode.
 */
fun episodeGuests(episode: TmdbEpisode?): List<CastMember> =
    episode
        ?.guestStars
        .orEmpty()
        .sortedBy { it.order }
        .mapNotNull { guest ->
            val id = guest.id ?: return@mapNotNull null
            val name = guest.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CastMember(id.toString(), name, guest.character?.takeIf { it.isNotBlank() }, TmdbApiService.profileUrl(guest.profilePath))
        }.distinctBy { it.personId }

/**
 * An episode's cast section (docs/plans/20261030_episode-cast-plan.md, EC-D1): the episode's
 * [guests] in one row and the show's regular cast ([show], the Cast tab's) in another (user,
 * 2026-10-31), each person once: a guest is not again among the regulars. No crew lines. Null
 * when TMDB has nobody for it: the section then is not shown.
 */
fun episodeCredits(
    guests: List<CastMember>,
    show: TitleCredits?,
): EpisodeCredits? {
    val guestCast = guests.distinctBy { it.personId }
    val guestIds = guestCast.mapTo(HashSet()) { it.personId }
    val regulars =
        show
            ?.cast
            .orEmpty()
            .filter { it.personId !in guestIds }
            .distinctBy { it.personId }
    if (guestCast.isEmpty() && regulars.isEmpty()) return null
    return EpisodeCredits(
        guests = guestCast.takeIf { it.isNotEmpty() }?.let { TitleCredits(cast = it, crew = emptyList()) },
        regulars = regulars.takeIf { it.isNotEmpty() }?.let { TitleCredits(cast = it, crew = emptyList()) },
    )
}

/** An episode's cast: its guest stars and the show's regular cast, each a row of cards, either null when empty. */
data class EpisodeCredits(
    val guests: TitleCredits?,
    val regulars: TitleCredits?,
)

/** Each person once, in this order; a person without an id or a name is left out. */
private fun List<TmdbCrewMember>.people(): List<CrewPerson> =
    mapNotNull { member ->
        val id = member.id ?: return@mapNotNull null
        member.name?.takeIf { it.isNotBlank() }?.let { CrewPerson(id.toString(), it) }
    }.distinctBy { it.personId }
