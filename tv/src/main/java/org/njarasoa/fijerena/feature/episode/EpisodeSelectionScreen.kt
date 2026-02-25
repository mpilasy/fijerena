@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.episode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import org.njarasoa.fijerena.core.player.domain.SeasonInfo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.domain.SeriesDetail
import org.njarasoa.fijerena.core.ui.components.CinemaThumbnail
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ThumbnailContentType
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SeriesDetailsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.core.player.domain.EpisodeItem as DomainEpisodeItem

@Composable
fun EpisodeSelectionScreen(
    seriesId: String,
    seriesName: String,
    categoryId: String,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailsViewModel = viewModel(
        factory = SeriesDetailsViewModelFactory(
            context = LocalContext.current,
            seriesId = seriesId,
            categoryId = categoryId
        )
    )
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    val uiState by viewModel.uiState.collectAsState()

    // Provide UI scale for all child composables
    CompositionLocalProvider(LocalUiScale provides uiScale) {
        when (val state = uiState) {
            is SeriesDetailsViewModel.UiState.Loading -> {
                LoadingScreen()
            }
            is SeriesDetailsViewModel.UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onBack = onBack
                )
            }
            is SeriesDetailsViewModel.UiState.Success -> {
                EpisodeListContent(
                    seriesDetail = state.seriesDetail,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    categoryId = categoryId,
                    isFavorite = state.isFavorite,
                    onEpisodeSelected = onEpisodeSelected,
                    onToggleFavorite = { viewModel.toggleFavorite(state.seriesDetail.name.ifEmpty { seriesName }) },
                    onRefresh = { viewModel.loadSeriesInfo() },
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    seriesDetail: SeriesDetail,
    seriesId: String,
    seriesName: String,
    categoryId: String,
    isFavorite: Boolean,
    onEpisodeSelected: (episodeId: String, episodeTitle: String, extension: String, startFromBeginning: Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }
    val listState = rememberLazyListState()
    val scale = LocalUiScale.current

    // Selected episode for detail panel
    var selectedEpisode by remember { mutableStateOf<DomainEpisodeItem?>(null) }

    // Handle back press: dismiss detail panel first, then navigate back
    BackHandler(enabled = selectedEpisode != null) {
        selectedEpisode = null
    }

    // Track refresh state for animation
    var isRefreshing by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableStateOf(0f) }

    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        finishedListener = { if (isRefreshing) targetRotation += 360f }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
            )
    ) {
        // Header: Back | Series Title | Favorite | Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.lg.scaled(scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
            ) {
                CinemaIconButton(
                    onClick = onBack,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                )
                Column {
                    Text(
                        text = seriesDetail.name.ifEmpty { seriesName },
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))
            ) {
                CinemaIconButton(
                    onClick = onToggleFavorite,
                    icon = {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                            tint = if (isFavorite) CinemaAccent else CinemaTextSecondary
                        )
                    }
                )
                CinemaIconButton(
                    onClick = {
                        isRefreshing = true
                        targetRotation += 360f
                        onRefresh()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Info",
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale))
        ) {
            // Left column: Series Poster + Metadata
            Column(
                modifier = Modifier
                    .width(TvDimensions.posterWidth.scaled(scale))
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
            ) {
                CinemaThumbnail(
                    url = seriesDetail.coverUrl,
                    fallbackLetter = seriesDetail.name.firstOrNull(),
                    contentType = ThumbnailContentType.MOVIE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TvDimensions.posterHeightLarge.scaled(scale))
                )

                // Series metadata (Genre, Cast, Plot)
                seriesDetail.metadata.genre?.let { genre ->
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaAccent
                    )
                }

                seriesDetail.metadata.plot?.let { plot ->
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                        ),
                        color = CinemaTextSecondary,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right column: Season/Episode List + Selection Detail
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // If an episode is selected, show its detail panel
                if (selectedEpisode != null) {
                    EpisodeDetailPanel(
                        seriesId = seriesId,
                        seriesName = seriesDetail.name.ifEmpty { seriesName },
                        episode = selectedEpisode!!,
                        scale = scale,
                        onPlay = { extension, startFromBeginning ->
                            onEpisodeSelected(selectedEpisode!!.id, selectedEpisode!!.title, extension, startFromBeginning)
                        },
                        onClose = { selectedEpisode = null }
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
                }

                // Season accordion list
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = Spacing.xl.scaled(scale)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    seriesDetail.seasons.forEach { season ->
                        item(key = "season_${season.seasonNumber}") {
                            SeasonHeader(
                                season = season,
                                scale = scale
                            )
                        }

                        val seasonEpisodes = seriesDetail.episodes[season.seasonNumber.toString()] ?: emptyList()
                        items(seasonEpisodes, key = { it.id }) { episode ->
                            EpisodeItem(
                                episode = episode,
                                isSelected = selectedEpisode?.id == episode.id,
                                scale = scale,
                                onClick = { selectedEpisode = episode }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(
    season: SeasonInfo,
    scale: Float
) {
    Text(
        text = season.name,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)
        ),
        color = CinemaTextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md.scaled(scale), bottom = Spacing.xs.scaled(scale))
    )
}

@Composable
private fun EpisodeItem(
    episode: DomainEpisodeItem,
    isSelected: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusableNoScale(),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) CinemaAccent.copy(alpha = CinemaAlpha.focusedTint) else CinemaSurface,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.focusedTint)
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    TvFocusTokens.focusBorderWidth.scaled(scale),
                    CinemaAccent
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
        ) {
            // Episode number
            Box(
                modifier = Modifier
                    .size(32.dp.scaled(scale)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.episodeNumber.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                    ),
                    color = if (isFocused || isSelected) CinemaAccent else CinemaTextSecondary
                )
            }

            // Thumbnail
            CinemaThumbnail(
                url = episode.thumbnailUrl,
                fallbackLetter = null,
                contentType = ThumbnailContentType.DEFAULT,
                modifier = Modifier
                    .width(80.dp.scaled(scale))
                    .height(45.dp.scaled(scale))
            )

            // Title
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize.scaled(scale)
                ),
                color = if (isFocused || isSelected) CinemaTextPrimary else CinemaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Duration if available
            episode.metadata.duration?.let { duration ->
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                    ),
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textMedium)
                )
            }
        }
    }
}

