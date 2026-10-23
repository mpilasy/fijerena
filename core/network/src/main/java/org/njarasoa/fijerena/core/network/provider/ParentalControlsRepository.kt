package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.xtream.db.NameMatch
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * What Settings › Parental controls shows besides the lists (docs/plans/20261020_parental-controls-plan.md):
 * how much each entry matches on the child profiles' sources, and, for one source, which
 * categories a child sees. Local only, no network.
 */
class ParentalControlsRepository(
    private val context: Context,
) {
    private val database = XtreamDatabase.getInstance(context)

    /** A source the counts and the check view look at. */
    data class Source(
        val id: Long,
        val name: String,
    )

    /** A category of the check view: [type] is `LIVE`, `VOD` or `SERIES`. */
    data class CheckedCategory(
        val name: String,
        val type: String,
        val shown: Boolean,
    )

    /** The child profiles' Xtream sources; every Xtream source when there is no child profile. */
    suspend fun sources(): List<Source> =
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            val providers = ProviderRepository(context)
            val children =
                SettingsDatabase
                    .getInstance(context)
                    .profileDao()
                    .getAll()
                    .filter { settings.isChildProfile(it.id) }
            val entities =
                if (children.isEmpty()) {
                    providers.getAllProvidersList()
                } else {
                    children.flatMap { providers.profileSourcesList(it.id) }
                }
            entities.filter { it.type == "XTREAM" }.distinctBy { it.id }.map { Source(it.id, it.name) }
        }

    /**
     * How many categories ([ParentalList.KIDS_CATEGORIES], [ParentalList.KIDS_FILM_CATEGORIES],
     * [ParentalList.ADULT_CATEGORIES]) or titles ([ParentalList.HIDDEN_TITLES],
     * [ParentalList.EXCEPTIONS]) [entry] matches on [sources]. Exceptions count films and series
     * only; kids' film categories, film categories only.
     */
    suspend fun matchCount(
        list: ParentalList,
        entry: String,
        sources: List<Source>,
    ): Int =
        withContext(Dispatchers.IO) {
            val matcher = EntryMatcher(listOf(entry))
            when (list) {
                ParentalList.KIDS_CATEGORIES, ParentalList.ADULT_CATEGORIES -> {
                    sources.sumOf { source -> categories(source.id).count { matcher.matches(it.categoryName) } }
                }

                ParentalList.KIDS_FILM_CATEGORIES -> {
                    sources.sumOf { source ->
                        database.categoryDao().getAllCategoriesIncludingExcluded(source.id, XtreamCategoryEntity.TYPE_VOD).count {
                            matcher.matches(it.categoryName)
                        }
                    }
                }

                ParentalList.HIDDEN_TITLES -> {
                    sources.sumOf { source -> titleMatches(source.id, entry, films = true, live = true).count { matcher.matches(it.name) } }
                }

                ParentalList.EXCEPTIONS -> {
                    sources.sumOf { source ->
                        titleMatches(source.id, entry, films = true, live = false).count { matcher.matches(it.name) }
                    }
                }
            }
        }

    /** [providerId]'s categories as a child sees them with the lists stored now (its own content filters left out). */
    suspend fun check(providerId: Long): List<CheckedCategory> =
        withContext(Dispatchers.IO) {
            val rules = AppSettings(context).parentalRules()
            categories(providerId).map {
                CheckedCategory(
                    it.categoryName,
                    it.type,
                    rules.showsCategory(
                        it.categoryName,
                        films =
                            it.type == XtreamCategoryEntity.TYPE_VOD,
                    ),
                )
            }
        }

    private fun categories(providerId: Long): List<XtreamCategoryEntity> =
        listOf(XtreamCategoryEntity.TYPE_LIVE, XtreamCategoryEntity.TYPE_VOD, XtreamCategoryEntity.TYPE_SERIES).flatMap {
            database.categoryDao().getAllCategoriesIncludingExcluded(providerId, it)
        }

    /**
     * The films and series (and, with [live], channels) of [providerId] whose names have [entry]'s
     * words, found with the full-text index: a first pass that [EntryMatcher] then checks exactly.
     */
    fun titleMatches(
        providerId: Long,
        entry: String,
        films: Boolean,
        live: Boolean,
    ): List<NameMatch> {
        val query = ftsQuery(entry) ?: return emptyList()
        val streams =
            database.streamDao().matchNames(providerId, query).filter {
                (films && it.type == XtreamCategoryEntity.TYPE_VOD) || (live && it.type == XtreamCategoryEntity.TYPE_LIVE)
            }
        val series = if (films) database.seriesDao().matchNames(providerId, query) else emptyList()
        return streams + series
    }

    companion object {
        private val nonWord = Regex("[^\\p{L}\\p{N}]+")

        /** [entry] as an FTS phrase: its words in order, the last one open when it ends with `*`. Null with no word. */
        fun ftsQuery(entry: String): String? {
            val words = entry.trim().split(nonWord).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            val open = entry.trim().endsWith("*")
            return "\"" + words.joinToString(" ") + (if (open) "*" else "") + "\""
        }
    }
}
