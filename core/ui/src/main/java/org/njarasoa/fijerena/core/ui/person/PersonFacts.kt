package org.njarasoa.fijerena.core.ui.person

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.tmdb.CrewRole
import org.njarasoa.fijerena.core.network.tmdb.PersonRole
import org.njarasoa.fijerena.core.network.tmdb.SPECIALS
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.network.tmdb.ShowTarget
import org.njarasoa.fijerena.core.network.tmdb.TmdbPerson
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.displayDate

/** "Born 3 Jul 1962 • Died … • Syracuse, New York, USA": the facts under a person's name; null when TMDB has none. */
@Composable
fun personFactsLine(person: TmdbPerson): String? =
    listOfNotNull(
        person.birthday?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.person_born, displayDate(it)) },
        person.deathday?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.person_died, displayDate(it)) },
        person.placeOfBirth?.takeIf { it.isNotBlank() },
    ).joinToString(" • ").ifEmpty { null }

/** See the [Resources] form. */
@Composable
fun personRoleText(role: PersonRole?): String? = personRoleText(LocalResources.current, role)

/**
 * "as Paul", for a show's role "24 episodes · as Olivia Benson" (docs/plans/20261030_episode-cast-plan.md,
 * EC-D2), or on a crew page "Director, Writer": a person's part in a title; null when TMDB has none.
 */
fun personRoleText(
    resources: Resources,
    role: PersonRole?,
): String? {
    val part =
        role?.character?.let { resources.getString(R.string.cast_as_character, it) }
            ?: role
                ?.jobs
                ?.map { job ->
                    when (job) {
                        CrewRole.DIRECTOR -> resources.getString(R.string.person_job_director)
                        CrewRole.CREATOR -> resources.getString(R.string.person_job_creator)
                        CrewRole.WRITER -> resources.getString(R.string.person_job_writer)
                    }
                }?.joinToString(", ")
                ?.ifEmpty { null }
    val episodes =
        role?.episodeCount?.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.person_episode_count, it, it) }
    return listOfNotNull(episodes, part).joinToString(" · ").ifEmpty { null }
}

/** A line of a person's appearances in a show: "S9E17 · Authority", "S9E17", "Season 3" or "Specials". */
fun appearanceLabel(
    resources: Resources,
    line: ShowAppearance,
): String =
    when {
        line.episode != null && line.name != null -> {
            resources.getString(R.string.person_episode_code_title, line.season, line.episode, line.name)
        }

        else -> {
            appearanceCode(resources, line)
        }
    }

/** The note for a [target] the source lacks (EC-D3): "S9E17 is not in your source"; nothing when it has it. */
fun showMissingNote(
    context: Context,
    target: ShowTarget,
) {
    target.missing?.let { line ->
        val text = context.getString(R.string.person_not_in_source, appearanceCode(context.resources, line))
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}

/** A line of a person's appearances by its number only, for the note "S9E17 is not in your source". */
fun appearanceCode(
    resources: Resources,
    line: ShowAppearance,
): String =
    when {
        line.episode != null -> resources.getString(R.string.person_episode_code, line.season, line.episode)
        line.season == SPECIALS -> resources.getString(R.string.person_specials)
        else -> resources.getString(R.string.series_season_label, line.season)
    }
