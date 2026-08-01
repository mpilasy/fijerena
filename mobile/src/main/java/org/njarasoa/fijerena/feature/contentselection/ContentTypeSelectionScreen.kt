package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.ShimmerPlaceholder
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaLive
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileContentTypeSelectionScreen(
    onContentTypeSelected: (contentType: String) -> Unit,
    onSettings: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {},
    onSearch: () -> Unit = {},
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember {
        mutableStateOf<Set<String>>(
            setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
        )
    }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Category counts per content type: Pair(filtered, total) — null while loading
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mediaProviderRef by remember { mutableStateOf<org.njarasoa.fijerena.core.player.domain.MediaProvider?>(null) }
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
        val xtream = mp as? XtreamMediaProvider
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

    Box(modifier = Modifier.fillMaxSize()) {
    AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = backdropImageUrl)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val displayName =
                buildString {
                    append(providerName.ifEmpty { "fijerena" })
                    if (appSettings.isDevMode && providerType.isNotEmpty()) {
                        append(" ($providerType)")
                    }
                }
            TopAppBar(
                title = {
                    // Only render as a dropdown when there's actually something to switch to —
                    // otherwise this is a dead tap: the picker dialog below only ever opens when
                    // allProviders.size > 1, but the arrow/click target used to show regardless.
                    if (allProviders.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .clickable(role = Role.DropdownList) { showProviderPicker = true }
                                    .semantics {
                                        contentDescription = "Switch Provider, current provider: $displayName"
                                    }.padding(end = CinemaSpacing.xs, top = CinemaSpacing.xs, bottom = CinemaSpacing.xs),
                        ) {
                            Text(
                                text = displayName,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = CinemaIcons.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    if (hasEpgData) {
                        CinemaIconButton(onClick = onEpgBrowser,
                            icon = {
                                Icon(CinemaIcons.MenuBook, "EPG Browser", tint = CinemaTextPrimary)
                            }
                        )
                    }
                    CinemaIconButton(onClick = onSearch,
                        icon = {
                            Icon(CinemaIcons.Search, "Search", tint = CinemaTextPrimary)
                        }
                    )
                    CinemaIconButton(onClick = onSettings,
                        icon = {
                            Icon(CinemaIcons.Settings, "Settings", tint = CinemaTextPrimary)
                        }
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(CinemaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Select Content Type",
                style = MaterialTheme.typography.headlineMedium,
                modifier =
                    Modifier
                        .padding(bottom = CinemaSpacing.lg)
                        .staggeredEntrance(0),
            )

            val isDevMode = appSettings.isDevMode
            var cardIndex = 1
            if (ContentType.LIVE_TV in supportedContentTypes) {
                GradientContentCard(
                    title = "Live TV",
                    description = "Watch live television channels",
                    icon = CinemaIcons.LiveTv,
                    categoryCounts = liveTvCounts,
                    showTotal = isDevMode,
                    showLivePulse = true,
                    gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                    onClick = { onContentTypeSelected(ContentType.LIVE_TV) },
                    modifier = Modifier.staggeredEntrance(cardIndex++),
                )
            }

            if (ContentType.MOVIES in supportedContentTypes) {
                GradientContentCard(
                    title = "Movies",
                    description = "Browse on-demand movies",
                    icon = CinemaIcons.Movie,
                    categoryCounts = moviesCounts,
                    showTotal = isDevMode,
                    gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                    onClick = { onContentTypeSelected(ContentType.MOVIES) },
                    modifier = Modifier.staggeredEntrance(cardIndex++),
                )
            }

            if (ContentType.TV_SHOWS in supportedContentTypes) {
                GradientContentCard(
                    title = "TV Shows",
                    description = "Watch series and episodes",
                    icon = CinemaIcons.Tv,
                    categoryCounts = tvShowsCounts,
                    showTotal = isDevMode,
                    gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                    onClick = { onContentTypeSelected(ContentType.TV_SHOWS) },
                    modifier = Modifier.staggeredEntrance(cardIndex++),
                )
            }
        }
    }
    }

    // Provider picker dialog
    if (showProviderPicker && allProviders.size > 1) {
        CinemaAlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text("Switch Provider") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    allProviders.forEach { provider ->
                        val isActive = provider.id == activeProviderId
                        val label =
                            if (appSettings.isDevMode) {
                                "${provider.name} (${provider.type})"
                            } else {
                                provider.name
                            }
                        Surface(
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
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(CinemaSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (isActive) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                CinemaDialogTextButton(onClick = { showProviderPicker = false }) {
                    Text("Close")
                }
            },
        )
    }
}

/**
 * Full-width gradient card for content type selection (mobile).
 */
@Composable
private fun GradientContentCard(
    title: String,
    description: String,
    icon: ImageVector,
    categoryCounts: Pair<Int, Int>?,
    showTotal: Boolean = false,
    showLivePulse: Boolean = false,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(MobileDimensions.contentTypeCardHeight),
        shape = RoundedCornerShape(CinemaCornerRadius.large),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(colors = gradientColors),
                        shape = RoundedCornerShape(CinemaCornerRadius.large),
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(CinemaSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(MobileDimensions.iconLarge),
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
                                        .size(MobileDimensions.liveDotSize)
                                        .border(MobileDimensions.dividerThin, CinemaTextPrimary.copy(alpha = pulseAlpha), CircleShape)
                                        .background(CinemaLive.copy(alpha = pulseAlpha), shape = CircleShape),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = CinemaTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                        )
                    }
                }
                if (categoryCounts == null) {
                    ShimmerPlaceholder(
                        modifier =
                            Modifier
                                .size(width = MobileDimensions.iconLarge, height = CinemaSpacing.lg)
                                .clip(RoundedCornerShape(CinemaCornerRadius.small)),
                    )
                } else {
                    val (filtered, total) = categoryCounts
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$filtered",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                            fontWeight = FontWeight.Bold,
                        )
                        if (showTotal && filtered < total) {
                            Text(
                                text = "of $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                            )
                        }
                    }
                }
            }
        }
    }
}
