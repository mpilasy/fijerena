package org.njarasoa.fijerena.core.network.tmdb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.wanted.TmdbOnlyItem
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.sourceKey
import java.io.File
import java.util.Locale

/**
 * The people read from TMDB, kept 7 days for the device (docs/plans/20261030_cast-tab-plan.md,
 * Design 5): an actor page opened again asks TMDB nothing. Kept in memory and in one file per
 * person under [dir]. A person read in another TMDB language ([language]) is read again.
 *
 * The credits of the person opened on an actor page ([credit], `credit/{credit_id}`) are kept with
 * the person, for as long as the person (docs/plans/20261030_episode-cast-plan.md).
 */
class TmdbPeople(
    private val dir: File,
    private val fetchPerson: suspend (Int) -> TmdbPerson,
    private val language: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
    private val fetchCredit: suspend (String) -> TmdbCredit = { error("No credit call") },
) {
    @Serializable
    private data class Stored(
        val fetchedAtMs: Long,
        val person: TmdbPerson,
        val language: String = "",
        // The person's credits read so far, by credit id.
        val credits: Map<String, TmdbCredit> = emptyMap(),
    )

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    private val lock = Mutex()
    private val memory = mutableMapOf<Int, Stored>()

    /** Person [personId]: the stored one, else TMDB's, stored. A failed call throws, and nothing is stored. */
    suspend fun person(personId: Int): TmdbPerson = lock.withLock { storedPerson(personId).person }

    /**
     * Credit [creditId] of person [personId]: the stored one, else TMDB's, stored with the person.
     * A failed call throws, and nothing is stored.
     */
    suspend fun credit(
        personId: Int,
        creditId: String,
    ): TmdbCredit =
        lock.withLock {
            val stored = storedPerson(personId)
            stored.credits[creditId] ?: fetchCredit(creditId).also { credit ->
                val updated = stored.copy(credits = stored.credits + (creditId to credit))
                memory[personId] = updated
                write(personId, updated)
            }
        }

    /** Under [lock]: the person stored and still fresh, else read again from TMDB and stored. */
    private suspend fun storedPerson(personId: Int): Stored {
        val stored = memory[personId] ?: read(personId)?.also { memory[personId] = it }
        val fresh = stored != null && stored.language == language() && now() - stored.fetchedAtMs in 0 until MAX_AGE_MS
        return if (fresh && stored != null) {
            stored
        } else {
            Stored(now(), fetchPerson(personId), language()).also { fetched ->
                memory[personId] = fetched
                write(personId, fetched)
            }
        }
    }

    /** Forgets every person read (Admin → Refresh Charts): the next actor page reads TMDB again. */
    suspend fun clear() {
        lock.withLock {
            memory.clear()
            withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
        }
    }

    private fun file(personId: Int) = File(dir, "$personId.json")

    private suspend fun read(personId: Int): Stored? =
        withContext(Dispatchers.IO) {
            try {
                file(personId).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Stored>(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Person $personId unreadable; read again from TMDB", e)
                null
            }
        }

    private suspend fun write(
        personId: Int,
        stored: Stored,
    ) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            file(personId).writeText(json.encodeToString(Stored.serializer(), stored))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept in memory still; the next start reads TMDB again.
            Log.w(TAG, "Person $personId not stored", e)
        }
    }

    companion object {
        private const val TAG = "TmdbPeople"
        private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: TmdbPeople? = null

        /** The device's people, in the app's cache directory, read with the app's TMDB key. */
        fun getInstance(context: Context): TmdbPeople =
            instance ?: synchronized(this) {
                instance ?: run {
                    val tmdb = TmdbApiService.getInstance(context)
                    TmdbPeople(
                        File(context.applicationContext.cacheDir, "tmdb_people"),
                        tmdb::getPerson,
                        language = { TmdbApiService.tmdbLanguage(Locale.getDefault().language) },
                        fetchCredit = tmdb::getCredit,
                    )
                }.also { instance = it }
            }
    }
}

/**
 * An actor page's rows: the person's films and shows, most popular first. [roles]: the person's
 * part in each card's title, by the card's `MediaItem.sourceKey`; a title without one is not in it.
 */
data class PersonRows(
    val movies: List<MediaItem>,
    val shows: List<MediaItem>,
    val roles: Map<String, PersonRole> = emptyMap(),
)

/**
 * A person's part in one title: the role played ([character]), or on a crew page the jobs
 * ([jobs], in [CrewRole] order). For a show's role, [episodeCount] (0 when TMDB gives none) and
 * [creditId], which `credit/{credit_id}` turns into the episodes or seasons of the role.
 */
data class PersonRole(
    val character: String? = null,
    val jobs: List<CrewRole> = emptyList(),
    val episodeCount: Int = 0,
    val creditId: String? = null,
)

/**
 * Finds a person's titles in one source: the source's title for each TMDB title of the content type
 * (`MOVIES` or `TV_SHOWS`), index-aligned, null where it has none (`MediaRepository.findTmdbTitles`).
 */
typealias PersonTitleSource = suspend (List<TmdbRecommendation>, String) -> List<MediaItem?>

/** CT-D10: the most credits of a person that are matched to the catalogue. */
const val PERSON_MAX_CREDITS = 50

private const val MOVIE = "movie"
private const val TV = "tv"

/** The crew departments of the people on the crew lines (CT-D4): directors, creators and writers. */
private val CREW_DEPARTMENTS = setOf("Directing", "Writing")

