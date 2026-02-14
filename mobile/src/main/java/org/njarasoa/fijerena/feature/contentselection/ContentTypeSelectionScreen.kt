package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileContentTypeSelectionScreen(
    onContentTypeSelected: (contentType: String) -> Unit,
    onSettings: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember { mutableStateOf<Set<String>>(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Category counts per content type: Pair(filtered, total) — null while loading
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mediaProviderRef by remember { mutableStateOf<org.njarasoa.fijerena.core.player.domain.MediaProvider?>(null) }
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

    Scaffold(
        topBar = {
            val displayName = buildString {
                append(providerName.ifEmpty { "Fijerena" })
                if (appSettings.isDevMode && providerType.isNotEmpty()) {
                    append(" ($providerType)")
                }
            }
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        modifier = Modifier.clickable { showProviderPicker = true },
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (hasEpgData) {
                        IconButton(onClick = onEpgBrowser) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, "EPG Browser")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Content Type",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = CinemaSpacing.lg)
            )

            val isDevMode = appSettings.isDevMode
            if ("LIVE_TV" in supportedContentTypes) {
                GradientContentCard(
                    title = "Live TV",
                    description = "Watch live television channels",
                    categoryCounts = liveTvCounts,
                    showTotal = isDevMode,
                    gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                    onClick = { onContentTypeSelected("LIVE_TV") }
                )
            }

            if ("MOVIES" in supportedContentTypes) {
                GradientContentCard(
                    title = "Movies",
                    description = "Browse on-demand movies",
                    categoryCounts = moviesCounts,
                    showTotal = isDevMode,
                    gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                    onClick = { onContentTypeSelected("MOVIES") }
                )
            }

            if ("TV_SHOWS" in supportedContentTypes) {
                GradientContentCard(
                    title = "TV Shows",
                    description = "Watch series and episodes",
                    categoryCounts = tvShowsCounts,
                    showTotal = isDevMode,
                    gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                    onClick = { onContentTypeSelected("TV_SHOWS") }
                )
            }
        }
    }

    // Provider picker dialog
    if (showProviderPicker && allProviders.size > 1) {
        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text("Switch Provider") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
                ) {
                    allProviders.forEach { provider ->
                        val isActive = provider.id == activeProviderId
                        val label = if (appSettings.isDevMode) {
                            "${provider.name} (${provider.type})"
                        } else provider.name
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
                            color = if (isActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CinemaSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isActive) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text("Close")
                }
            }
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
    categoryCounts: Pair<Int, Int>?,
    showTotal: Boolean = false,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MobileDimensions.contentTypeCardHeight),
        shape = RoundedCornerShape(CinemaCornerRadius.large)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(colors = gradientColors),
                    shape = RoundedCornerShape(CinemaCornerRadius.large)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = CinemaTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium)
                    )
                }
                if (categoryCounts != null) {
                    val (filtered, total) = categoryCounts
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$filtered",
                            style = MaterialTheme.typography.headlineMedium,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                            fontWeight = FontWeight.Bold
                        )
                        if (showTotal && filtered < total) {
                            Text(
                                text = "of $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow)
                            )
                        }
                    }
                }
            }
        }
    }
}
