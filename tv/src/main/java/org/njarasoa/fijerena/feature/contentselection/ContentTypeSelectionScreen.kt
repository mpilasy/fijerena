@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import androidx.compose.runtime.CompositionLocalProvider
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Content type selection screen with icons, category counts, and gradient cards.
 */
@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (ContentType) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember { mutableStateOf<Set<String>>(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<org.njarasoa.fijerena.core.network.provider.ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Category counts per content type: Pair(filtered, total) — null while loading
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Stash provider ref + filters so we can load counts
    var mediaProviderRef by remember { mutableStateOf<MediaProvider?>(null) }
    var categoryFilters by remember {
        mutableStateOf(org.njarasoa.fijerena.core.network.provider.CategoryFilters())
    }

    // Show EPG Browser button when EPG index has data
    val hasEpgData = remember {
        org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
            .getInstance(context.applicationContext).state.value is
            org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.Indexed
    }


    LaunchedEffect(refreshTrigger) {
        // Reset counts so stale values don't linger during provider switch
        liveTvCounts = null
        moviesCounts = null
        tvShowsCounts = null
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
                categoryFilters = providerRepo.getProviderSettings(activeProvider.id).categoryFilters
                mediaProviderRef = mediaProvider
            } else {
                providerName = appSettings.providerName
            }
        }
    }

    // Load category counts in the background once provider is ready
    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        val filters = categoryFilters
        val hasFilters = filters.prefixes.isNotEmpty() || filters.allowedScripts.isNotEmpty()
        withContext(Dispatchers.IO) {
            if ("LIVE_TV" in mp.capabilities.supportedContentTypes) {
                mp.getCategories("LIVE_TV").onSuccess { cats ->
                    val filtered = if (hasFilters) cats.count { filters.shouldShowCategory(it.name) } else cats.size
                    liveTvCounts = Pair(filtered, cats.size)
                }
            }
            if ("MOVIES" in mp.capabilities.supportedContentTypes) {
                mp.getCategories("MOVIES").onSuccess { cats ->
                    val filtered = if (hasFilters) cats.count { filters.shouldShowCategory(it.name) } else cats.size
                    moviesCounts = Pair(filtered, cats.size)
                }
            }
            if ("TV_SHOWS" in mp.capabilities.supportedContentTypes) {
                mp.getCategories("TV_SHOWS").onSuccess { cats ->
                    val filtered = if (hasFilters) cats.count { filters.shouldShowCategory(it.name) } else cats.size
                    tvShowsCounts = Pair(filtered, cats.size)
                }
            }
        }
    }

    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
    val scale = LocalUiScale.current

    // Subtle background gradient wash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CinemaBackground,
                        CinemaAccentDark.copy(alpha = CinemaAlpha.ghost),
                        CinemaBackground
                    )
                )
            )
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with provider name in glass pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl.scaled(scale)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = providerName.ifEmpty { "Fijerena" },
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale)
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Provider name in glass pill badge + settings gear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (allProviders.size > 1) {
                        val displayName = if (appSettings.isDevMode && providerType.isNotEmpty()) {
                            "$providerName ($providerType)"
                        } else providerName
                        var providerPillFocused by remember { mutableStateOf(false) }
                        val pillScale by animateFloatAsState(
                            targetValue = if (providerPillFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                            animationSpec = tween(durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs),
                            label = "provider_pill_scale"
                        )
                        GlassPanel(
                            modifier = Modifier
                                .scale(pillScale)
                                .border(
                                    width = TvFocusTokens.focusBorderWidth,
                                    color = if (providerPillFocused) CinemaAccentLight else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(CinemaCornerRadius.large)
                                )
                                .onFocusChanged { providerPillFocused = it.isFocused }
                                .clickable { showProviderPicker = true }
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.md,
                                    vertical = Spacing.xs
                                )
                            )
                        }
                    }
                    if (hasEpgData) {
                        CinemaIconButton(
                            onClick = onEpgBrowser,
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "EPG Browser",
                                    tint = CinemaAccentLight
                                )
                            }
                        )
                    }
                    CinemaIconButton(
                        onClick = onSearch,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search All",
                                tint = CinemaAccentLight
                            )
                        }
                    )
                    CinemaIconButton(
                        onClick = onSettings,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = CinemaAccentLight
                            )
                        }
                    )
                }
            }

            // Content type hero cards
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isDevMode = appSettings.isDevMode
                    if ("LIVE_TV" in supportedContentTypes) {
                        ContentTypeHeroCard(
                            title = "Live TV",
                            subtitle = "Watch live channels",
                            icon = Icons.Default.LiveTv,
                            categoryCounts = liveTvCounts,
                            showTotal = isDevMode,
                            gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                            onClick = { onContentTypeSelected(ContentType.LIVE_TV) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if ("MOVIES" in supportedContentTypes) {
                        ContentTypeHeroCard(
                            title = "Movies",
                            subtitle = "Browse on-demand",
                            icon = Icons.Default.Movie,
                            categoryCounts = moviesCounts,
                            showTotal = isDevMode,
                            gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                            onClick = { onContentTypeSelected(ContentType.MOVIES) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if ("TV_SHOWS" in supportedContentTypes) {
                        ContentTypeHeroCard(
                            title = "TV Shows",
                            subtitle = "Series & episodes",
                            icon = Icons.Default.Tv,
                            categoryCounts = tvShowsCounts,
                            showTotal = isDevMode,
                            gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                            onClick = { onContentTypeSelected(ContentType.TV_SHOWS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Provider picker dialog
        if (showProviderPicker && allProviders.size > 1) {
            val coroutineScope = rememberCoroutineScope()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showProviderPicker = false },
                containerColor = CinemaSurface,
                titleContentColor = CinemaTextPrimary,
                textContentColor = CinemaTextSecondary,
                title = { androidx.compose.material3.Text("Switch Provider") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        allProviders.forEach { provider ->
                            val isActive = provider.id == activeProviderId
                            val label = if (appSettings.isDevMode) {
                                "${provider.name} (${provider.type})"
                            } else provider.name
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
                                color = if (isActive)
                                    CinemaAccent.copy(alpha = CinemaAlpha.focusedTint)
                                else
                                    CinemaSurfaceVariant,
                                shape = RoundedCornerShape(CinemaCornerRadius.small)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Text(
                                        text = label,
                                        color = if (isActive) CinemaAccent else CinemaTextPrimary
                                    )
                                    if (isActive) {
                                        androidx.compose.material3.Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CinemaAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showProviderPicker = false }) {
                        androidx.compose.material3.Text("Close", color = CinemaAccent)
                    }
                }
            )
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
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalUiScale.current
    Card(
        onClick = onClick,
        modifier = modifier.height(TvDimensions.contentTypeCardHeight.scaled(scale)),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaSurface,
            focusedContentColor = CinemaTextPrimary
        ),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleSubtle,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.xLarge)),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    TvFocusTokens.focusBorderWidth,
                    CinemaTextPrimary
                ),
                shape = RoundedCornerShape(CinemaCornerRadius.xLarge)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(colors = gradientColors),
                    shape = RoundedCornerShape(CinemaCornerRadius.xLarge)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Spacing.md)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CinemaTextPrimary,
                    modifier = Modifier.size(TvDimensions.contentTypeIconSize.scaled(scale))
                )
                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize.scaled(scale)
                    ),
                    color = CinemaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize.scaled(scale)
                    ),
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xxs.scaled(scale))
                )
                if (categoryCounts != null) {
                    val (filtered, total) = categoryCounts
                    val countText = if (showTotal && filtered < total) {
                        "$filtered of $total categories"
                    } else {
                        "$filtered categories"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelLarge,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
}
