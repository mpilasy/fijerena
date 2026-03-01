package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import org.njarasoa.fijerena.core.ui.components.bounceMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.alpha
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserMatchedStream
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserDateGroup
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaSuccess
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgBrowserScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: (streamId: String, streamName: String, categoryId: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel = viewModel(
        factory = remember { EpgBrowserViewModelFactory(context.applicationContext) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indexState by viewModel.indexState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDevMode = viewModel.isDevMode
    val sourceLabels by viewModel.sourceLabels.collectAsStateWithLifecycle()
    val epgDbStats = when (val idx = indexState) {
        is EpgIndexState.Indexed -> "${idx.programmeCount} progs, ${idx.channelCount} channels"
        else -> null
    }

    // Shared time tick to keep "On Air" status fresh
    var nowEpoch by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowEpoch = System.currentTimeMillis() / 1000L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EPG Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                placeholder = { Text("Search programme titles...") },
                leadingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.performSearch(searchQuery)
                            keyboardController?.hide()
                        }
                    }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.performSearch(searchQuery)
                            keyboardController?.hide()
                        }
                    }
                )
            )

            // Dev mode: show EPG DB stats
            if (isDevMode && epgDbStats != null) {
                Text(
                    text = "EPG: $epgDbStats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }

            // Indexing progress banner
            val currentIndexState = indexState
            if (currentIndexState is EpgIndexState.Indexing) {
                val idx = currentIndexState
                Column(
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = CinemaSpacing.xs
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Building search index...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${idx.progressPercent}% (${formatCount(idx.programmesIndexed)} programmes)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { idx.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CinemaSpacing.xxs)
                    )
                }
            }

            when (val state = uiState) {
                is EpgBrowserViewModel.UiState.Idle,
                is EpgBrowserViewModel.UiState.Indexing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search programme titles in your local EPG data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is EpgBrowserViewModel.UiState.NoEpgFile -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No EPG file available.\nConfigure an EPG URL in Settings.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is EpgBrowserViewModel.UiState.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Searching...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is EpgBrowserViewModel.UiState.Results -> {
                    MobileResultsContent(
                        results = state,
                        nowEpoch = nowEpoch,
                        isDevMode = isDevMode,
                        sourceLabels = sourceLabels,
                        onNavigateToPlayer = onNavigateToPlayer
                    )
                }
                is EpgBrowserViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
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
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> }
) {
    Column {
        // Stats row
        val timeStr = "%.1f".format(results.searchTimeMs / 1000.0)
        val truncatedSuffix = if (results.truncated) " (truncated)" else ""
        val sourceSuffix = if (results.searchedFromIndex) " [indexed]" else " [XML scan]"
        Text(
            text = "${results.totalPrograms} programs (${results.totalAirings} airings) — ${timeStr}s$truncatedSuffix$sourceSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.xs
            )
        )

        if (results.dateGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for '${results.query}'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md)
            ) {
                results.dateGroups.forEach { dateGroup ->
                    stickyHeader(key = "date::${dateGroup.dayStartEpoch}", contentType = "header") {
                        MobileDateHeader(dateLabel = dateGroup.dateLabel)
                    }
                    items(
                        dateGroup.programs,
                        key = { "${dateGroup.dayStartEpoch}::${it.title}::${it.description}" },
                        contentType = { "program" }
                    ) { program ->
                        MobileProgramCard(
                            program = program,
                            nowEpoch = nowEpoch,
                            isDevMode = isDevMode,
                            sourceLabels = sourceLabels,
                            onNavigateToPlayer = onNavigateToPlayer
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
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = CinemaSpacing.sm)
    )
}

@Composable
private fun MobileProgramCard(
    program: EpgBrowserProgram,
    nowEpoch: Long,
    isDevMode: Boolean = false,
    sourceLabels: Map<Long, String> = emptyMap(),
    onNavigateToPlayer: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    val showExpander = program.airings.size > 3
    var pendingConfirmAiring by remember { mutableStateOf<EpgBrowserAiring?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CinemaSpacing.md)
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).bounceMarquee()
                )
                val category = program.category
                if (category != null) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = CinemaSpacing.sm)
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
                    modifier = Modifier.padding(top = CinemaSpacing.xs)
                )
            }

            // Airings
            Spacer(modifier = Modifier.height(CinemaSpacing.xs))
            val visibleAirings = if (expanded || !showExpander) {
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
                    onRequestConfirmation = { pendingConfirmAiring = it }
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
                        Text("This show airs at ${formatAiringTime(airingContext, pending.startEpoch, pending.endEpoch)}.\nWatch ${pending.channelName} now?")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingConfirmAiring = null
                            onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                        }) { Text("Watch now") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingConfirmAiring = null }) { Text("Cancel") }
                    }
                )
            }

            // Expand/collapse toggle
            if (showExpander) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(top = CinemaSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more",
                        modifier = Modifier.size(CinemaSpacing.lg),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (expanded) "Show less" else "${program.airings.size - 3} more airings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
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
    onRequestConfirmation: (EpgBrowserAiring) -> Unit = {}
) {
    val isOnAir = nowEpoch >= airing.startEpoch && nowEpoch < airing.endEpoch
    val isSoon = !isOnAir && airing.startEpoch > nowEpoch && (airing.startEpoch - nowEpoch) <= 7200L
    val isMatched = airing.matchedStream != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isMatched) Modifier.clickable {
                    val matched = airing.matchedStream!!
                    if (isOnAir) {
                        onNavigateToPlayer(matched.streamId.toString(), matched.streamName, matched.categoryId)
                    } else {
                        onRequestConfirmation(airing)
                    }
                } else Modifier
            )
            .alpha(if (isMatched) 1f else 0.5f)
            .padding(vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMatched) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Watch",
                modifier = Modifier.size(CinemaSpacing.lg),
                tint = if (isOnAir) CinemaSuccess else MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = airing.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).bounceMarquee()
        )
        if (isDevMode && airing.sourceId > 0) {
            val sourceName = sourceLabels[airing.sourceId]
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CinemaAlpha.textLow)
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
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(CinemaCornerRadius.small))
                    .padding(horizontal = CinemaSpacing.xs, vertical = CinemaSpacing.xxs)
            )
        }
        val airingContext = LocalContext.current
        Text(
            text = formatAiringTime(airingContext, airing.startEpoch, airing.endEpoch),
            style = MaterialTheme.typography.bodySmall,
            color = if (isOnAir) CinemaSuccess else if (isSoon) CinemaWarning else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatAiringTime(context: android.content.Context, startEpoch: Long, endEpoch: Long): String {
    val startText = org.njarasoa.fijerena.core.player.model.TimeFormat.formatTime(context, startEpoch)
    val endText = org.njarasoa.fijerena.core.player.model.TimeFormat.formatTime(context, endEpoch)
    return "$startText – $endText"
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}
