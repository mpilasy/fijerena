package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvSelectableButton
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.rememberFocusReturn
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.scaled

private const val CATEGORY_FILTER_PREVIEW_COUNT = 6

/** Entry row of the Edit Source settings column (its first focus stop). */
internal const val EDIT_SOURCE_FIRST_SETTING_KEY = "auto_resume"

/**
 * Left/Right inside a row of buttons: moves to the named neighbour and keeps the key from the
 * enclosing `tvPane`, which would otherwise take it to the other column. With no neighbour on
 * that side the key goes on to the pane.
 */
internal fun Modifier.rowNeighbours(
    left: FocusRequester? = null,
    right: FocusRequester? = null,
): Modifier =
    onKeyEvent { event ->
        val target =
            when {
                event.type != KeyEventType.KeyDown -> null
                event.key == Key.DirectionLeft -> left
                event.key == Key.DirectionRight -> right
                else -> null
            }
        target?.requestFocus(FocusDirection.Enter) ?: false
    }

/** Title (and optional dimmed subtitle) of one Edit Source section, left-aligned (T-11). */
@Composable
internal fun ProviderSectionTitle(
    title: String,
    subtitle: String? = null,
    titleColor: Color = CinemaAccent,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val titleStyle = remember(scale, typography) { typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize.scaled(scale)) }
    val subtitleStyle = remember(scale, typography) { typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale)) }
    Text(text = title, style = titleStyle, color = titleColor)
    subtitle?.let {
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        Text(
            text = it,
            style = subtitleStyle,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
    }
}

/**
 * Behaviour: this source's playback and caching settings, each saved as soon as it changes
 * (A-7). Every control is a row of [pane], the settings column of Edit Source.
 */
@Composable
fun ProviderSettingsSection(
    providerType: ProviderType,
    providerSettings: ProviderSettings,
    onUpdateSettings: (ProviderSettings) -> Unit,
    pane: PaneFocusState,
) {
    val scale = LocalUiScale.current

    ProviderSectionTitle(
        title = stringResource(R.string.provider_section_behaviour),
        subtitle = stringResource(R.string.settings_applies_immediately),
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    TvSwitchRow(
        checked = providerSettings.autoResumeEnabled,
        onCheckedChange = { enabled ->
            onUpdateSettings(providerSettings.copy(autoResumeEnabled = enabled))
        },
        label = stringResource(R.string.provider_auto_resume_label),
        description = stringResource(R.string.provider_auto_resume_desc),
        modifier = Modifier.paneItem(pane, EDIT_SOURCE_FIRST_SETTING_KEY),
    )

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    WatchHistorySizeSetting(
        currentSize = providerSettings.watchHistorySize,
        onSizeChanged = { size ->
            onUpdateSettings(providerSettings.copy(watchHistorySize = size))
        },
        pane = pane,
    )

    if (providerType == ProviderType.XTREAM) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        ChoiceSetting(
            title = stringResource(R.string.provider_stream_format_label),
            description = stringResource(R.string.provider_stream_format_desc),
            options = listOf("m3u8", "ts"),
            selected = providerSettings.streamOutputFormat,
            onSelect = { format -> onUpdateSettings(providerSettings.copy(streamOutputFormat = format)) },
            pane = pane,
            keyPrefix = "format",
        )

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        ChoiceSetting(
            title = stringResource(R.string.provider_playlist_type_label),
            description = stringResource(R.string.provider_playlist_type_desc),
            options = listOf("m3u_plus", "simple"),
            selected = providerSettings.playlistType,
            onSelect = { type -> onUpdateSettings(providerSettings.copy(playlistType = type)) },
            pane = pane,
            keyPrefix = "playlist",
        )

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        TvSwitchRow(
            checked = providerSettings.cachingEnabled,
            onCheckedChange = { enabled ->
                onUpdateSettings(providerSettings.copy(cachingEnabled = enabled))
            },
            label = stringResource(R.string.provider_enable_caching_label),
            description = stringResource(R.string.provider_enable_caching_desc),
            modifier = Modifier.paneItem(pane, "caching"),
        )
    }
}

