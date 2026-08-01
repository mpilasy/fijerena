@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ShimmerPlaceholder
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaLive
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.navigation.ContentType as NavContentType
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

/**
 * Content type selection screen with icons, category counts, and gradient cards.
 */
@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (NavContentType) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {},
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember {
        mutableStateOf<Set<String>>(
            setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
        )
    }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<org.njarasoa.fijerena.core.network.provider.ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Category counts per content type: Pair(filtered, total) — null while loading
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Stash provider ref so we can load counts
    var mediaProviderRef by remember { mutableStateOf<MediaProvider?>(null) }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }

    // Show EPG Browser button when EPG index has data. Collected live (not a one-shot
    // `remember`) so a source that finishes indexing while this screen is on-screen shows the
    // icon immediately, instead of waiting for the composable to be torn down and rebuilt.
    val epgIndexState by remember {
        org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer.getInstance(context.applicationContext).state
    }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.Indexed

    LaunchedEffect(refreshTrigger) {
        // Reset counts so stale values don't linger during provider switch
        liveTvCounts = null
        moviesCounts = null
        tvShowsCounts = null
        val resolvedTypes =
            withContext(Dispatchers.IO) {
                val providerRepo = ProviderRepository(context.applicationContext)
                allProviders = providerRepo.getAllProvidersList()
                val activeProvider = providerRepo.getActiveProvider()
                if (activeProvider != null) {
                    providerName = activeProvider.name
                    providerType = activeProvider.type
                    activeProviderId = activeProvider.id
                    val password = providerRepo.getPassword(activeProvider.id) ?: ""
                    val mediaProvider = MediaProviderFactory.create(activeProvider, context.applicationContext, password)
                    supportedContentTypes = mediaProvider.capabilities.supportedContentTypes
                    mediaProviderRef = mediaProvider
                    mediaProvider.capabilities.supportedContentTypes
                } else {
                    providerName = appSettings.providerName
                    null
                }
            }
        resolvedTypes?.let(onCapabilitiesResolved)
    }

    // Pull a recently-watched poster for the ambient backdrop wash — falls back to the plain
    // gradient (AmbientBackdrop's default) if there's no watch history yet or the provider
    // doesn't support it.
    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (contentType in listOf(ContentType.MOVIES, ContentType.TV_SHOWS, ContentType.LIVE_TV)) {
                val poster =
                    mp.getRecentlyPlayed(contentType)
                        ?.getOrNull()
                        ?.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                        ?.thumbnailUrl
                if (poster != null) {
                    backdropImageUrl = poster
                    break
                }
            }
        }
    }

    // Load category counts in the background once provider is ready
    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        // getCategories() already excludes filtered-out categories at the DB layer, so its size
        // IS the visible count — the total (for "X of Y") needs the unfiltered count separately.
        val xtream = mp as? org.njarasoa.fijerena.core.network.XtreamMediaProvider
        withContext(Dispatchers.IO) {
            if (ContentType.LIVE_TV in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.LIVE_TV).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.LIVE_TV) ?: cats.size
                    liveTvCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.MOVIES in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.MOVIES).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.MOVIES) ?: cats.size
                    moviesCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.TV_SHOWS in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.TV_SHOWS).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.TV_SHOWS) ?: cats.size
                    tvShowsCounts = Pair(cats.size, total)
                }
            }
        }
    }

    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
        val scale = LocalUiScale.current

        Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = backdropImageUrl)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with provider name in glass pill
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.xxl.scaled(scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "fijerena",
                        style =
                            MaterialTheme.typography.displayMedium.copy(
                                fontSize =
                                    MaterialTheme.typography.displayMedium.fontSize
                                        .scaled(scale),
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.staggeredEntrance(0),
                    )
                    // Provider name in glass pill badge + settings gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (allProviders.size > 1) {
                            val displayName =
                                if (appSettings.isDevMode && providerType.isNotEmpty()) {
                                    "$providerName ($providerType)"
                                } else {
                                    providerName
                                }
                            var providerPillFocused by remember { mutableStateOf(false) }
                            val pillScale by animateFloatAsState(
                                targetValue = if (providerPillFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                                animationSpec = tween(durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs),
                                label = "provider_pill_scale",
                            )
                            GlassPanel(
                                modifier =
                                    Modifier
                                        .scale(pillScale)
                                        .border(
                                            width = TvFocusTokens.focusBorderWidth,
                                            color = if (providerPillFocused) CinemaAccentLight else androidx.compose.ui.graphics.Color.Transparent,
                                            shape = RoundedCornerShape(CinemaCornerRadius.large),
                                        ).onFocusChanged { providerPillFocused = it.isFocused }
                                        .clickable(role = Role.DropdownList) { showProviderPicker = true }
                                        .semantics {
                                            contentDescription = "Switch Provider, current provider: $displayName"
                                        },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = Spacing.md,
                                            vertical = Spacing.xs,
                                        ),
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                    )
                                    Icon(
                                        imageVector = CinemaIcons.ArrowDropDown,
                                        contentDescription = null,
                                        tint = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                            }
                        }
                        if (hasEpgData) {
                            CinemaIconButton(
                                onClick = onEpgBrowser,
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.MenuBook,
                                        contentDescription = "EPG Browser",
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                        }
                        CinemaIconButton(
                            onClick = onSearch,
                            icon = {
                                Icon(
                                    imageVector = CinemaIcons.Search,
                                    contentDescription = "Search All",
                                    tint = CinemaTextPrimary,
                                )
                            },
                        )
                        CinemaIconButton(
                            onClick = onSettings,
                            icon = {
                                Icon(
                                    imageVector = CinemaIcons.Settings,
                                    contentDescription = "Settings",
                                    tint = CinemaTextPrimary,
                                )
                            },
                        )
                    }
                }

                // Content type hero cards
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val isDevMode = appSettings.isDevMode
                        var cardIndex = 1
                        if (ContentType.LIVE_TV in supportedContentTypes) {
                            ContentTypeHeroCard(
                                title = "Live TV",
                                subtitle = "Watch live channels",
                                icon = CinemaIcons.LiveTv,
                                categoryCounts = liveTvCounts,
                                showTotal = isDevMode,
                                showLivePulse = true,
                                gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                                onClick = { onContentTypeSelected(NavContentType.LIVE_TV) },
                                modifier = Modifier.weight(1f).staggeredEntrance(cardIndex++),
                            )
                        }

                        if (ContentType.MOVIES in supportedContentTypes) {
                            ContentTypeHeroCard(
                                title = "Movies",
                                subtitle = "Browse on-demand",
                                icon = CinemaIcons.Movie,
                                categoryCounts = moviesCounts,
                                showTotal = isDevMode,
                                gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                                onClick = { onContentTypeSelected(NavContentType.MOVIES) },
                                modifier = Modifier.weight(1f).staggeredEntrance(cardIndex++),
                            )
                        }

                        if (ContentType.TV_SHOWS in supportedContentTypes) {
                            ContentTypeHeroCard(
                                title = "TV Shows",
                                subtitle = "Series & episodes",
                                icon = CinemaIcons.Tv,
                                categoryCounts = tvShowsCounts,
                                showTotal = isDevMode,
                                gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                                onClick = { onContentTypeSelected(NavContentType.TV_SHOWS) },
                                modifier = Modifier.weight(1f).staggeredEntrance(cardIndex++),
                            )
                        }
                    }
                }
            }

            // Provider picker dialog
            if (showProviderPicker && allProviders.size > 1) {
                val coroutineScope = rememberCoroutineScope()
                CinemaAlertDialog(
                    onDismissRequest = { showProviderPicker = false },
                    containerColor = CinemaSurface,
                    titleContentColor = CinemaTextPrimary,
                    textContentColor = CinemaTextSecondary,
                    title = { androidx.compose.material3.Text("Switch Provider") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            allProviders.forEach { provider ->
                                val isActive = provider.id == activeProviderId
                                val label =
                                    if (appSettings.isDevMode) {
                                        "${provider.name} (${provider.type})"
                                    } else {
                                        provider.name
                                    }
                                androidx.compose.material3.Surface(
                                    onClick = {
                                        if (!isActive) {
                                            coroutineScope.launch {
                                                val providerRepo = ProviderRepository(context.applicationContext)
                                                providerRepo.setActiveProvider(provider.id)
                                                showProviderPicker = false
                                                refreshTrigger++
                                                onProviderChanged()
                                            }
                                        } else {
                                            showProviderPicker = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color =
                                        if (isActive) {
                                            CinemaAccent.copy(alpha = CinemaAlpha.focusedTint)
                                        } else {
                                            CinemaSurfaceVariant
                                        },
                                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.md),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = label,
                                            color = if (isActive) CinemaAccent else CinemaTextPrimary,
                                        )
                                        if (isActive) {
                                            androidx.compose.material3.Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = CinemaAccent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        CinemaDialogTextButton(onClick = { showProviderPicker = false }) {
                            androidx.compose.material3.Text("Close", color = CinemaAccent)
                        }
                    },
                )
            }
        }
        }
    } // CompositionLocalProvider
}

