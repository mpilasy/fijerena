package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip

/** Title (and optional dimmed subtitle) of one Edit Source section. */
@Composable
internal fun ProviderSectionTitle(
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = titleColor,
    )
    subtitle?.let {
        Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
        )
    }
}

/** Behaviour: per-source playback and caching settings, each saved as soon as it changes. */
@Composable
fun ColumnScope.ProviderSettingsSection(
    isEditMode: Boolean,
    editId: Long,
    selectedType: ProviderType,
    providerSettings: ProviderSettings,
    autoResumeEnabled: Boolean,
    watchHistorySize: String,
    newWatchHistorySize: String,
    isEditingQueueSize: Boolean,
    cachingEnabled: Boolean,
    streamOutputFormat: String,
    playlistType: String,
    coroutineScope: CoroutineScope,
    providerRepo: ProviderRepository,
    onProviderSettingsChange: (ProviderSettings) -> Unit,
    onAutoResumeEnabledChange: (Boolean) -> Unit,
    onWatchHistorySizeChange: (String) -> Unit,
    onNewWatchHistorySizeChange: (String) -> Unit,
    onIsEditingQueueSizeChange: (Boolean) -> Unit,
    onCachingEnabledChange: (Boolean) -> Unit,
    onStreamOutputFormatChange: (String) -> Unit,
    onPlaylistTypeChange: (String) -> Unit,
) {
    if (isEditMode) {
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                ProviderSectionTitle(
                    title = stringResource(R.string.provider_section_behaviour),
                    subtitle = stringResource(R.string.settings_applies_immediately),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))

                // Auto-Resume
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.provider_auto_resume_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.provider_auto_resume_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                        )
                    }
                    Spacer(modifier = Modifier.width(CinemaSpacing.md))
                    Switch(
                        checked = autoResumeEnabled,
                        onCheckedChange = { enabled ->
                            onAutoResumeEnabledChange(enabled)
                            coroutineScope.launch {
                                val newSettings = providerSettings.copy(autoResumeEnabled = enabled)
                                providerRepo.updateProviderSettings(editId, newSettings)
                                onProviderSettingsChange(newSettings)
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                Text(text = stringResource(R.string.provider_watch_history_size_label), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.provider_watch_history_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))

                if (!isEditingQueueSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = watchHistorySize, style = MaterialTheme.typography.titleLarge)
                        CinemaOutlinedButton(onClick = {
                            onIsEditingQueueSizeChange(true)
                            onNewWatchHistorySizeChange(watchHistorySize)
                        }) { Text(stringResource(R.string.provider_edit_button)) }
                    }
                } else {
                    OutlinedTextField(
                        value = newWatchHistorySize,
                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) onNewWatchHistorySizeChange(it) },
                        label = { Text(stringResource(R.string.provider_queue_size_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs, Alignment.End),
                    ) {
                        CinemaOutlinedButton(onClick = {
                            onIsEditingQueueSizeChange(false)
                            onNewWatchHistorySizeChange("")
                        }) { Text(stringResource(R.string.common_cancel)) }
                        CinemaButton(
                            onClick = {
                                val size = newWatchHistorySize.toIntOrNull()
                                if (size != null && size in 1..100) {
                                    onWatchHistorySizeChange(size.toString())
                                    onIsEditingQueueSizeChange(false)
                                    onNewWatchHistorySizeChange("")
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(watchHistorySize = size)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        onProviderSettingsChange(newSettings)
                                    }
                                }
                            },
                            enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true,
                        ) { Text(stringResource(R.string.provider_save_button)) }
                    }
                }

                if (selectedType == ProviderType.XTREAM) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))
                    // Selected = app accent, not the theme's orange secondaryContainer (M3's chip default).
                    val chipColors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        )

                    Text(text = stringResource(R.string.provider_stream_format_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.provider_stream_format_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                    ) {
                        listOf("m3u8", "ts").forEach { format ->
                            CinemaFilterChip(
                                selected = streamOutputFormat == format,
                                onClick = {
                                    onStreamOutputFormatChange(format)
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(streamOutputFormat = format)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        onProviderSettingsChange(newSettings)
                                    }
                                },
                                label = { Text(format) },
                                colors = chipColors,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    Text(text = stringResource(R.string.provider_playlist_type_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.provider_playlist_type_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                    ) {
                        listOf("m3u_plus", "simple").forEach { type ->
                            CinemaFilterChip(
                                selected = playlistType == type,
                                onClick = {
                                    onPlaylistTypeChange(type)
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(playlistType = type)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        onProviderSettingsChange(newSettings)
                                    }
                                },
                                label = { Text(type) },
                                colors = chipColors,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Enable Caching
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.provider_enable_caching_label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(R.string.provider_enable_caching_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                            )
                        }
                        Spacer(modifier = Modifier.width(CinemaSpacing.md))
                        Switch(
                            checked = cachingEnabled,
                            onCheckedChange = { enabled ->
                                onCachingEnabledChange(enabled)
                                coroutineScope.launch {
                                    val newSettings = providerSettings.copy(cachingEnabled = enabled)
                                    providerRepo.updateProviderSettings(editId, newSettings)
                                    onProviderSettingsChange(newSettings)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