/**
 * Content filters · <profile>: the active profile's category filters for this source (A-8,
 * Xtream only). [manageFocusRequester] lets the screen land on Manage filters.
 */
@Composable
fun ProviderFiltersSection(
    providerSettings: ProviderSettings,
    onManageFiltersClick: () -> Unit,
    pane: PaneFocusState,
    manageFocusRequester: FocusRequester,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val bodyMedium = remember(scale, typography) { typography.bodyMedium.copy(fontSize = typography.bodyMedium.fontSize.scaled(scale)) }

    // Filters are per profile: the title says whose these are.
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    ProviderSectionTitle(
        title =
            activeProfile?.let { stringResource(R.string.provider_section_content_filters_format, it.name) }
                ?: stringResource(R.string.provider_category_filters_title),
        subtitle = stringResource(R.string.provider_category_filters_desc),
    )
    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                stringResource(
                    R.string.provider_filter_mode_value,
                    if (providerSettings.categoryFilters.mode == FilterMode.EXCLUDE) {
                        stringResource(R.string.provider_filter_exclude)
                    } else {
                        stringResource(R.string.provider_filter_include)
                    },
                ),
            style = bodyMedium,
            color = CinemaTextPrimary,
        )
        Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
        Text(
            text =
                if (providerSettings.categoryFilters.rules.isEmpty()) {
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
            style = bodyMedium,
            color = CinemaTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        text =
            stringResource(
                R.string.provider_scripts_value,
                if (providerSettings.categoryFilters.allowedScripts.isEmpty()) {
                    stringResource(R.string.common_all)
                } else {
                    providerSettings.categoryFilters.allowedScripts.joinToString(", ") { it.displayName }
                },
            ),
        style = bodyMedium,
        color = CinemaTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
    CinemaPrimaryButton(
        onClick = onManageFiltersClick,
        text = stringResource(R.string.provider_manage_filters_button),
        modifier = Modifier.paneItem(pane, "filters").focusRequester(manageFocusRequester),
    )
}

/** A choice between a few values: title, description, then one left-aligned row of options. */
@Composable
private fun ChoiceSetting(
    title: String,
    description: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    pane: PaneFocusState,
    keyPrefix: String,
) {
    val scale = LocalUiScale.current
    val typography = MaterialTheme.typography
    val titleSmall = remember(scale, typography) { typography.titleSmall.copy(fontSize = typography.titleSmall.fontSize.scaled(scale)) }
    val bodySmall = remember(scale, typography) { typography.bodySmall.copy(fontSize = typography.bodySmall.fontSize.scaled(scale)) }
    val requesters = remember(options) { options.map { FocusRequester() } }
    Column {
        Text(text = title, style = titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = description,
            style = bodySmall,
            color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
        )
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
            options.forEachIndexed { index, option ->
                TvSelectableButton(
                    selected = option == selected,
                    onSelect = { onSelect(option) },
                    text = option,
                    modifier =
                        Modifier
                            .paneItem(pane, "$keyPrefix:$option")
                            .focusRequester(requesters[index])
                            .rowNeighbours(
                                left = requesters.getOrNull(index - 1),
                                right = requesters.getOrNull(index + 1),
                            ),
                )
            }
        }
    }
}

@Composable
private fun WatchHistorySizeSetting(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit,
    pane: PaneFocusState,
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
    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

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
            // Value then Edit, left-aligned, so Up/Down through the column stays on its left edge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentSize.toString(),
                    style = styles.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(Spacing.md.scaled(scale)))
                CinemaSecondaryButton(
                    onClick = {
                        isEditing = true
                        newSize = currentSize.toString()
                    },
                    text = stringResource(R.string.provider_edit_button),
                    modifier = Modifier.paneItem(pane, "history").focusRequester(editButtonFocusRequester),
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
                    modifier = Modifier.focusRequester(cancelFocusRequester).rowNeighbours(right = saveFocusRequester),
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
                    modifier = Modifier.focusRequester(saveFocusRequester).rowNeighbours(left = cancelFocusRequester),
                )
            }
        }
    }
}
