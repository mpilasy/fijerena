package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.profile.ProfileFavorite
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.ui.components.input.TvSearchField
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A child profile's Favourites, in place of its page like its Sources (docs/plans/20261019_kids-home-plan.md,
 * Phase 3): what its Home shows, filled from the admin's Settings. The field searches the child's
 * sources for what the child may see; with no search, the list is the child's favourites. OK on a
 * row adds it or takes it out, the star saying which, and the row stays, so a second OK undoes.
 * Focus opens on the field; Back and Left leave, except while the keyboard is up.
 */
@Composable
internal fun ProfileFavoritesPane(
    profile: ProfileUi,
    viewModel: ProfilesViewModel,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    BackHandler(enabled = !editing, onBack = onBack)
    val fieldFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusWithRetry() }

    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf<List<ProfileFavorite>?>(null) }
    var results by remember { mutableStateOf<List<ProfileFavorite>?>(null) }
    LaunchedEffect(profile.id) { favorites = viewModel.favoritesOf(profile.id) }
    // Searched as it is typed, once the typing pauses.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        results = viewModel.searchFavoritesFor(profile.id, query)
    }
    val toggle: (ProfileFavorite) -> Unit = { item ->
        val on = !item.isFavorite
        viewModel.setFavoriteFor(profile.id, item, on)
        val flip: (List<ProfileFavorite>?) -> List<ProfileFavorite>? = { list ->
            list?.map { if (it.key == item.key) it.copy(isFavorite = on) else it }
        }
        results = flip(results)
        // Added from a search: on the list too when the search is cleared.
        favorites =
            if (on && favorites?.none { it.key == item.key } == true) {
                listOf(item.copy(isFavorite = true)) + favorites.orEmpty()
            } else {
                flip(favorites)
            }
    }
    val rows = if (query.isBlank()) favorites else results

    Column(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                when {
                    editing -> {
                        false
                    }

                    event.key == Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        true
                    }

                    event.key == Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onBack()
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        CinemaText(
            "‹ ${stringResource(R.string.profile_favorites_title)}",
            modifier = Modifier.padding(horizontal = Spacing.xs),
            role = TextRole.Title,
            tone = TextTone.Primary,
        )
        CinemaText(
            stringResource(R.string.profile_favorites_desc, profile.name),
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            role = TextRole.Body,
            tone = TextTone.Secondary,
        )
        TvSearchField(
            query = query,
            onQueryChange = { query = it },
            onSearchSubmit = {},
            onClear = { query = "" },
            placeholder = stringResource(R.string.profile_favorites_search),
            focusRequester = fieldFocus,
            editing = editing,
            onEditingChange = { editing = it },
            modifier =
                Modifier.fillMaxWidth().then(
                    if (rows.isNullOrEmpty()) Modifier else Modifier.focusProperties { down = firstRowFocus },
                ),
        )
        when {
            rows == null -> {
                Unit
            }

            rows.isEmpty() -> {
                CinemaText(
                    if (query.isBlank()) {
                        stringResource(R.string.profile_favorites_none)
                    } else {
                        stringResource(R.string.profile_favorites_no_results, profile.name)
                    },
                    modifier = Modifier.padding(Spacing.xs),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(rows, key = { it.key }) { item ->
                        SettingsRow(
                            title = parseDisplayTitle(item.name).title.ifBlank { item.name },
                            description = stringResource(sectionLabel(item.contentType)),
                            onClick = { toggle(item) },
                            chevron = false,
                            focusRequester = if (item.key == rows.first().key) firstRowFocus else null,
                            leading = {
                                Icon(
                                    imageVector = if (item.isFavorite) CinemaIcons.Star else CinemaIcons.StarBorder,
                                    contentDescription =
                                        stringResource(if (item.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                                    tint = if (item.isFavorite) CinemaTheme.colors.accent else CinemaTheme.colors.textPrimary,
                                    modifier = Modifier.size(TvDimensions.iconMedium),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun sectionLabel(contentType: String): Int =
    when (contentType) {
        ContentType.LIVE_TV -> R.string.provider_live_tv_label
        ContentType.MOVIES -> R.string.provider_movies_label
        else -> R.string.provider_tv_shows_label
    }

private const val SEARCH_DEBOUNCE_MS = 400L