private const val DIRECTOR_JOB = "Director"
private const val CREATOR_JOB = "Creator"
private const val WRITING = "Writing"

/**
 * [person]'s part in each title, by the title's media type and TMDB id: the role played, for a
 * show the role in the most episodes (CT-D3); or with [crew] the jobs on the crew lines (CT-D4).
 * A title without a role or a job is not in it.
 */
internal fun personRoles(
    person: TmdbPerson,
    crew: Boolean,
): Map<Pair<String?, Int>, PersonRole> {
    val credits = person.combinedCredits ?: return emptyMap()
    val chosen = if (crew) credits.crew else credits.cast
    return chosen
        .groupBy { it.mediaType to it.id }
        .mapValues { (_, entries) ->
            if (crew) {
                val jobs =
                    entries
                        .mapNotNull { entry ->
                            when {
                                entry.job == DIRECTOR_JOB -> CrewRole.DIRECTOR
                                entry.job == CREATOR_JOB -> CrewRole.CREATOR
                                entry.department == WRITING -> CrewRole.WRITER
                                else -> null
                            }
                        }.distinct()
                        .sorted()
                PersonRole(jobs = jobs).takeIf { jobs.isNotEmpty() }
            } else {
                // maxByOrNull keeps the first of equal counts: a film's first role.
                entries
                    .filter { !it.character.isNullOrBlank() }
                    .maxByOrNull { it.episodeCount }
                    ?.let { PersonRole(character = it.character?.trim(), episodeCount = it.episodeCount, creditId = it.creditId) }
            }
        }.mapNotNull { (key, role) -> role?.let { key to it } }
        .toMap()
}

/**
 * The [PERSON_MAX_CREDITS] most popular titles of [person], each once, most popular first: the
 * titles the person acted in, or with [crew] the titles the person directed or wrote (CT-D4).
 */
fun personCredits(
    person: TmdbPerson,
    crew: Boolean,
): List<TmdbPersonCredit> {
    val credits = person.combinedCredits ?: return emptyList()
    val chosen = if (crew) credits.crew.filter { it.department in CREW_DEPARTMENTS } else credits.cast
    return chosen
        .filter { it.mediaType == MOVIE || it.mediaType == TV }
        .filterNot(::isAppearance)
        .distinctBy { it.mediaType to it.id }
        .sortedByDescending { it.popularity }
        .take(PERSON_MAX_CREDITS)
}

/**
 * A credit that is not a role in a story: a talk, news or reality show, or the person as
 * themselves ("Self", "Himself", "Herself"). Found in the walk (2026-10-30): an actor's TV
 * Shows row was full of the talk shows they visited.
 */
internal fun isAppearance(credit: TmdbPersonCredit): Boolean =
    credit.genreIds.any { it in APPEARANCE_GENRES } ||
        credit.character
            ?.split(ROLE_PARTS)
            ?.any { it.trim().lowercase() in SELF_ROLES } == true

// TMDB TV genres: News, Reality, Talk. The charts leave out the same (TmdbChart.POPULAR_SHOWS).
private val APPEARANCE_GENRES = setOf(10763, 10764, 10767)
private val SELF_ROLES = setOf("self", "himself", "herself", "themselves")

// "Self - Guest", "Himself (archive footage)", "Narrator / Self": each part on its own.
private val ROLE_PARTS = Regex("[/,()-]")

/**
 * The rows of an actor page (Design 3–4): [person]'s titles ([personCredits]) cut down to the
 * catalogue, films and shows apart, each from the first of [sources] that has it (the profile's
 * order), most popular first. With [missingMovies] (films) or [missingShows] (shows), that row's
 * titles no source has are TMDB-only cards (`TmdbOnlyItem`) in their place: each row has its own
 * switch (UX-D7). Makes no TMDB call.
 */
suspend fun personRows(
    person: TmdbPerson,
    crew: Boolean,
    sources: List<PersonTitleSource>,
    missingMovies: Boolean = false,
    missingShows: Boolean = false,
): PersonRows {
    val credits = personCredits(person, crew)
    val roleOf = personRoles(person, crew)
    val roles = mutableMapOf<String, PersonRole>()

    suspend fun row(
        mediaType: String,
        contentType: String,
        itemType: MediaType,
        withMissing: Boolean,
    ): List<MediaItem> {
        val titles = credits.filter { it.mediaType == mediaType }.map { it.toRecommendation() }

        // Each card found for a title takes the person's part in that title.
        fun noteRole(
            title: TmdbRecommendation,
            item: MediaItem,
        ) {
            roleOf[mediaType to title.id]?.let { roles[item.sourceKey] = it }
        }
        val typed =
            sources.map { source ->
                suspend { list: List<TmdbRecommendation> ->
                    source(list, contentType).also { found ->
                        found.forEachIndexed { index, item -> if (item != null) noteRole(list[index], item) }
                    }
                }
            }
        return if (withMissing) {
            titlesWithMissing(titles, typed, missing = { title -> TmdbOnlyItem.of(title, itemType).also { noteRole(title, it) } })
        } else {
            matchTitles(titles, typed)
        }
    }
    val movies = row(MOVIE, ContentType.MOVIES, MediaType.MOVIE, missingMovies)
    val shows = row(TV, ContentType.TV_SHOWS, MediaType.SERIES, missingShows)
    // Only the cards on the rows: a source's card that another source's took the place of is left out.
    val shown = (movies + shows).mapTo(mutableSetOf()) { it.sourceKey }
    return PersonRows(movies = movies, shows = shows, roles = roles.filterKeys { it in shown })
}
