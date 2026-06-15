package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.network.xmltv.EpgFileManager
import org.njarasoa.fijerena.core.network.xmltv.EpgSearchPath
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.core.ui.components.MitadyLoading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgBrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (streamId: String, streamName: String, categoryId: String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel =
        viewModel(
            factory = remember { EpgBrowserViewModelFactory(context.applicationContext) },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val activeProviderName by viewModel.activeProviderName.collectAsStateWithLifecycle()
    val epgSearchHistory by viewModel.epgSearchHistory.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchMode) {
        searchQuery = ""
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val isDevMode = viewModel.isDevMode
    val sourceLabels by viewModel.sourceLabels.collectAsStateWithLifecycle()
    val oldestIngestedAtMs by viewModel.oldestEnabledIngestedAtMs.collectAsStateWithLifecycle()
    val staleSourceCount by viewModel.staleSourceCount.collectAsStateWithLifecycle()
    val processingState by viewModel.epgProcessingState.collectAsStateWithLifecycle()
    val epgDbStats =
        when (val idx = indexState) {
            is EpgIndexState.Indexed -> "${formatCount(idx.programmeCount)} progs, ${formatCount(idx.channelCount)} channels"
            else -> null
        }

    var matchedOnly by remember { mutableStateOf(true) }

    // Shared time tick drives "On Air" + freshness label recomputes
    var nowEpoch by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpoch = System.currentTimeMillis() / 1000L
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { snackbarHostState.showSnackbar(it.asString(context)) }
    }

    val isRefreshing =
        processingState is EpgFileManager.MultiSourceState.Pending ||
            processingState is EpgFileManager.MultiSourceState.Processing ||
            processingState is EpgFileManager.MultiSourceState.Finalizing

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (activeProviderName != null) {
                                "EPG Browser — $activeProviderName"
                            } else {
                                "EPG Browser"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val freshnessText =
                            freshnessLabel(oldestIngestedAtMs, nowEpoch, staleSourceCount)
                        val freshnessColor =
                            if (staleSourceCount > 0 || oldestIngestedAtMs == 0L) {
                                CinemaWarning
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        Text(
                            text = freshnessText,
                            style = MaterialTheme.typography.labelSmall,
                            color = freshnessColor,
                        )
                        if (isDevMode && epgDbStats != null) {
                            Text(
                                text = "EPG Index: $epgDbStats",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshStale() },
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh stale EPG sources",
                                tint =
                                    if (staleSourceCount > 0) {
                                        CinemaWarning
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Filters row: Radio buttons + Matched only checkbox
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Radio buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EpgBrowserViewModel.SearchMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .clickable { viewModel.setSearchMode(mode) }
                                    .padding(end = Spacing.sm),
                        ) {
                            RadioButton(
                                selected = searchMode == mode,
                                onClick = { viewModel.setSearchMode(mode) },
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text =
                                    when (mode) {
                                        EpgBrowserViewModel.SearchMode.PROGRAMME -> "Prog."
                                        EpgBrowserViewModel.SearchMode.CHANNEL -> "Chan."
                                    },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // Matched only checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { matchedOnly = !matchedOnly },
                ) {
                    Checkbox(
                        checked = matchedOnly,
                        onCheckedChange = { matchedOnly = it },
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = "Matched",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Search bar
            val placeholderText =
                when (searchMode) {
                    EpgBrowserViewModel.SearchMode.PROGRAMME -> "Search titles..."
                    EpgBrowserViewModel.SearchMode.CHANNEL -> "Search channels..."
                }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = CinemaSpacing.xs),
                placeholder = { Text(placeholderText) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    val hasResults = (uiState as? EpgBrowserViewModel.UiState.Results)?.totalPrograms ?: 0 > 0
                    if (searchQuery.isNotEmpty() || hasResults) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.performSearch(searchQuery)
                                keyboardController?.hide()
                            }
                        },
                    ),
                shape = RoundedCornerShape(CinemaCornerRadius.medium),
            )

            // Indexing progress banner
            val currentIndexState = indexState
            if (currentIndexState is EpgIndexState.Indexing || currentIndexState is EpgIndexState.Optimizing) {
                val idx = currentIndexState
                val progressText = if (idx is EpgIndexState.Indexing) {
                    "${idx.progressPercent}% (${formatCount(idx.programmesIndexed)})"
                } else {
                    "finalizing... (${formatCount((idx as EpgIndexState.Optimizing).programmeCount)})"
                }
                val progressValue = if (idx is EpgIndexState.Indexing) idx.progressPercent / 100f else 0.95f

                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = CinemaSpacing.xxs,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (idx is EpgIndexState.Indexing) "Indexing..." else "Optimizing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .padding(top = 2.dp),
                    )
                }
            }

            when (val state = uiState) {
                is EpgBrowserViewModel.UiState.Idle,
                is EpgBrowserViewModel.UiState.Indexing,
                -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (epgSearchHistory.isNotEmpty()) {
                            MobileEpgSearchHistorySection(
                                history = epgSearchHistory,
                                onItemClick = { term ->
                                    searchQuery = term
                                    viewModel.performSearch(term)
                                    keyboardController?.hide()
                                },
                                onItemRemove = { viewModel.removeEpgSearchHistoryEntry(it) },
                                onClearAll = { viewModel.clearEpgSearchHistory() },
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val hintText =
                                when (searchMode) {
                                    EpgBrowserViewModel.SearchMode.PROGRAMME -> "Search programme titles"
                                    EpgBrowserViewModel.SearchMode.CHANNEL -> "Search by channel name"
                                }
                            Text(
                                text = hintText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                is EpgBrowserViewModel.UiState.NoEpgFile -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No EPG file available.\nConfigure an EPG URL in Settings.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is EpgBrowserViewModel.UiState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MitadyLoading(
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is EpgBrowserViewModel.UiState.Results -> {
                    MobileResultsContent(
                        results = state,
                        nowEpoch = nowEpoch,
                        isDevMode = isDevMode,
                        sourceLabels = sourceLabels,
                        searchMode = searchMode,
                        matchedOnly = matchedOnly,
                        onMatchedOnlyChange = { matchedOnly = it },
                        onNavigateToPlayer = onNavigateToPlayer,
                    )
                }
                is EpgBrowserViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileResultsContent(
    results: EpgBrowserViewModel.UiState.Results,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    searchMode: EpgBrowserViewModel.SearchMode = EpgBrowserViewModel.SearchMode.PROGRAMME,
    matchedOnly: Boolean = true,
    onMatchedOnlyChange: (Boolean) -> Unit = {},
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
) {
    // Filter date groups when hiding unmatched channels
    val displayDateGroups =
        remember(results.dateGroups, matchedOnly) {
            if (matchedOnly) {
                results.dateGroups.mapNotNull { group ->
                    val filteredPrograms =
                        group.programs.mapNotNull { program ->
                            val matchedAirings = program.airings.filter { it.matchedStream != null }
                            if (matchedAirings.isEmpty()) {
                                null
                            } else {
                                program.copy(airings = matchedAirings)
                            }
                        }
                    if (filteredPrograms.isEmpty()) {
                        null
                    } else {
                        group.copy(programs = filteredPrograms)
                    }
                }
            } else {
                results.dateGroups
            }
        }

    Column {
        // Stats row
        val timeStr = "%.1f".format(results.searchTimeMs / 1000.0)
        val truncatedSuffix = if (results.truncated) " (truncated)" else ""
        val sourceSuffix = when {
            !results.searchedFromIndex -> " [XML scan]"
            else -> when (results.searchPath) {
                EpgSearchPath.FTS_PHRASE -> " [FTS phrase]"
                EpgSearchPath.FTS_AND -> " [FTS AND]"
                EpgSearchPath.NONE -> " [indexed]"
            }
        }
        Text(
            text = "${results.totalPrograms} programs (${results.totalAirings} airings) — ${timeStr}s$truncatedSuffix$sourceSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = Spacing.md,
                    vertical = CinemaSpacing.xxs,
                ),
        )

        if (displayDateGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (matchedOnly) {
                            "No matched results for '${results.query}'"
                        } else {
                            "No results found for '${results.query}'"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.md),
            ) {
                displayDateGroups.forEach { dateGroup ->
                    stickyHeader(key = "date::${dateGroup.dateLabel}::${dateGroup.dayStartEpoch}::$matchedOnly", contentType = "header") {
                        MobileDateHeader(dateLabel = dateGroup.dateLabel)
                    }
                    items(
                        dateGroup.programs,
                        key = { it.id },
                        contentType = { "program" },
                    ) { program ->
                        MobileProgramCard(
                            program = program,
                            nowEpoch = nowEpoch,
                            isDevMode = isDevMode,
                            sourceLabels = sourceLabels,
                            onNavigateToPlayer = onNavigateToPlayer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileDateHeader(dateLabel: String) {
    Text(
        text = dateLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = CinemaSpacing.sm),
    )
}

@Composable
private fun MobileProgramCard(
    program: EpgBrowserProgram,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
) {
    var expanded by remember { mutableStateOf(false) }
    val showExpander = program.airings.size > 3
    var pendingConfirmAiring by remember { mutableStateOf<EpgBrowserAiring?>(null) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CinemaSpacing.md),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).bounceMarquee(),
                )
                val category = program.category
                if (category != null) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = CinemaSpacing.sm),
                    )
                }
            }

            // Description
            val description = program.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = CinemaSpacing.xs),
                )
            }

            // Airings
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            val visibleAirings =
                if (expanded || !showExpander) {
                    program.airings
                } else {
                    program.airings.take(3)
                }
            visibleAirings.forEach { airing ->
                MobileAiringRow(
                    airing = airing,
                    nowEpoch = nowEpoch,
                    isDevMode = isDevMode,
                    sourceLabels = sourceLabels,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onRequestConfirmation = { pendingConfirmAiring = it },
                )
            }

            // Confirmation dialog for non-ON-AIR matched airings
            val pending = pendingConfirmAiring
            if (pending != null) {
                val matched = pending.matchedStream!!
                val airingContext = LocalContext.current
                AlertDialog(
                    onDismissRequest = { pendingConfirmAiring = null },
                    title = { Text("Watch now?") },
                    text = {
                        Text(
                            "This show airs at ${formatAiringTime(
                                airingContext,
                                pending.startEpoch,
                                pending.endEpoch,
                            )}.\nWatch ${pending.channelName} now?",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingConfirmAiring = null
                            onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                        }) { Text("Watch now") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingConfirmAiring = null }) { Text("Cancel") }
                    },
                )
            }

            // Expand/collapse toggle
            if (showExpander) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(top = CinemaSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more",
                        modifier = Modifier.size(CinemaSpacing.lg),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (expanded) "Show less" else "${program.airings.size - 3} more airings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileAiringRow(
    airing: EpgBrowserAiring,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> },
    onRequestConfirmation: (EpgBrowserAiring) -> Unit = {},
) {
    val isOnAir = nowEpoch >= airing.startEpoch && nowEpoch < airing.endEpoch
    val isSoon = !isOnAir && airing.startEpoch > nowEpoch && (airing.startEpoch - nowEpoch) <= 7200L
    val isMatched = airing.matchedStream != null

    val onClick =
        remember(isMatched, isOnAir, airing, onNavigateToPlayer, onRequestConfirmation) {
            if (!isMatched) {
                null
            } else {
                {
                    val matched = airing.matchedStream!!
                    if (isOnAir) {
                        onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                    } else {
                        onRequestConfirmation(airing)
                    }
                }
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                ).alpha(if (isMatched) 1f else 0.5f)
                .padding(vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMatched) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Watch",
                modifier = Modifier.size(CinemaSpacing.lg),
                tint = if (isOnAir) CinemaSuccess else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = airing.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).bounceMarquee(),
        )
        if (isDevMode && airing.sourceId > 0) {
            val sourceName = sourceLabels[airing.sourceId]
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CinemaAlpha.textLow),
                )
            }
        }
        if (isOnAir || isSoon) {
            val badgeColor = if (isOnAir) CinemaSuccess else CinemaWarning
            val badgeLabel = if (isOnAir) "ON AIR" else "SOON"
            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = CinemaBackground,
                modifier =
                    Modifier
                        .background(badgeColor, RoundedCornerShape(CinemaCornerRadius.small))
                        .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs),
            )
        }
        val airingContext = LocalContext.current
        Text(
            text = formatAiringTime(airingContext, airing.startEpoch, airing.endEpoch),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (isOnAir) {
                    CinemaSuccess
                } else if (isSoon) {
                    CinemaWarning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private fun formatAiringTime(
    context: android.content.Context,
    startEpoch: Long,
    endEpoch: Long,
): String {
    val startText =
        org.njarasoa.fijerena.core.player.model.TimeFormat
            .formatTime(context, startEpoch)
    val endText =
        org.njarasoa.fijerena.core.player.model.TimeFormat
            .formatTime(context, endEpoch)
    return "$startText – $endText"
}

private fun formatFileSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileEpgSearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent Searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
            IconButton(onClick = onClearAll) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Clear all",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            history.forEach { term ->
                AssistChip(
                    onClick = { onItemClick(term) },
                    label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onItemRemove(term) },
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun formatCount(count: Int): String =
    when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }

private fun freshnessLabel(
    oldestIngestedAtMs: Long?,
    nowEpoch: Long,
    staleSourceCount: Int,
): String {
    if (oldestIngestedAtMs == null) return "No EPG sources"
    if (oldestIngestedAtMs == 0L) return "Never refreshed"
    val ageSec = nowEpoch - oldestIngestedAtMs / 1000L
    val ageLabel =
        when {
            ageSec < 60 -> "just now"
            ageSec < 3600 -> "${ageSec / 60}m ago"
            ageSec < 86_400 -> "${ageSec / 3600}h ago"
            else -> "${ageSec / 86_400}d ago"
        }
    val suffix = if (staleSourceCount > 0) " • $staleSourceCount stale" else ""
    return "Updated $ageLabel$suffix"
}
