@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.epgbrowser

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserProgram
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.EpgBrowserViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun EpgBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: EpgBrowserViewModel = viewModel(
        factory = remember { EpgBrowserViewModelFactory(context.applicationContext) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val indexState by viewModel.indexState.collectAsState()
    val appSettings = remember { org.njarasoa.fijerena.core.network.AppSettings(context.applicationContext) }
    val isDevMode = appSettings.isDevMode
    val epgFileSizeBytes = viewModel.epgFileSizeBytes

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            // Header
            Text(
                text = "EPG Browser",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            when (val state = uiState) {
                is EpgBrowserViewModel.UiState.NoEpgFile -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No EPG file available. Configure an EPG URL in Settings.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaTextSecondary
                        )
                    }
                }
                is EpgBrowserViewModel.UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CinemaError
                        )
                    }
                }
                else -> {
                    EpgBrowserContent(
                        uiState = state,
                        indexState = indexState,
                        isDevMode = isDevMode,
                        epgFileSizeBytes = epgFileSizeBytes,
                        onSearch = { viewModel.performSearch(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgBrowserContent(
    uiState: EpgBrowserViewModel.UiState,
    indexState: EpgIndexState,
    isDevMode: Boolean,
    epgFileSizeBytes: Long?,
    onSearch: (String) -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    var localQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        GlassPanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = localQuery,
                    onValueChange = { localQuery = it },
                    label = { Text("Search programmes") },
                    placeholder = { Text("Enter programme title...") },
                    singleLine = true,
                    modifier = Modifier
                        .width(TvDimensions.formFieldWidth)
                        .focusRequester(searchFocusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch(localQuery) }
                    )
                )
                CinemaIconButton(
                    onClick = { onSearch(localQuery) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                )
            }
        }

        // Auto-focus on screen open
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }

        // Dev mode: show EPG file size
        if (isDevMode && epgFileSizeBytes != null) {
            Text(
                text = "EPG file: ${formatFileSize(epgFileSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textLow),
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        // Indexing progress banner
        val currentIndexState = indexState
        if (currentIndexState is EpgIndexState.Indexing) {
            val idx = currentIndexState
            Column(modifier = Modifier.padding(top = Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Building search index...",
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaAccentLight
                    )
                    Text(
                        text = "${idx.progressPercent}% (${formatCount(idx.programmesIndexed)} programmes)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CinemaTextSecondary
                    )
                }
                LinearProgressIndicator(
                    progress = { idx.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xxs),
                    color = CinemaAccent,
                    trackColor = CinemaSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        when (uiState) {
            is EpgBrowserViewModel.UiState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search programme titles in your local EPG data",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(TvDimensions.iconXLarge),
                            color = CinemaAccent
                        )
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaTextSecondary
                        )
                    }
                }
            }
            is EpgBrowserViewModel.UiState.Results -> {
                ResultsContent(results = uiState)
            }
            else -> {} // NoEpgFile and Error handled in parent
        }
    }
}

@Composable
private fun ResultsContent(results: EpgBrowserViewModel.UiState.Results) {
    Column {
        // Stats row
        val timeStr = "%.1f".format(results.searchTimeMs / 1000.0)
        val truncatedSuffix = if (results.truncated) " (truncated)" else ""
        val sourceSuffix = if (results.searchedFromIndex) " [indexed]" else " [XML scan]"
        Text(
            text = "${results.programs.size} programs (${results.totalAirings} airings) — ${timeStr}s$truncatedSuffix$sourceSuffix",
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        if (results.programs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for '${results.query}'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh)
                )
            }
        } else {
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results.programs, key = { it.title }) { program ->
                    ProgramCard(program = program)
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(program: EpgBrowserProgram) {
    Card(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        colors = CardDefaults.colors(
            containerColor = CinemaSurface,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaAccent.copy(alpha = CinemaAlpha.glassBorder),
            focusedContentColor = CinemaTextPrimary
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CornerRadius.medium)),
        scale = CardDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScaleContent,
            pressedScale = TvFocusTokens.pressedScaleSubtle
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = CinemaAccent.copy(alpha = CinemaAlpha.cardElevationShadow),
                elevation = TvFocusTokens.focusShadowElevation
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            // Title + category badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = program.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val category = program.category
                if (category != null) {
                    GlassPanel(
                        modifier = Modifier.padding(start = Spacing.sm)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            color = CinemaAccentLight,
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xxs
                            )
                        )
                    }
                }
            }

            // Description (truncated)
            val description = program.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            // Airings
            Spacer(modifier = Modifier.height(Spacing.sm))
            program.airings.forEach { airing ->
                AiringRow(airing = airing)
            }
        }
    }
}

@Composable
private fun AiringRow(airing: EpgBrowserAiring) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = airing.channelName,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaAccentLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatAiringTime(airing.startEpoch, airing.endEpoch),
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary
        )
    }
}

private fun formatAiringTime(startEpoch: Long, endEpoch: Long): String {
    val dateFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
    dateFormat.timeZone = TimeZone.getDefault()
    val timeOnlyFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    timeOnlyFormat.timeZone = TimeZone.getDefault()

    val startDate = Date(startEpoch * 1000L)
    val endDate = Date(endEpoch * 1000L)

    // Check if today
    val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    todayFormat.timeZone = TimeZone.getDefault()
    val today = todayFormat.format(Date())
    val startDay = todayFormat.format(startDate)

    val dayPrefix = if (startDay == today) "Today" else {
        SimpleDateFormat("EEE", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(startDate)
    }

    return "$dayPrefix ${timeOnlyFormat.format(startDate)} – ${timeOnlyFormat.format(endDate)}"
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
