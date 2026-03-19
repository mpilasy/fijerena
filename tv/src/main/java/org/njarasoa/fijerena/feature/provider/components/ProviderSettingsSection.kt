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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

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
        text = "Provider Settings",
        style = styles.titleMedium,
        color = CinemaAccent,
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Auto-Resume
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tvFocusableNoScale(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Auto-Resume",
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Resume VOD content from where you left off",
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }
        Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
        Switch(
            checked = providerSettings.autoResumeEnabled,
            onCheckedChange = { enabled ->
                onUpdateSettings(providerSettings.copy(autoResumeEnabled = enabled))
            },
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = CinemaAccent,
                    checkedTrackColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                    uncheckedThumbColor = CinemaTextSecondary,
                    uncheckedTrackColor = CinemaSurfaceVariant,
                ),
        )
    }

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Watch History Size
    WatchHistorySizeSetting(
        currentSize = providerSettings.watchHistorySize,
        onSizeChanged = { size ->
            onUpdateSettings(providerSettings.copy(watchHistorySize = size))
        },
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Favorites Max Size
    FavoritesMaxSizeSetting(
        currentSize = providerSettings.favoritesMaxSize,
        onSizeChanged = { size ->
            onUpdateSettings(providerSettings.copy(favoritesMaxSize = size))
        },
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Clear Favorites
    Column {
        Text(
            text = "Clear All Favorites",
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Remove all favorited streams from all content types",
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        CinemaDangerButton(
            onClick = onClearFavoritesClick,
            text = "Clear All Favorites",
        )
    }

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    // Clear Progress
    Column {
        Text(
            text = "Clear Playback Progress",
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Remove all saved positions (Continue Watching will be empty)",
            style = styles.bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        CinemaDangerButton(
            onClick = onClearProgressClick,
            text = "Clear All Progress",
        )
    }

    // Xtream-only settings
    if (providerType == ProviderType.XTREAM) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Stream Output Format
        Column {
            Text(
                text = "Stream Output Format",
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Format used for live stream URLs (m3u8 = HLS, ts = MPEG-TS)",
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                val currentFormat = providerSettings.streamOutputFormat
                listOf("m3u8", "ts").forEach { format ->
                    if (format == currentFormat) {
                        CinemaPrimaryButton(
                            onClick = {},
                            text = format,
                        )
                    } else {
                        CinemaSecondaryButton(
                            onClick = {
                                onUpdateSettings(providerSettings.copy(streamOutputFormat = format))
                            },
                            text = format,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Playlist Type
        Column {
            Text(
                text = "Playlist Type",
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Playlist format (m3u_plus = extended with EPG, simple = basic)",
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                val currentType = providerSettings.playlistType
                listOf("m3u_plus", "simple").forEach { type ->
                    if (type == currentType) {
                        CinemaPrimaryButton(
                            onClick = {},
                            text = type,
                        )
                    } else {
                        CinemaSecondaryButton(
                            onClick = {
                                onUpdateSettings(providerSettings.copy(playlistType = type))
                            },
                            text = type,
                        )
                    }
                }
            }
        }
    }

    // Category Filters (Xtream only)
    if (providerType == ProviderType.XTREAM) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        Column {
            Text(
                text = "Category Filters",
                style = styles.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Hide or show categories based on name prefixes",
                style = styles.bodySmall,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Mode: ${if (providerSettings.categoryFilters.mode == FilterMode.EXCLUDE) "Exclude" else "Include"}",
                    style = styles.bodyMedium,
                    color = CinemaTextPrimary,
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                Text(
                    text = "Prefixes: ${if (providerSettings.categoryFilters.prefixes.isEmpty()) {
                        "None"
                    } else {
                        providerSettings.categoryFilters.prefixes
                            .joinToString(
                                ", ",
                            )
                    }}",
                    style = styles.bodyMedium,
                    color = CinemaTextSecondary,
                )
            }
            Text(
                text = "Scripts: ${if (providerSettings.categoryFilters.allowedScripts.isEmpty()) {
                    "All"
                } else {
                    providerSettings.categoryFilters.allowedScripts
                        .joinToString(
                            ", ",
                        ) { it.displayName }
                }}",
                style = styles.bodyMedium,
                color = CinemaTextSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CinemaPrimaryButton(
                onClick = onManageFiltersClick,
                text = "Manage Filters",
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // Enable Caching
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tvFocusableNoScale(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Caching",
                    style = styles.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Cache categories and streams for faster loading",
                    style = styles.bodySmall,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
            Switch(
                checked = providerSettings.cachingEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings(providerSettings.copy(cachingEnabled = enabled))
                },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = CinemaAccent,
                        checkedTrackColor = CinemaAccent.copy(alpha = CinemaAlpha.tint),
                        uncheckedThumbColor = CinemaTextSecondary,
                        uncheckedTrackColor = CinemaSurfaceVariant,
                    ),
            )
        }
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

    Column {
        Text(
            text = "Last Watched Queue Size",
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Items to keep in Last Watched category (1-100)",
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
                    text = "Edit",
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
                    label = { Text("Queue Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(TvDimensions.selectionListWidth.scaled(scale)),
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                CinemaSecondaryButton(
                    onClick = {
                        isEditing = false
                        newSize = ""
                    },
                    text = "Cancel",
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
                    text = "Save",
                )
            }
        }
    }
}

@Composable
private fun FavoritesMaxSizeSetting(
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

    Column {
        Text(
            text = "Favorites Max Size",
            style = styles.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Maximum number of favorites to store (10-500)",
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
                    text = "Edit",
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
                    label = { Text("Max Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(TvDimensions.selectionListWidth.scaled(scale)),
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                CinemaSecondaryButton(
                    onClick = {
                        isEditing = false
                        newSize = ""
                    },
                    text = "Cancel",
                )
                Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                CinemaPrimaryButton(
                    onClick = {
                        val size = newSize.toIntOrNull()
                        if (size != null && size in 10..500) {
                            onSizeChanged(size)
                            isEditing = false
                            newSize = ""
                        }
                    },
                    enabled = newSize.toIntOrNull()?.let { it in 10..500 } == true,
                    text = "Save",
                )
            }
        }
    }
}
