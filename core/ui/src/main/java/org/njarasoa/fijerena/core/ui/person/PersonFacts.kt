package org.njarasoa.fijerena.core.ui.person

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.tmdb.CrewRole
import org.njarasoa.fijerena.core.network.tmdb.PersonRole
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

/** "as Paul", or on a crew page "Director, Writer": a person's part in a title; null when TMDB has none. */
@Composable
fun personRoleText(role: PersonRole?): String? =
    role?.character?.let { stringResource(R.string.cast_as_character, it) }
        ?: role
            ?.jobs
            ?.map { job ->
                when (job) {
                    CrewRole.DIRECTOR -> stringResource(R.string.person_job_director)
                    CrewRole.CREATOR -> stringResource(R.string.person_job_creator)
                    CrewRole.WRITER -> stringResource(R.string.person_job_writer)
                }
            }?.joinToString(", ")
            ?.ifEmpty { null }