/**
 * Hero card with gradient background, icon, and category count.
 */
@Composable
private fun ContentTypeHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    categoryCounts: Pair<Int, Int>?,
    showTotal: Boolean = false,
    showLivePulse: Boolean = false,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    Card(
        onClick = onClick,
        modifier = modifier.height(TvDimensions.contentTypeCardHeight.scaled(scale)),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.xLarge)),
        border =
            CardDefaults.border(
                focusedBorder =
                    Border(
                        border =
                            BorderStroke(
                                TvFocusTokens.focusBorderWidth,
                                CinemaTextPrimary,
                            ),
                        shape = RoundedCornerShape(CinemaCornerRadius.xLarge),
                    ),
            ),
    ) {
        val brush = remember(gradientColors) { Brush.verticalGradient(colors = gradientColors) }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush = brush,
                        shape = RoundedCornerShape(CinemaCornerRadius.xLarge),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Spacing.md),
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CinemaTextPrimary,
                        modifier = Modifier.size(TvDimensions.contentTypeIconSize.scaled(scale)),
                    )
                    if (showLivePulse) {
                        val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.3f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(CinemaAnimation.shimmerDurationMs),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "live_pulse_alpha",
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(TvDimensions.liveDotSize.scaled(scale))
                                    .border(TvDimensions.borderThin, CinemaTextPrimary.copy(alpha = pulseAlpha), CircleShape)
                                    .background(CinemaLive.copy(alpha = pulseAlpha), shape = CircleShape),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontSize =
                                MaterialTheme.typography.headlineMedium.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize =
                                MaterialTheme.typography.bodyLarge.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xxs.scaled(scale)),
                )
                if (categoryCounts == null) {
                    ShimmerPlaceholder(
                        modifier =
                            Modifier
                                .padding(top = Spacing.xs)
                                .size(width = TvDimensions.contentTypeIconSize.scaled(scale), height = Spacing.md)
                                .clip(RoundedCornerShape(CinemaCornerRadius.small)),
                    )
                } else {
                    val (filtered, total) = categoryCounts
                    val countText =
                        if (showTotal && filtered < total) {
                            "$filtered of $total categories"
                        } else {
                            "$filtered categories"
                        }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelLarge,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}
