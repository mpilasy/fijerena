@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import org.njarasoa.fijerena.core.ui.components.CinemaRadioButton
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PanelCorner
import org.njarasoa.fijerena.core.ui.components.PanelFill
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.panel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.TvContentText
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.components.input.TvRadioRow
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * One choice in a [SettingsPickerPane]; [preview] is a small static sample shown on the right;
 * [inLook] wraps the row (a Look's row drawn in that Look).
 */
data class PickerOption<T>(
    val label: String,
    val value: T,
    val preview: (@Composable () -> Unit)? = null,
    val inLook: (@Composable (@Composable () -> Unit) -> Unit)? = null,
)

/**
 * Drill-in picker for a choice setting (plan Part I, B, "value row + drill-in picker"): a
 * `‹ Title` header and a one-column list of radio rows in place of the group's rows. Focus opens on
 * the selected option; OK applies and leaves, Back and Left leave unchanged (Left = back one level,
 * the TV focus contract). Back is taken in `onPreviewKeyEvent` on the pane root
 * (docs/NAVIGATION_GUIDE.md, "TV Back on Detail Screens"), with [BackHandler] as the fallback; Left
 * is taken there too, before the enclosing `tvPane` could hand it to the rail. [paneLook]: the
 * whole pane drawn in the focused option's look, as a preview; nothing applies until OK.
 */
@Composable
fun <T> SettingsPickerPane(
    title: String,
    options: List<PickerOption<T>>,
    selectedValue: T,
    onPick: (T) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    paneLook: (@Composable (T, @Composable () -> Unit) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    var focusedValue by remember { mutableStateOf(selectedValue) }
    if (paneLook == null) {
        PickerColumn(title, options, selectedValue, onPick, onBack, modifier) { }
    } else {
        paneLook(focusedValue) {
            // The preview theme's own background, so a light theme reads as light.
            PickerColumn(
                title,
                options,
                selectedValue,
                onPick,
                onBack,
                modifier.panel(PanelFill.Ground, PanelCorner.Card).padding(Spacing.sm),
            ) { focusedValue = it }
        }
    }
}

@Composable
private fun <T> PickerColumn(
    title: String,
    options: List<PickerOption<T>>,
    selectedValue: T,
    onPick: (T) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    onFocusOption: (T) -> Unit,
) {
    val selectedFocusRequester = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    // Opens scrolled to the selected option: one below the first screen is not composed yet, so its
    // focus request would fail.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
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
        CinemaText(
            "‹ $title",
            modifier = Modifier.padding(horizontal = Spacing.xs),
            role = TextRole.Title,
            tone = TextTone.Primary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            itemsIndexed(options) { index, option ->
                val selected = index == selectedIndex
                val rowModifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) onFocusOption(option.value) }
                        .then(if (selected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                val pick = {
                    onPick(option.value)
                    onBack()
                }
                val preview = option.preview
                val row: @Composable () -> Unit = {
                    // A row with a preview names a theme or a Look: a heading, where its title font shows.
                    TvRadioRow(
                        selected = selected,
                        onClick = pick,
                        label = option.label,
                        modifier = rowModifier,
                        labelIsName = preview != null,
                        trailing = preview,
                    )
                }
                option.inLook?.invoke(row) ?: row()
            }
        }
    }
}
