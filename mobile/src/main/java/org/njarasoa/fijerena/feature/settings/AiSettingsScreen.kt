package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.AiSettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAiSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: AiSettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val isAiProcessing by viewModel.isAiProcessing.collectAsStateWithLifecycle()
    val isPremiumDevice = viewModel.isPremiumDevice

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Search Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md)
        ) {
            if (!isPremiumDevice) {
                GlassPanel {
                    Text(
                        text = "AI semantic search is not supported on this device. High-end hardware (NVIDIA Shield or Flagship phone) is required for on-device vector processing.",
                        modifier = Modifier.padding(CinemaSpacing.md),
                        color = CinemaError
                    )
                }
            } else {
                // Global Action
                GlassPanel {
                    Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                        Text("Vector Processing", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Background AI processing indexes your movies, series, and categories for conceptual searching. This runs when the device is idle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow)
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.md))
                        if (isAiProcessing) {
                            Button(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                                Text("AI Processing...")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.scheduleVectorization() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(modifier = Modifier.width(CinemaSpacing.sm))
                                Text("Run AI Processing Pass")
                            }
                        }
                    }
                }

                // Overall Progress
                GlassPanel {
                    Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                        Text("Overall Indexing Status", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                        LinearProgressIndicator(
                            progress = { stats.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(stats.progress * 100).toInt()}% processed", style = MaterialTheme.typography.bodySmall)
                            Text("${stats.totalProcessed} / ${stats.totalItems} items", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Content Type Breakdown
                Text("Content Breakdown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                
                AiStatRow(label = "Categories", processed = stats.processedCategories, total = stats.totalCategories)
                AiStatRow(label = "VOD Streams", processed = stats.processedStreams, total = stats.totalStreams)
                AiStatRow(label = "TV Series", processed = stats.processedSeries, total = stats.totalSeries)
                AiStatRow(label = "Episodes", processed = stats.processedEpisodes, total = stats.totalEpisodes)
            }
        }
    }
}

@Composable
fun AiStatRow(label: String, processed: Int, total: Int) {
    val progress = if (total > 0) processed.toFloat() / total else 1f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(CinemaSpacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text("$processed / $total", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (progress >= 1f) CinemaSuccess else MaterialTheme.colorScheme.primary
            )
        }
    }
}
