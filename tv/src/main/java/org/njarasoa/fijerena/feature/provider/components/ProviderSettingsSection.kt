package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.rememberFocusReturn
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

private const val CATEGORY_FILTER_PREVIEW_COUNT = 6

@Composable
fun ProviderSettingsSection(
    providerType: ProviderType,
    providerSettings: ProviderSettings,
    onUpdateSettings: (ProviderSettings) -> Unit,
    onClearFavoritesClick: () -> Unit,
    onClearProgressClick: () -> Unit,
    onManageFiltersClick: () -> Unit,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    // Memoize scaled TextStyles to avoid allocating new copies per recomposition
    val styles =
        remember(scale, typography) {
            object {
                val titleMedium = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale))
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val titleLarge = typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale))
                val bodyMedium = typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }

    Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))
    HorizontalDivider(color = CinemaTextSecondary.copy(alpha = CinemaAlpha.focusedTint))
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    Text(
        text = stringResource(R.string.provider_settings_title),
        style = styles.titleMedium,
        color = CinemaAccent,
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Auto-Resume
    TvSwitchRow(
        checked = providerSettings.autoResumeEnabled,
        onCheckedChange = { enabled ->
            onUpdateSettings(providerSettings.copy(autoResumeEnabled = enabled))
        },
        label = stringResource(R.string.provider_auto_resume_label),
        description = stringResource(R.string.provider_auto_resume_desc),
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Watch History Size
    WatchHistorySizeSetting(
        currentSize = providerSettings.watchHistorySize,
        onSizeChanged = { size ->
            onUpdateSettings(providerSettings.copy(watchHistorySize = size))
        },
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Clear Favorites
    Column {
        Text(
            text = stringResource(R.string.provider_clear_favorites_title).removeSuffix("?"),
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.provider_clear_favorites_message),
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        CinemaDangerButton(
            onClick = onClearFavoritesClick,
            text = stringResource(R.string.provider_clear_favorites_button),
        )
    }

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Clear Progress
    Column {
        Text(
            text = stringResource(R.string.provider_clear_progress_title).removeSuffix("?"),
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.provider_clear_progress_message),
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        CinemaDangerButton(
            onClick = onClearProgressClick,
            text = stringResource(R.string.provider_clear_progress_button),
        )
    }

    // Xtream-only settings
    if (providerType == ProviderType.XTREAM) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Stream Output Format
        Column {
            Text(
                text = stringResource(R.string.provider_stream_format_label),
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.provider_stream_format_desc),
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                val currentFormat = providerSettings.streamOutputFormat
                listOf("m3u8", "ts").forEach { format ->
                    TvSelectableButton(
                        selected = format == currentFormat,
                        onSelect = { onUpdateSettings(providerSettings.copy(streamOutputFormat = format)) },
                        text = format,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Playlist Type
        Column {
            Text(
                text = stringResource(R.string.provider_playlist_type_label),
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.provider_playlist_type_desc),
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                val currentType = providerSettings.playlistType
                listOf("m3u_plus", "simple").forEach { type ->
                    TvSelectableButton(
                        selected = type == currentType,
                        onSelect = { onUpdateSettings(providerSettings.copy(playlistType = type)) },
                        text = type,
                    )
                }
            }
        }
    }

    // Category Filters (Xtream only)
    if (providerType == ProviderType.XTREAM) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        Column {
            Text(
                text = stringResource(R.string.provider_category_filters_title),
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.provider_category_filters_desc),
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.provider_filter_mode_value,
                        if (providerSettings.categoryFilters.mode == FilterMode.EXCLUDE) {
                            stringResource(R.string.provider_filter_exclude)
                        } else {
                            stringResource(R.string.provider_filter_include)
                        },
                    ),
                    style = styles.bodyMedium,
                    color = CinemaTextPrimary,
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                Text(
                    text = if (providerSettings.categoryFilters.rules.isEmpty()) {
                        stringResource(R.string.provider_no_filters)
                    } else {
                        val rules = providerSettings.categoryFilters.rules
                        val preview = rules.take(CATEGORY_FILTER_PREVIEW_COUNT).joinToString(", ") { it.value }
                        val remaining = rules.size - CATEGORY_FILTER_PREVIEW_COUNT
                        val suffix = if (remaining > 0) ", +$remaining more" else ""
                        stringResource(
                            R.string.provider_prefixes_value,
                            rules.size,
                            "$preview$suffix",
                        )
                    },
                    style = styles.bodyMedium,
                    color = CinemaTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.provider_scripts_value,
                    if (providerSettings.categoryFilters.allowedScripts.isEmpty()) {
                        stringResource(R.string.common_all)
                    } else {
                        providerSettings.categoryFilters.allowedScripts.joinToString(", ") { it.displayName }
                    },
                ),
                style = styles.bodyMedium,
                color = CinemaTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CinemaPrimaryButton(
                onClick = onManageFiltersClick,
                text = stringResource(R.string.provider_manage_filters_button),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Enable Caching
        TvSwitchRow(
            checked = providerSettings.cachingEnabled,
            onCheckedChange = { enabled ->
                onUpdateSettings(providerSettings.copy(cachingEnabled = enabled))
            },
            label = stringResource(R.string.provider_enable_caching_label),
            description = stringResource(R.string.provider_enable_caching_desc),
        )
    }
}

@Composable
private fun WatchHistorySizeSetting(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val styles =
        remember(scale, typography) {
            object {
                val titleSmall = typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale))
                val titleLarge = typography.titleLarge.copy(fontSize = typography.titleLarge.fontSize.scaled(scale))
                val bodySmall = typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale))
            }
        }
    var isEditing by remember { mutableStateOf(false) }
    var newSize by remember { mutableStateOf("") }

    // Leaving edit mode destroys the focused TextField; without a hand-off Compose drops focus to
    // the window root and the next D-pad press restarts at the top of the form.
    val editButtonFocusRequester = rememberFocusReturn(active = isEditing)

    Column {
        Text(
            text = stringResource(R.string.provider_watch_history_size_label),
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.provider_watch_history_size_desc),
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))

        if (!isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentSize.toString(),
                    style = styles.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CinemaSecondaryButton(
                    onClick = {
                        isEditing = true
                        newSize = currentSize.toString()
                    },
                    text = stringResource(R.string.provider_edit_button),
                    modifier = Modifier.focusRequester(editButtonFocusRequester),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = newSize,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                            newSize = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.provider_queue_size_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(TvDimensions.selectionListWidth.scaled(scale)).tvDpadEscape(),
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                CinemaSecondaryButton(
                    onClick = {
                        isEditing = false
                        newSize = ""
                    },
                    text = stringResource(R.string.common_cancel),
                )
                Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                CinemaPrimaryButton(
                    onClick = {
                        val size = newSize.toIntOrNull()
                        if (size != null && size in 1..100) {
                            onSizeChanged(size)
                            isEditing = false
                            newSize = ""
                        }
                    },
                    enabled = newSize.toIntOrNull()?.let { it in 1..100 } == true,
                    text = stringResource(R.string.provider_save_button),
                )
            }
        }
    }
}
