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
import org.njarasoa.fijerena.core.network.provider.SourceShortNames
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.FieldKeyboards
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.feature.settings.components.PickerOption
import org.njarasoa.fijerena.feature.settings.components.SettingsPickerPane
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.TvSwitchRow
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/** Entry row of the Edit Source settings column (its first focus stop). */
internal const val EDIT_SOURCE_FIRST_SETTING_KEY = "short_name"

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
    titleTone: TextTone = TextTone.Primary,
) {
    val scale = LocalUiScale.current
    CinemaText(text = title, role = TextRole.Heading, tone = titleTone)
    subtitle?.let {
        Spacer(modifier = Modifier.height(Spacing.xxs.scaled(scale)))
        CinemaText(text = it, role = TextRole.BodySmall, tone = TextTone.Secondary)
    }
}

/**
 * The Advanced choices that drill into a picker in place of the settings column, as Settings'
 * choice rows do (TV UI audit, X5): stream format, playlist type. [paneKey] is the row's key in
 * the settings pane.
 */
enum class EditSourcePicker(
    val paneKey: String,
) {
    STREAM_FORMAT("format"),
    PLAYLIST_TYPE("playlist"),
}

private val STREAM_FORMATS = listOf("m3u8", "ts")
private val PLAYLIST_TYPES = listOf("m3u_plus", "simple")

/**
 * Badge: what the source's badge says instead of its name — the settings column's first section,
 * beside the name in Connection (the settings reorg plan, Phase 3).
 */
@Composable
fun SourceBadgeSection(
    providerSettings: ProviderSettings,
    shortName: ShortNameField,
    pane: PaneFocusState,
) {
    val scale = LocalUiScale.current
    ProviderSectionTitle(title = stringResource(R.string.provider_section_badge))
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
    ReadOnlyFieldWithEdit(
        value = providerSettings.shortName,
        onValueChange = { shortName.onChange(it.trim().take(SourceShortNames.MAX_LENGTH)) },
        label = stringResource(R.string.provider_short_name_label),
        keyboardOptions = FieldKeyboards.Code,
        placeholder = shortName.automatic,
        displayText =
            providerSettings.shortName.ifBlank {
                stringResource(R.string.provider_short_name_automatic, shortName.automatic)
            },
        modifier = Modifier.paneItem(pane, EDIT_SOURCE_FIRST_SETTING_KEY),
    )
    CinemaText(
        text = shortName.error ?: stringResource(R.string.provider_short_name_desc),
        role = TextRole.BodySmall,
        tone = if (shortName.error != null) TextTone.Danger else TextTone.Secondary,
    )
}

/**
 * Advanced (Xtream): how the source's streams are asked for, each saved as soon as it changes
 * (A-7). The rows call [onOpenPicker]; [pickerRowFocus] gives each its requester, for focus to
 * come back to it when its picker closes.
 */
@Composable
fun SourceAdvancedSection(
    providerSettings: ProviderSettings,
    onOpenPicker: (EditSourcePicker) -> Unit,
    pickerRowFocus: (EditSourcePicker) -> FocusRequester,
    pane: PaneFocusState,
) {
    val scale = LocalUiScale.current
    ProviderSectionTitle(
        title = stringResource(R.string.provider_section_advanced),
        subtitle = stringResource(R.string.settings_applies_immediately),
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
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
}

/**
 * The source badge's short name row: [automatic] is the one worked out from the name (shown while
 * the viewer has none), [error] why the last one typed wasn't saved.
 */
class ShortNameField(
    val automatic: String,
    val error: String?,
    val onChange: (String) -> Unit,
)

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
