package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.feature.settings.components.PickerOption
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerPane
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

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
 * The Behaviour choices that drill into a picker in place of the settings column, as Settings'
 * choice rows do (TV UI audit, X5): Recent row size, stream format, playlist type. [paneKey] is
 * the row's key in the settings pane.
 */
enum class EditSourcePicker(
    val paneKey: String,
) {
    HISTORY_SIZE("history"),
    STREAM_FORMAT("format"),
    PLAYLIST_TYPE("playlist"),
}

private val STREAM_FORMATS = listOf("m3u8", "ts")
private val PLAYLIST_TYPES = listOf("m3u_plus", "simple")

// The 25 default (ProviderSettings.watchHistorySize) is one of them, so a new source shows it selected.
private val HISTORY_SIZE_OPTIONS = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/** The stored size as shown: a preset as is, anything else (set before the picker, 1–100) marked custom. */
@Composable
private fun historySizeLabel(size: Int): String =
    if (size in HISTORY_SIZE_OPTIONS) size.toString() else stringResource(R.string.settings_custom_value_format, size.toString())

/**
 * Behaviour: this source's playback and caching settings, each saved as soon as it changes
 * (A-7). Every control is a row of [pane], the settings column of Edit Source. The choice rows
 * call [onOpenPicker]; [pickerRowFocus] gives each its requester, for focus to come back to it
 * when its picker closes.
 */
@Composable
fun ProviderSettingsSection(
    providerType: ProviderType,
    providerSettings: ProviderSettings,
    onUpdateSettings: (ProviderSettings) -> Unit,
    onOpenPicker: (EditSourcePicker) -> Unit,
    pickerRowFocus: (EditSourcePicker) -> FocusRequester,
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

    val choiceRow: @Composable (EditSourcePicker, String, String, String) -> Unit = { picker, title, description, value ->
        Spacer(modifier = Modifier.height(Spacing.xs))
        SettingsRow(
            title = title,
            description = description,
            value = value,
            onClick = { onOpenPicker(picker) },
            modifier = Modifier.paneItem(pane, picker.paneKey),
            focusRequester = pickerRowFocus(picker),
        )
    }

    choiceRow(
        EditSourcePicker.HISTORY_SIZE,
        stringResource(R.string.provider_watch_history_size_label),
        stringResource(R.string.provider_watch_history_size_desc),
        historySizeLabel(providerSettings.watchHistorySize),
    )

    if (providerType == ProviderType.XTREAM) {
        choiceRow(
            EditSourcePicker.STREAM_FORMAT,
            stringResource(R.string.provider_stream_format_label),
            stringResource(R.string.provider_stream_format_desc),
            providerSettings.streamOutputFormat,
        )
        choiceRow(
            EditSourcePicker.PLAYLIST_TYPE,
            stringResource(R.string.provider_playlist_type_label),
            stringResource(R.string.provider_playlist_type_desc),
            providerSettings.playlistType,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

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
 * The open [EditSourcePicker], drawn in place of the settings column: Settings' picker (radio
 * rows, focus on the current value; OK applies and closes, Left and Back close unchanged).
 */
@Composable
fun EditSourcePickerPane(
    picker: EditSourcePicker,
    providerSettings: ProviderSettings,
    onUpdateSettings: (ProviderSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (picker) {
        EditSourcePicker.HISTORY_SIZE -> {
            val current = providerSettings.watchHistorySize
            // A stored value outside the presets is kept as its own checked option, so nothing is lost.
            val sizes = (HISTORY_SIZE_OPTIONS + current).distinct().sorted()
            SettingsPickerPane(
                title = stringResource(R.string.provider_watch_history_size_label),
                options = sizes.map { PickerOption(historySizeLabel(it), it) },
                selectedValue = current,
                onPick = { size -> if (size != current) onUpdateSettings(providerSettings.copy(watchHistorySize = size)) },
                onBack = onBack,
                modifier = modifier,
            )
        }

        EditSourcePicker.STREAM_FORMAT -> {
            SettingsPickerPane(
                title = stringResource(R.string.provider_stream_format_label),
                options = STREAM_FORMATS.map { PickerOption(it, it) },
                selectedValue = providerSettings.streamOutputFormat,
                onPick = { format -> onUpdateSettings(providerSettings.copy(streamOutputFormat = format)) },
                onBack = onBack,
                modifier = modifier,
            )
        }

        EditSourcePicker.PLAYLIST_TYPE -> {
            SettingsPickerPane(
                title = stringResource(R.string.provider_playlist_type_label),
                options = PLAYLIST_TYPES.map { PickerOption(it, it) },
                selectedValue = providerSettings.playlistType,
                onPick = { type -> onUpdateSettings(providerSettings.copy(playlistType = type)) },
                onBack = onBack,
                modifier = modifier,
            )
        }
    }
}
