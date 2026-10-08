package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog

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

private enum class ProviderPicker { HISTORY_SIZE, STREAM_FORMAT, PLAYLIST_TYPE }

private val STREAM_FORMATS = listOf("m3u8", "ts")
private val PLAYLIST_TYPES = listOf("m3u_plus", "simple")

// The presets of the TV's picker; the 25 default (ProviderSettings.watchHistorySize) is one of them.
private val HISTORY_SIZE_OPTIONS = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/** The stored size as shown: a preset as is, anything else (set before the picker, 1–100) marked custom. */
@Composable
private fun historySizeLabel(size: Int): String =
    if (size in HISTORY_SIZE_OPTIONS) size.toString() else stringResource(R.string.settings_custom_value_format, size.toString())

/** A setting whose value is picked from a list: title and description, the value, a chevron; opens the picker. */
@Composable
private fun ProviderChoiceRow(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(vertical = CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )
        }
        Spacer(modifier = Modifier.width(CinemaSpacing.md))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Icon(
            CinemaIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
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
    cachingEnabled: Boolean,
    streamOutputFormat: String,
    playlistType: String,
    coroutineScope: CoroutineScope,
    providerRepo: ProviderRepository,
    onProviderSettingsChange: (ProviderSettings) -> Unit,
    onAutoResumeEnabledChange: (Boolean) -> Unit,
    onWatchHistorySizeChange: (String) -> Unit,
    onCachingEnabledChange: (Boolean) -> Unit,
    onStreamOutputFormatChange: (String) -> Unit,
    onPlaylistTypeChange: (String) -> Unit,
) {
    if (isEditMode) {
        var openPicker by remember { mutableStateOf<ProviderPicker?>(null) }
        // Saves one changed setting, as the switches do.
        val save: (ProviderSettings) -> Unit = { newSettings ->
            coroutineScope.launch {
                providerRepo.updateProviderSettings(editId, newSettings)
                onProviderSettingsChange(newSettings)
            }
        }
        when (openPicker) {
            ProviderPicker.HISTORY_SIZE -> {
                val current = watchHistorySize.toIntOrNull() ?: providerSettings.watchHistorySize
                // A stored value outside the presets is kept as its own checked option, so nothing is lost.
                val sizes = (HISTORY_SIZE_OPTIONS + current).distinct().sorted()
                SettingsPickerDialog(
                    title = stringResource(R.string.provider_watch_history_size_label),
                    options = sizes.map { historySizeLabel(it) to it },
                    selected = current,
                    onSelect = { size ->
                        openPicker = null
                        if (size != current) {
                            onWatchHistorySizeChange(size.toString())
                            save(providerSettings.copy(watchHistorySize = size))
                        }
                    },
                    onDismiss = { openPicker = null },
                )
            }

            ProviderPicker.STREAM_FORMAT -> {
                SettingsPickerDialog(
                    title = stringResource(R.string.provider_stream_format_label),
                    options = STREAM_FORMATS.map { it to it },
                    selected = streamOutputFormat,
                    onSelect = { format ->
                        openPicker = null
                        onStreamOutputFormatChange(format)
                        save(providerSettings.copy(streamOutputFormat = format))
                    },
                    onDismiss = { openPicker = null },
                )
            }

            ProviderPicker.PLAYLIST_TYPE -> {
                SettingsPickerDialog(
                    title = stringResource(R.string.provider_playlist_type_label),
                    options = PLAYLIST_TYPES.map { it to it },
                    selected = playlistType,
                    onSelect = { type ->
                        openPicker = null
                        onPlaylistTypeChange(type)
                        save(providerSettings.copy(playlistType = type))
                    },
                    onDismiss = { openPicker = null },
                )
            }

            null -> {}
        }

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

                ProviderChoiceRow(
                    title = stringResource(R.string.provider_watch_history_size_label),
                    description = stringResource(R.string.provider_watch_history_size_desc),
                    value = historySizeLabel(watchHistorySize.toIntOrNull() ?: providerSettings.watchHistorySize),
                    onClick = { openPicker = ProviderPicker.HISTORY_SIZE },
                )

                if (selectedType == ProviderType.XTREAM) {
                    ProviderChoiceRow(
                        title = stringResource(R.string.provider_stream_format_label),
                        description = stringResource(R.string.provider_stream_format_desc),
                        value = streamOutputFormat,
                        onClick = { openPicker = ProviderPicker.STREAM_FORMAT },
                    )
                    ProviderChoiceRow(
                        title = stringResource(R.string.provider_playlist_type_label),
                        description = stringResource(R.string.provider_playlist_type_desc),
                        value = playlistType,
                        onClick = { openPicker = ProviderPicker.PLAYLIST_TYPE },
                    )

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
