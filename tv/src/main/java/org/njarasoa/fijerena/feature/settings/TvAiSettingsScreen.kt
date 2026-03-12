package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.AiSettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAiSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AiSettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isAiProcessing by viewModel.isAiProcessing.collectAsStateWithLifecycle()
    val isPremiumDevice = viewModel.isPremiumDevice
    val scale = LocalUiScale.current

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                )
        ) {
            Text(
                text = "AI Search Settings",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))

            TvLazyColumn(
                contentPadding = PaddingValues(vertical = Spacing.xs.scaled(scale)),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (!isPremiumDevice) {
                    item {
                        GlassPanel {
                            Text(
                                text = "AI semantic search is not supported on this device.",
                                modifier = Modifier.padding(Spacing.md.scaled(scale)),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    // Action Card
                    item {
                        GlassPanel {
                            Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                                Text("Vector Processing", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Indexes your movies, series, and categories for conceptual searching.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                                )
                                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                                
                                if (isAiProcessing) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(Spacing.sm.scaled(scale)))
                                        Text("AI Processing in progress...")
                                    }
                                } else {
                                    CinemaSecondaryButton(
                                        onClick = { viewModel.scheduleVectorization() },
                                        text = "Run AI Processing Pass"
                                    )
                                }
                            }
                        }
                    }

                    // Overall Progress
                    item {
                        GlassPanel {
                            Column(modifier = Modifier.padding(Spacing.md.scaled(scale))) {
                                Text("Indexing Status", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { stats.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                                Text(
                                    "${(stats.progress * 100).toInt()}% processed (${stats.totalProcessed} / ${stats.totalItems} items)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Content Breakdown
                    item {
                        Text("Detailed Breakdown", style = MaterialTheme.typography.titleSmall)
                    }

                    item { TvAiStatRow("Categories", stats.processedCategories, stats.totalCategories, scale) }
                    item { TvAiStatRow("VOD Streams", stats.processedStreams, stats.totalStreams, scale) }
                    item { TvAiStatRow("TV Series", stats.processedSeries, stats.totalSeries, scale) }
                    item { TvAiStatRow("Episodes", stats.processedEpisodes, stats.totalEpisodes, scale) }
                }
            }
        }
    }
}

@Composable
fun TvAiStatRow(label: String, processed: Int, total: Int, scale: Float) {
    val progress = if (total > 0) processed.toFloat() / total else 1f
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RectangleShape)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm.scaled(scale))) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text("$processed / $total", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}
