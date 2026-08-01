package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceLight
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons

@Composable
fun TvSearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    showClearButton: Boolean = query.isNotEmpty(),
) {
    val clearFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    val scale = LocalUiScale.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.sm.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder, color = CinemaTextPrimary.copy(alpha = 0.6f)) },
            singleLine = true,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (showClearButton) {
                                        clearFocusRequester.requestFocus()
                                    } else {
                                        submitFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
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
            leadingIcon = {
                Icon(
                    imageVector = CinemaIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                    tint = CinemaTextPrimary
                )
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                KeyboardActions(
                    onSearch = { onSearchSubmit() },
                ),
        )

        if (showClearButton) {
            CinemaIconButton(
                onClick = onClear,
                modifier = Modifier
                    .focusRequester(clearFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    focusRequester.requestFocus()
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    submitFocusRequester.requestFocus()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                icon = {
                    Icon(
                        imageVector = CinemaIcons.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(TvDimensions.iconSmall.scaled(scale)),
                        tint = CinemaTextPrimary
                    )
                }
            )
        }

        CinemaIconButton(
            onClick = onSearchSubmit,
            modifier = Modifier
                .focusRequester(submitFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (query.isNotEmpty()) {
                                    clearFocusRequester.requestFocus()
                                } else {
                                    focusRequester.requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            icon = {
                Icon(
                    imageVector = CinemaIcons.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(TvDimensions.iconMedium.scaled(scale)),
                    tint = CinemaTextPrimary
                )
            }
        )
    }

    // Auto-focus on screen open
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
