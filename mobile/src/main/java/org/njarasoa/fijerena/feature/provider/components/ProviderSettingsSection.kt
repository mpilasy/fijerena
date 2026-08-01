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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.sync.DriveSettingsSyncManager
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.components.chips.CinemaFilterChip
import org.njarasoa.fijerena.ui.theme.CinemaError

private const val CATEGORY_FILTER_PREVIEW_COUNT = 6

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
    favoritesMaxSize: String,
    newFavoritesMaxSize: String,
    isEditingFavoritesSize: Boolean,
    cachingEnabled: Boolean,
    categoryFilters: CategoryFilters,
    streamOutputFormat: String,
    playlistType: String,
    coroutineScope: CoroutineScope,
    providerRepo: ProviderRepository,
    syncManager: DriveSettingsSyncManager,
    onProviderSettingsChange: (ProviderSettings) -> Unit,
    onAutoResumeEnabledChange: (Boolean) -> Unit,
    onWatchHistorySizeChange: (String) -> Unit,
    onNewWatchHistorySizeChange: (String) -> Unit,
    onIsEditingQueueSizeChange: (Boolean) -> Unit,
    onFavoritesMaxSizeChange: (String) -> Unit,
    onNewFavoritesMaxSizeChange: (String) -> Unit,
    onIsEditingFavoritesSizeChange: (Boolean) -> Unit,
    onCachingEnabledChange: (Boolean) -> Unit,
    onStreamOutputFormatChange: (String) -> Unit,
    onPlaylistTypeChange: (String) -> Unit,
    onShowClearFavoritesDialogChange: (Boolean) -> Unit,
    onShowClearProgressDialogChange: (Boolean) -> Unit,
    onShowCategoryFilterDialogChange: (Boolean) -> Unit,
) {
    if (isEditMode) {
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(CinemaSpacing.md)) {
                Text(
                    text = "Provider Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                            text = "Auto-Resume",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Resume VOD content from where you left off",
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
                                syncManager.syncProviderSettings(editId)
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Watch History Size
                Text(text = "Last Watched Queue Size", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Items to keep in Last Watched category (1-100)",
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
                        }) { Text("Edit") }
                    }
                } else {
                    OutlinedTextField(
                        value = newWatchHistorySize,
                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) onNewWatchHistorySizeChange(it) },
                        label = { Text("Queue Size") },
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
                        }) { Text("Cancel") }
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
                                        syncManager.syncProviderSettings(editId)
                                    }
                                }
                            },
                            enabled = newWatchHistorySize.toIntOrNull()?.let { it in 1..100 } == true,
                        ) { Text("Save") }
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Favorites Max Size
                Text(text = "Favorites Max Size", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Maximum number of favorites to store (10-500)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))

                if (!isEditingFavoritesSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = favoritesMaxSize, style = MaterialTheme.typography.titleLarge)
                        CinemaOutlinedButton(onClick = {
                            onIsEditingFavoritesSizeChange(true)
                            onNewFavoritesMaxSizeChange(favoritesMaxSize)
                        }) { Text("Edit") }
                    }
                } else {
                    OutlinedTextField(
                        value = newFavoritesMaxSize,
                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) onNewFavoritesMaxSizeChange(it) },
                        label = { Text("Max Size") },
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
                            onIsEditingFavoritesSizeChange(false)
                            onNewFavoritesMaxSizeChange("")
                        }) { Text("Cancel") }
                        CinemaButton(
                            onClick = {
                                val size = newFavoritesMaxSize.toIntOrNull()
                                if (size != null && size in 10..500) {
                                    onFavoritesMaxSizeChange(size.toString())
                                    onIsEditingFavoritesSizeChange(false)
                                    onNewFavoritesMaxSizeChange("")
                                    coroutineScope.launch {
                                        val newSettings = providerSettings.copy(favoritesMaxSize = size)
                                        providerRepo.updateProviderSettings(editId, newSettings)
                                        onProviderSettingsChange(newSettings)
                                        syncManager.syncProviderSettings(editId)
                                    }
                                }
                            },
                            enabled = newFavoritesMaxSize.toIntOrNull()?.let { it in 10..500 } == true,
                        ) { Text("Save") }
                    }
                }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Clear Favorites
                Text(text = "Clear All Favorites", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Remove all favorited streams from all content types",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                CinemaButton(
                    onClick = { onShowClearFavoritesDialogChange(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear All Favorites") }

                Spacer(modifier = Modifier.height(CinemaSpacing.md))

                // Clear Progress
                Text(text = "Clear Playback Progress", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Remove all saved positions (Continue Watching will be empty)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                CinemaButton(
                    onClick = { onShowClearProgressDialogChange(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaError),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear All Progress") }

                // Xtream-only settings
                if (selectedType == ProviderType.XTREAM) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Stream Output Format
                    Text(text = "Stream Output Format", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Format used for live stream URLs (m3u8 = HLS, ts = MPEG-TS)",
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
                                        syncManager.syncProviderSettings(editId)
                                    }
                                },
                                label = { Text(format) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Playlist Type
                    Text(text = "Playlist Type", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Playlist format (m3u_plus = extended with EPG, simple = basic)",
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
                                        syncManager.syncProviderSettings(editId)
                                    }
                                },
                                label = { Text(type) },
                            )
                        }
                    }
                }

                // Category Filters (Xtream only)
                if (selectedType == ProviderType.XTREAM) {
                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    Text(text = "Category Filters", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Hide categories by prefix (e.g., 'Adult', 'XXX')",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                    Spacer(modifier = Modifier.height(CinemaSpacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Mode: ${categoryFilters.mode.name}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text =
                                    if (categoryFilters.rules.isEmpty()) {
                                        "No filters configured"
                                    } else {
                                        val preview = categoryFilters.rules.take(CATEGORY_FILTER_PREVIEW_COUNT).joinToString(", ") { it.value }
                                        val remaining = categoryFilters.rules.size - CATEGORY_FILTER_PREVIEW_COUNT
                                        val suffix = if (remaining > 0) ", +$remaining more" else ""
                                        "${categoryFilters.rules.size} prefix(es): $preview$suffix"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Scripts: ${if (categoryFilters.allowedScripts.isEmpty()) {
                                    "All"
                                } else {
                                    categoryFilters.allowedScripts
                                        .joinToString(
                                            ", ",
                                        ) { it.displayName }
                                }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        CinemaOutlinedButton(onClick = { onShowCategoryFilterDialogChange(true) }) { Text("Edit") }
                    }

                    Spacer(modifier = Modifier.height(CinemaSpacing.md))

                    // Enable Caching
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable Caching", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Enable caching for faster loading",
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
                                    syncManager.syncProviderSettings(editId)
                                }
                            },
                        )
                    }
                }
            } // Column
        } // GlassPanel
    }
}
