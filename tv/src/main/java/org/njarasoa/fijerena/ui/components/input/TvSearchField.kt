package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * TV search field that opens the keyboard only on OK (UX overhaul plan Part II P4, R7).
 *
 * A focused Compose text field starts an input session, so on TV merely moving focus onto it —
 * Up from the results, entry focus — popped the keyboard, and the keyboard then took every D-pad
 * key: the buttons beside the field and the recent searches below it were out of reach.
 *
 * At rest ([editing] false) the field is a plain focus stop showing the query or [placeholder]:
 * Up/Down/Left/Right move focus like on any other control, so Right reaches the clear (×) and
 * search buttons, which are ordinary stops in the same row. **OK** asks for [editing]; the field
 * becomes a text field, takes focus and opens the keyboard. **IME Search/Done** submits; **Back**
 * submits too when the text changed. Either way the keyboard closes and focus comes back to the
 * resting field. Back is taken before the keyboard sees it ([onInterceptKeyBeforeSoftKeyboard]):
 * a TV keyboard otherwise eats it to hide itself and leaves focus in a text field with nothing on
 * screen. Moving focus out of the text field by any other way (Up/Down with no keyboard showing)
 * also ends editing, without pulling focus back.
 *
 * [editing] is hoisted so a screen can open on the keyboard (a first search with no history).
 * [focusRequester] targets the resting field; the caller's [modifier] goes on the whole row, so
 * `focusProperties { down = … }` there applies to the field and both buttons.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showClearButton: Boolean = query.isNotEmpty(),
) {
    val scale = LocalUiScale.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editFocusRequester = remember { FocusRequester() }
    // Set by the closes that should hand focus back to the resting field (Done, Back); left unset
    // when editing ends because focus already went somewhere else.
    var returnFocusOnClose by remember { mutableStateOf(false) }
    var hadEditFocus by remember { mutableStateOf(false) }
    var queryAtEditStart by remember { mutableStateOf(query) }

    fun close(submit: Boolean) {
        returnFocusOnClose = true
        onEditingChange(false)
        if (submit) onSearchSubmit()
    }

    LaunchedEffect(editing) {
        if (editing) {
            queryAtEditStart = query
            hadEditFocus = false
            // Focusing the text field opens the keyboard on its own; show() covers an IME that
            // waits for an explicit request.
            if (editFocusRequester.requestFocusWithRetry()) keyboard?.show()
        } else if (returnFocusOnClose) {
            returnFocusOnClose = false
            focusRequester.requestFocusWithRetry()
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        val searchIcon: @Composable () -> Unit = {
            Icon(
                imageVector = CinemaIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                tint = CinemaTextPrimary,
            )
        }

        if (editing) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder, color = CinemaTextPrimary.copy(alpha = 0.6f)) },
                singleLine = true,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(editFocusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                hadEditFocus = true
                            } else if (hadEditFocus) {
                                hadEditFocus = false
                                onEditingChange(false)
                            }
                        }.onInterceptKeyBeforeSoftKeyboard { event ->
                            val isBack = event.key == Key.Back || event.key == Key.Escape
                            if (isBack && event.type == KeyEventType.KeyUp) {
                                close(submit = query != queryAtEditStart && query.isNotBlank())
                            }
                            isBack
                        }.onPreviewKeyEvent { event ->
                            // No keyboard on screen (it was hidden by other means, or there is a
                            // hardware keyboard): OK brings the soft keyboard back.
                            val isOk = event.key == Key.DirectionCenter
                            if (isOk && event.type == KeyEventType.KeyUp) keyboard?.show()
                            isOk
                        }.tvDpadEscape(),
                shape = CircleShape,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedContainerColor = CinemaSurfaceVariant,
                        unfocusedContainerColor = CinemaSurfaceLight,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextPrimary.copy(alpha = 0.4f),
                    ),
                leadingIcon = searchIcon,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { close(submit = true) },
                        onDone = { close(submit = true) },
                    ),
            )
        } else {
            Surface(
                onClick = { onEditingChange(true) },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = OutlinedTextFieldDefaults.MinHeight)
                        .focusRequester(focusRequester),
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = CinemaSurfaceLight,
                        contentColor = CinemaTextPrimary,
                        focusedContainerColor = CinemaSurfaceVariant,
                        focusedContentColor = CinemaTextPrimary,
                        pressedContainerColor = CinemaSurfaceVariant,
                        pressedContentColor = CinemaTextPrimary,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border =
                    ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(TvFocusTokens.borderDefault, CinemaTextPrimary.copy(alpha = 0.4f))),
                        focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccent)),
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = Spacing.md.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
                ) {
                    searchIcon()
                    Text(
                        text = query.ifEmpty { placeholder },
                        color = if (query.isEmpty()) CinemaTextPrimary.copy(alpha = 0.6f) else CinemaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showClearButton) {
            CinemaIconButton(
                onClick = {
                    onClear()
                    // The button usually disappears with the text; don't let focus fall to the root.
                    focusRequester.requestFocus()
                },
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Close,
                        contentDescription = stringResource(R.string.provider_clear_button),
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        tint = CinemaTextPrimary,
                    )
                },
            )
        }

        CinemaIconButton(
            onClick = onSearchSubmit,
            icon = {
                Icon(
                    imageVector = CinemaIcons.Search,
                    contentDescription = stringResource(R.string.common_search),
                    modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                    tint = CinemaTextPrimary,
                )
            },
        )
    }
}
