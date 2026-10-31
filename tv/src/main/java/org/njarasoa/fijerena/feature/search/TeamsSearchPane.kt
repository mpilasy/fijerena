package org.njarasoa.fijerena.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.MitadyLoading
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.sports.TeamsSearchViewModel
import org.njarasoa.fijerena.core.ui.sports.followKey
import org.njarasoa.fijerena.core.ui.sports.route
import org.njarasoa.fijerena.core.ui.sports.sportLabel
import org.njarasoa.fijerena.core.ui.sports.teamDetails
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.feature.sports.TvSportsItemRow
import org.njarasoa.fijerena.ui.components.TvEmptyState
import org.njarasoa.fijerena.ui.components.TvErrorState
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * Search's Teams type (the sports teams plan → Screens): the field the other types share, then
 * the teams and the leagues found, each group under a heading that takes no focus, a star on what
 * the profile follows. The search runs once typing stops and on the keyboard's Search; only the
 * keyboard's Search moves focus to the first result. Down from the field goes to the first result,
 * Up from it to the field. Each result records itself in [returnFocus], so Back from the page it
 * opened lands on it again.
 *
 * A team opens its page ([onTeamSelected]), a league its page ([onLeagueSelected]). Its view model
 * is made the first time the type shows, so another type's search never asks the sports sources.
 * [holdFocus]: a type switch keeps focus on the type selector.
 */
@Composable
internal fun TeamsSearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    holdFocus: Boolean,
    fieldModifier: Modifier,
    returnFocus: NavReturnFocus,
    onTeamSelected: (Screen.SportsTeam) -> Unit,
    onLeagueSelected: (Screen.SportsLeague) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: TeamsSearchViewModel = viewModel(factory = remember { TeamsSearchViewModel.Factory(context.applicationContext) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val followed by viewModel.followed.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    // Set by the keyboard's Search: the next results take focus.
    var jumpToResults by remember { mutableStateOf(false) }
    // As the People type: straight into the keyboard for a first search, else the field.
    var editing by remember { mutableStateOf(!holdFocus && returnFocus.key == null && query.isEmpty()) }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null && !holdFocus && !editing) fieldFocus.requestFocusWithRetry()
    }
    // The type shown with text it has not searched: at once. Then once typing stops.
    LaunchedEffect(Unit) { viewModel.search(query) }
    LaunchedEffect(query) {
        delay(CinemaAnimation.searchDebounceMs)
        viewModel.search(query)
    }
    val loaded = state as? TeamsSearchViewModel.State.Loaded
    LaunchedEffect(loaded) {
        if (loaded != null && jumpToResults) {
            jumpToResults = false
            if (!loaded.isEmpty) firstResultFocus.requestFocusWithRetry()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvSearchField(
            modifier =
                (if (loaded?.isEmpty == false) Modifier.focusProperties { down = firstResultFocus } else Modifier)
                    .then(fieldModifier),
            query = query,
            onQueryChange = onQueryChange,
            onSearchSubmit = {
                // The results on screen already are this query's: none come to jump to (Down reaches them).
                jumpToResults = (state as? TeamsSearchViewModel.State.Loaded)?.query != query.trim()
                viewModel.search(query)
            },
            onClear = {
                onQueryChange("")
                viewModel.search("")
            },
            placeholder = stringResource(R.string.search_teams_placeholder),
            focusRequester = fieldFocus,
            editing = editing,
            onEditingChange = { editing = it },
            restingFieldModifier = Modifier.leftToRail(),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        when (val shown = state) {
            TeamsSearchViewModel.State.Idle -> {
                TvEmptyState(message = stringResource(R.string.search_teams_placeholder), icon = CinemaIcons.Search)
            }

            is TeamsSearchViewModel.State.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MitadyLoading(role = TextRole.Display, tone = TextTone.Accent)
                }
            }

            is TeamsSearchViewModel.State.Failed -> {
                TvErrorState(
                    title = stringResource(R.string.sports_load_error),
                    message = shown.raw?.let { "[dev] $it" }.orEmpty(),
                    onRetry = viewModel::retry,
                )
            }

            is TeamsSearchViewModel.State.Loaded -> {
                if (shown.isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CinemaText(
                            text = stringResource(R.string.search_no_results_query_format_tv, shown.query),
                            tone = TextTone.Secondary,
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    NavReturnFocusEffect(returnFocus, listState = listState)
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (shown.teams.isNotEmpty()) {
                            item(key = "teams_header", contentType = "header") {
                                SearchSectionHeader(
                                    title = stringResource(R.string.search_tab_teams),
                                    count = shown.teams.size,
                                    hiddenCount = 0,
                                )
                            }
                            itemsIndexed(shown.teams, key = {
                                _,
                                team,
                                ->
                                team.followKey
                            }, contentType = { _, _ -> "team" }) { index, team ->
                                val returnKey = team.followKey
                                TvSportsItemRow(
                                    name = team.name,
                                    details = teamDetails(resources, team),
                                    logoUrl = team.logoUrl,
                                    followed = team.followKey in followed,
                                    onClick = {
                                        returnFocus.leaveFrom(returnKey, listState)
                                        onTeamSelected(team.route())
                                    },
                                    modifier =
                                        (if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                                            .navReturnFocusTarget(returnFocus, returnKey),
                                )
                            }
                        }
                        if (shown.leagues.isNotEmpty()) {
                            item(key = "leagues_header", contentType = "header") {
                                SearchSectionHeader(
                                    title = stringResource(R.string.sports_leagues_heading),
                                    count = shown.leagues.size,
                                    hiddenCount = 0,
                                )
                            }
                            itemsIndexed(shown.leagues, key = {
                                _,
                                league,
                                ->
                                league.followKey
                            }, contentType = { _, _ -> "league" }) { index, league ->
                                val returnKey = league.followKey
                                TvSportsItemRow(
                                    name = league.name,
                                    details = sportLabel(resources, league.sport).orEmpty(),
                                    logoUrl = league.logoUrl,
                                    followed = league.followKey in followed,
                                    onClick = {
                                        returnFocus.leaveFrom(returnKey, listState)
                                        onLeagueSelected(league.route())
                                    },
                                    modifier =
                                        (if (index == 0 && shown.teams.isEmpty()) Modifier.focusRequester(firstResultFocus) else Modifier)
                                            .navReturnFocusTarget(returnFocus, returnKey),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
