package org.njarasoa.fijerena.feature.epgbrowser

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEpgBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel = viewModel(
        factory = remember { EpgBrowserViewModelFactory(context.applicationContext) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val indexState by viewModel.indexState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDevMode = viewModel.isDevMode
    val sourceLabels by viewModel.sourceLabels.collectAsState()
    val epgDbStats = when (val idx = indexState) {
        is EpgIndexState.Indexed -> "${idx.programmeCount} progs, ${idx.channelCount} channels"
        else -> null
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
                    MobileResultsContent(results = state, isDevMode = isDevMode, sourceLabels = sourceLabels)
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

@Composable
private fun MobileResultsContent(results: EpgBrowserViewModel.UiState.Results, isDevMode: Boolean = false, sourceLabels: Map<Long, String> = emptyMap()) {
    Column {
        // Stats row
        val timeStr = "%.1f".format(results.searchTimeMs / 1000.0)
        val truncatedSuffix = if (results.truncated) " (truncated)" else ""
        val sourceSuffix = if (results.searchedFromIndex) " [indexed]" else " [XML scan]"
        Text(
            text = "${results.programs.size} programs (${results.totalAirings} airings) — ${timeStr}s$truncatedSuffix$sourceSuffix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.xs
            )
        )

        if (results.programs.isEmpty()) {
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
                items(results.programs, key = { it.title }) { program ->
                    MobileProgramCard(program = program, isDevMode = isDevMode, sourceLabels = sourceLabels)
                }
            }
        }
    }
}

@Composable
private fun MobileProgramCard(program: EpgBrowserProgram, isDevMode: Boolean = false, sourceLabels: Map<Long, String> = emptyMap()) {
    var expanded by remember { mutableStateOf(false) }
    val showExpander = program.airings.size > 3

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
                    modifier = Modifier.weight(1f)
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
                MobileAiringRow(airing = airing, isDevMode = isDevMode, sourceLabels = sourceLabels)
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
private fun MobileAiringRow(airing: EpgBrowserAiring, isDevMode: Boolean = false, sourceLabels: Map<Long, String> = emptyMap()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CinemaSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = airing.channelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isDevMode && airing.sourceId > 0) {
            val sourceName = sourceLabels[airing.sourceId]
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CinemaAlpha.textLow),
                    modifier = Modifier.padding(end = CinemaSpacing.xs)
                )
            }
        }
        Text(
            text = formatAiringTime(airing.startEpoch, airing.endEpoch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatAiringTime(startEpoch: Long, endEpoch: Long): String {
    val timeOnlyFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    timeOnlyFormat.timeZone = TimeZone.getDefault()

    val startDate = Date(startEpoch * 1000L)
    val endDate = Date(endEpoch * 1000L)
    val now = Date()

    val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    todayFormat.timeZone = TimeZone.getDefault()
    val today = todayFormat.format(now)
    val startDay = todayFormat.format(startDate)

    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val tomorrow = todayFormat.format(cal.time)

    val diffMs = startDate.time - now.time
    val diffDays = diffMs / (24 * 60 * 60 * 1000L)

    val timePart = "${timeOnlyFormat.format(startDate)} – ${timeOnlyFormat.format(endDate)}"
    val dayPrefix = when {
        startDay == today -> "Today"
        startDay == tomorrow -> "Tomorrow"
        diffDays <= 2 -> SimpleDateFormat("EEE", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(startDate)
        else -> SimpleDateFormat("EEE MMM d", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(startDate)
    }

    return "$dayPrefix $timePart"
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
