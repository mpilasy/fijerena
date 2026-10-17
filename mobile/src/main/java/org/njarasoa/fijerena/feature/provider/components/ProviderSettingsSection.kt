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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.provider.SourceShortNames
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaSwitch
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.CinemaTextField
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerDialog
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/** Title (and optional dimmed subtitle) of one Edit Source section. */
@Composable
internal fun ProviderSectionTitle(
    title: String,
    subtitle: String? = null,
    titleTone: TextTone = TextTone.Accent,
) {
    CinemaText(title, role = TextRole.Heading, tone = titleTone)
    subtitle?.let {
        Spacer(modifier = Modifier.height(CinemaSpacing.xxs))
        CinemaText(it, role = TextRole.BodySmall, tone = TextTone.Secondary)
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
                .heightIn(min = MobileDimensions.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(vertical = CinemaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(title, role = TextRole.Heading)
            CinemaText(description, role = TextRole.BodySmall, tone = TextTone.Secondary)
        }
        Spacer(modifier = Modifier.width(CinemaSpacing.md))
        CinemaText(value, tone = TextTone.Accent)
        Icon(
            CinemaIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = CinemaTheme.colors.textSecondary,
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
                        CinemaText(stringResource(R.string.provider_auto_resume_label), role = TextRole.Heading)
                        CinemaText(stringResource(R.string.provider_auto_resume_desc), role = TextRole.BodySmall, tone = TextTone.Secondary)
                    }
                    Spacer(modifier = Modifier.width(CinemaSpacing.md))
                    CinemaSwitch(
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
                            CinemaText(stringResource(R.string.provider_enable_caching_label), role = TextRole.Heading)
                            CinemaText(
                                stringResource(R.string.provider_enable_caching_desc),
                                role = TextRole.BodySmall,
                                tone = TextTone.Secondary,
                            )
                        }
                        Spacer(modifier = Modifier.width(CinemaSpacing.md))
                        CinemaSwitch(
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

                // The source badge's short name: the viewer's, else the one worked out from the name.
                val shortNames by remember { providerRepo.sourceShortNames() }.collectAsState(initial = emptyMap())
                var editingShortName by remember { mutableStateOf(false) }
                val automatic = shortNames[editId].orEmpty()
                Spacer(modifier = Modifier.height(CinemaSpacing.md))
                ProviderChoiceRow(
                    title = stringResource(R.string.provider_short_name_label),
                    description = stringResource(R.string.provider_short_name_desc),
                    value = providerSettings.shortName.ifBlank { stringResource(R.string.provider_short_name_automatic, automatic) },
                    onClick = { editingShortName = true },
                )
                if (editingShortName) {
                    ShortNameDialog(
                        initial = providerSettings.shortName,
                        automatic = automatic,
                        onSave = { wanted, onTaken ->
                            coroutineScope.launch {
                                if (providerRepo.setShortName(editId, wanted)) {
                                    onProviderSettingsChange(providerSettings.copy(shortName = wanted))
                                    editingShortName = false
                                } else {
                                    onTaken()
                                }
                            }
                        },
                        onDismiss = { editingShortName = false },
                    )
                }
            }
        }
    }
}

/** Types the short name ([SourceShortNames.MAX_LENGTH] characters at most); empty means automatic. */
@Composable
private fun ShortNameDialog(
    initial: String,
    automatic: String,
    onSave: (String, onTaken: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var taken by remember { mutableStateOf<String?>(null) }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = { CinemaText(stringResource(R.string.provider_short_name_label), role = TextRole.Title) },
        text = {
            CinemaTextField(
                value = value,
                onValueChange = {
                    value = it.take(SourceShortNames.MAX_LENGTH)
                    taken = null
                },
                placeholder = automatic,
                keyboardOptions = FieldKeyboards.Code,
                singleLine = true,
                isError = taken != null,
                supportingText =
                    taken?.let { stringResource(R.string.provider_short_name_taken, it) }
                        ?: stringResource(R.string.provider_short_name_desc),
            )
        },
        confirmButton = {
            CinemaDialogActionButton(onClick = {
                val wanted = value.trim()
                onSave(wanted) { taken = wanted }
            }) { CinemaText(stringResource(R.string.common_ok), role = TextRole.Label, tone = TextTone.Inherit) }
        },
        dismissButton = {
            CinemaDialogActionButton(onClick = onDismiss, kind = ButtonKind.Other) {
                CinemaText(stringResource(R.string.common_cancel), role = TextRole.Label, tone = TextTone.Inherit)
            }
        },
    )
}
