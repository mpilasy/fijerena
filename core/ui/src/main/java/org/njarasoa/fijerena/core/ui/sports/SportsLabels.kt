package org.njarasoa.fijerena.core.ui.sports

import android.content.res.Resources
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.GameChannelHow
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.IllformedLocaleException
import java.util.Locale

// The words of the sports screens, the same on TV and phone (the sports teams plan → Screens).

/** A sport's name in the app language, from the source's sport key ("soccer", "water-polo"); null for one the app does not name. */
fun sportLabel(
    resources: Resources,
    sport: String?,
): String? =
    when (sport?.lowercase()) {
        "soccer" -> R.string.sport_soccer
        "football" -> R.string.sport_american_football
        "basketball" -> R.string.sport_basketball
        "baseball" -> R.string.sport_baseball
        "hockey" -> R.string.sport_hockey
        "rugby" -> R.string.sport_rugby
        "rugby-league" -> R.string.sport_rugby_league
        "cricket" -> R.string.sport_cricket
        "tennis" -> R.string.sport_tennis
        "golf" -> R.string.sport_golf
        "mma" -> R.string.sport_mma
        "racing" -> R.string.sport_racing
        "australian-football" -> R.string.sport_australian_football
        "lacrosse" -> R.string.sport_lacrosse
        "volleyball" -> R.string.sport_volleyball
        "water-polo" -> R.string.sport_water_polo
        else -> null
    }?.let(resources::getString)

/** A team's second line in a list: its sport and its league, the parts known. */
fun teamDetails(
    resources: Resources,
    team: SportsTeam,
): String = listOfNotNull(sportLabel(resources, team.sport), team.leagueName?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** "Today", "Tomorrow", else the day's full date in the app language. */
fun gameDayLabel(
    resources: Resources,
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): String =
    when (date) {
        today -> {
            resources.getString(R.string.epg_tab_today)
        }

        today.plusDays(1) -> {
            resources.getString(R.string.epg_tab_tomorrow)
        }

        // The system has no Malagasy day or month names (see displayDate).
        else -> {
            if (locale.language ==
                "mg"
            ) {
                malagasyDate(date)
            } else {
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
            }
        }
    }

private fun malagasyDate(date: LocalDate): String =
    "${MALAGASY_DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${MALAGASY_MONTHS[date.monthValue - 1]}"

/** A game's title: "Arsenal v Chelsea", or the one name of an event with no second team. */
fun gameTitle(
    resources: Resources,
    game: SportsGame,
): String = game.away?.let { resources.getString(R.string.sports_game_format, game.home.name, it.name) } ?: game.home.name

/** Whether [team] plays [game] at home: the game's home side has its id. */
fun isHomeGame(
    game: SportsGame,
    team: SportsTeam,
): Boolean = game.home.source == team.source && game.home.id == team.id

/** [team]'s opponent in [game], null for an event with no second team. */
fun opponentIn(
    game: SportsGame,
    team: SportsTeam,
): SportsTeam? = if (isHomeGame(game, team)) game.away else game.home

/**
 * What a channel row says under the channel's name: "In the guide" for a guide programme,
 * "On Canal+" for a broadcaster's channel or a broadcaster with no channel, "TV not announced
 * yet" when nothing is known.
 */
fun channelNote(
    resources: Resources,
    channel: GameChannel,
): String =
    when (channel.how) {
        GameChannelHow.GUIDE_ON_BROADCASTER, GameChannelHow.GUIDE -> {
            resources.getString(R.string.sports_in_guide)
        }

        GameChannelHow.BROADCASTER_CHANNEL, GameChannelHow.BROADCASTER_ONLY -> {
            channel.broadcaster?.let { resources.getString(R.string.sports_on_broadcaster, it.name) }
                ?: resources.getString(R.string.sports_not_announced)
        }

        GameChannelHow.NOT_ANNOUNCED -> {
            resources.getString(R.string.sports_not_announced)
        }
    }

/** A country's name in the app language, from its ISO code. */
fun countryName(
    code: String,
    locale: Locale = Locale.getDefault(),
): String =
    try {
        Locale
            .Builder()
            .setRegion(code)
            .build()
            .getDisplayCountry(locale)
    } catch (_: IllformedLocaleException) {
        ""
    }.ifBlank { code }

/**
 * Where a broadcaster shows the game: its countries by name when they are few or [expanded],
 * else their number ("48 countries"); "Worldwide" first for a service with no country.
 */
fun broadcasterCountries(
    resources: Resources,
    group: BroadcasterGroup,
    expanded: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    val countries =
        when {
            group.countries.isEmpty() -> {
                null
            }

            expanded || group.countries.size <= FEW_COUNTRIES -> {
                group.countries
                    .map { countryName(it, locale) }
                    .sorted()
                    .joinToString(", ")
            }

            else -> {
                resources.getQuantityString(R.plurals.sports_countries, group.countries.size, group.countries.size)
            }
        }
    return listOfNotNull(resources.getString(R.string.sports_worldwide).takeIf { group.worldwide }, countries).joinToString(" · ")
}

/** Whether [group]'s country list is shortened, so OK or a tap shows all of it. */
fun hasMoreCountries(group: BroadcasterGroup): Boolean = group.countries.size > FEW_COUNTRIES

/** Up to this many countries are named; more are counted. */
private const val FEW_COUNTRIES = 3

private val MALAGASY_DAYS = listOf("Alatsinainy", "Talata", "Alarobia", "Alakamisy", "Zoma", "Asabotsy", "Alahady")

private val MALAGASY_MONTHS =
    listOf(
        "Janoary",
        "Febroary",
        "Martsa",
        "Aprily",
        "Mey",
        "Jona",
        "Jolay",
        "Aogositra",
        "Septambra",
        "Oktobra",
        "Novambra",
        "Desambra",
    )