@Composable
private fun EpisodeDetailPanel(
    seriesId: String,
    seriesName: String,
    episode: DomainEpisodeItem,
    scale: Float,
    onPlay: (extension: String, startFromBeginning: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(episode.id) {
        playButtonFocusRequester.requestFocus()
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Episode ${episode.episodeNumber}: ${episode.title}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize.scaled(scale)
                        ),
                        color = CinemaTextPrimary
                    )
                    episode.metadata.releaseDate?.let { date ->
                        Text(
                            text = "Aired: $date",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)
                            ),
                            color = CinemaAccentLight
                        )
                    }
                }
                
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Close",
                        tint = CinemaTextSecondary
                    )
                }
            }

            Text(
                text = episode.metadata.plot ?: "No description available.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)
                ),
                color = CinemaTextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
            ) {
                CinemaPrimaryButton(
                    onClick = { onPlay(episode.extension ?: "mp4", false) },
                    text = "▶ Play Episode",
                    modifier = Modifier.focusRequester(playButtonFocusRequester)
                )
                CinemaSecondaryButton(
                    onClick = { onPlay(episode.extension ?: "mp4", true) },
                    text = "Start Over"
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TvDimensions.progressIndicator),
                color = CinemaAccent
            )
            Text(
                text = "Loading episodes...",
                style = MaterialTheme.typography.titleLarge,
                color = CinemaTextSecondary
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier = Modifier.padding(Spacing.xl)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.displayMedium,
                color = CinemaError
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            CinemaPrimaryButton(
                onClick = onBack,
                text = "Back"
            )
        }
    }
}
