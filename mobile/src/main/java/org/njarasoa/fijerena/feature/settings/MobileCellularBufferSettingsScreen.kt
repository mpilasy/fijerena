package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.player.config.NetworkBufferProfile
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaWarning
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileCellularBufferSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }

    var liveMultiplier by remember {
        mutableStateOf(appSettings.cellularLiveMultiplier)
    }
    var vodMultiplier by remember {
        mutableStateOf(appSettings.cellularVodMultiplier)
    }

    val hasChanges by remember {
        derivedStateOf {
            liveMultiplier != appSettings.cellularLiveMultiplier ||
                vodMultiplier != appSettings.cellularVodMultiplier
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cellular Buffer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, "Back")
                    }
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
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            // Warning card
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CinemaSpacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = CinemaIcons.Info,
                            contentDescription = "Warning",
                            tint = CinemaWarning,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Advanced Settings",
                                style = MaterialTheme.typography.labelMedium,
                                color = CinemaWarning,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Adjusting buffer multipliers affects cellular playback quality. Changes take effect on next playback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                            )
                        }
                    }
                }
            }

            // Live TV Section
            LocalSettingsSection(title = "Live TV Buffer") {
                Text(
                    text = "Adjust buffer size for cellular Live TV streams",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = liveMultiplier,
                        onValueChange = { liveMultiplier = it },
                        valueRange = 0.5f..3.0f,
                        steps = 4, // 0.5x, 1.0x, 1.5x, 2.0x, 2.5x, 3.0x
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Multiplier: %.1fx".format(liveMultiplier),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                // Real-time preview
                CellularBufferPreview(
                    minMs = NetworkBufferProfile.CELLULAR_LIVE_MIN_BUFFER_MS,
                    maxMs = NetworkBufferProfile.CELLULAR_LIVE_MAX_BUFFER_MS,
                    multiplier = liveMultiplier,
                )
            }

            // VOD Section
            LocalSettingsSection(title = "VOD Buffer") {
                Text(
                    text = "Adjust buffer size for cellular VOD streams",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = vodMultiplier,
                        onValueChange = { vodMultiplier = it },
                        valueRange = 0.5f..3.0f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Multiplier: %.1fx".format(vodMultiplier),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                // Real-time preview
                CellularBufferPreview(
                    minMs = NetworkBufferProfile.CELLULAR_VOD_MIN_BUFFER_MS,
                    maxMs = NetworkBufferProfile.CELLULAR_VOD_MAX_BUFFER_MS,
                    multiplier = vodMultiplier,
                )
            }

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        appSettings.cellularLiveMultiplier = liveMultiplier
                        appSettings.cellularVodMultiplier = vodMultiplier
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasChanges,
                ) {
                    Text("Apply Changes")
                }

                OutlinedButton(
                    onClick = {
                        liveMultiplier = 1.0f
                        vodMultiplier = 1.0f
                        appSettings.resetCellularBuffers()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset to Defaults")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CellularBufferPreview(
    minMs: Int,
    maxMs: Int,
    multiplier: Float,
) {
    val minBufferSeconds = (minMs * multiplier / 1000f)
    val maxBufferSeconds = (maxMs * multiplier / 1000f)

    GlassPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
    ) {
        Column(
            modifier = Modifier.padding(CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Buffer Preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(
                        text = "Min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                    )
                    Text(
                        text = "%.1fs".format(minBufferSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column {
                    Text(
                        text = "Max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                    )
                    Text(
                        text = "%.1fs".format(maxBufferSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(CinemaSpacing.md)) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                content()
            }
        }
    }
}
