@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvRadioRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing

/** One choice in a [SettingsPickerPane]; [preview] is a small static sample shown on the right. */
data class PickerOption<T>(
    val label: String,
    val value: T,
    val preview: (@Composable () -> Unit)? = null,
)

/**
 * Drill-in picker for a choice setting (plan Part I, B, "value row + drill-in picker"): a
 * `‹ Title` header and a one-column list of radio rows in place of the group's rows. Focus opens
 * on the selected option; OK applies and leaves, Back and Left leave unchanged (Left = back one
 * level, the TV focus contract). Back is taken in `onPreviewKeyEvent` on the pane root
 * (docs/NAVIGATION_GUIDE.md, "TV Back on Detail Screens"), with [BackHandler] as the fallback;
 * Left is taken there too, before the enclosing `tvPane` could hand it to the rail.
 */
@Composable
fun <T> SettingsPickerPane(
    title: String,
    options: List<PickerOption<T>>,
    selectedValue: T,
    onPick: (T) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val selectedFocusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        selectedFocusRequester.requestFocusWithRetry()
    }

    Column(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Back -> {
                        if (event.type == KeyEventType.KeyUp) onBack()
                        true
                    }

                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) onBack()
                        true
                    }

                    else -> {
                        false
                    }
                }
            },
    ) {
        Text(
            text = "‹ $title",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            itemsIndexed(options) { index, option ->
                val selected = index == selectedIndex
                val rowModifier =
                    Modifier.fillMaxWidth().then(
                        if (selected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                    )
                val pick = {
                    onPick(option.value)
                    onBack()
                }
                val preview = option.preview
                if (preview == null) {
                    TvRadioRow(selected = selected, onClick = pick, label = option.label, modifier = rowModifier)
                } else {
                    PickerPreviewRow(selected = selected, onClick = pick, label = option.label, preview = preview, modifier = rowModifier)
                }
            }
        }
    }
}

/** [TvRadioRow] with a trailing preview slot, which the shared row does not offer. */
@Composable
private fun PickerPreviewRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    preview: @Composable () -> Unit,
    modifier: Modifier,
) {
    TvInputListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor = CinemaAccent,
                        unselectedColor = CinemaTextSecondary,
                    ),
            )
        },
        trailingContent = preview,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
    }
}
