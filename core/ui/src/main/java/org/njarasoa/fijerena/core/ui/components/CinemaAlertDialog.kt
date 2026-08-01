package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.njarasoa.fijerena.core.ui.theme.DialogPosition
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/**
 * Themed replacement for [AlertDialog] — branches on the active
 * [org.njarasoa.fijerena.core.ui.theme.UiStyle]'s [org.njarasoa.fijerena.core.ui.theme.UiDialogTokens.position]
 * so Cupertino renders as a bottom sheet and Roku renders full-bleed, matching each platform's
 * real dialog convention; Material/BRAVIA stay a centered [AlertDialog]. Corner radius and scrim
 * alpha come from the same tokens. Param list intentionally mirrors [AlertDialog]'s slot-based
 * overload so migrating a call site is a rename, not a rewrite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinemaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    properties: DialogProperties = DialogProperties(),
) {
    val tokens = LocalUiStyle.current.dialog
    val shape = RoundedCornerShape(tokens.cornerRadius)

    when (tokens.position) {
        DialogPosition.CENTERED -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                shape = shape,
                containerColor = containerColor,
                iconContentColor = iconContentColor,
                titleContentColor = titleContentColor,
                textContentColor = textContentColor,
                properties = properties,
            )
            ApplyDialogScrim(tokens.scrimAlpha)
        }

        DialogPosition.FULL_BLEED -> {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties =
                    DialogProperties(
                        dismissOnBackPress = properties.dismissOnBackPress,
                        dismissOnClickOutside = properties.dismissOnClickOutside,
                        securePolicy = properties.securePolicy,
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = properties.decorFitsSystemWindows,
                    ),
            ) {
                ApplyDialogScrim(tokens.scrimAlpha)
                Surface(
                    modifier = modifier.fillMaxSize(),
                    shape = RectangleShape,
                    color = containerColor,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            icon?.let {
                                CompositionLocalProvider(LocalContentColor provides iconContentColor, content = it)
                                Spacer(Modifier.height(8.dp))
                            }
                            title?.let {
                                CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                                    ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            text?.let {
                                CompositionLocalProvider(LocalContentColor provides textContentColor) {
                                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            dismissButton?.invoke()
                            if (dismissButton != null) Spacer(Modifier.width(8.dp))
                            confirmButton()
                        }
                    }
                }
            }
        }

        DialogPosition.BOTTOM_SHEET -> {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                sheetState = rememberModalBottomSheetState(),
                shape = shape,
                containerColor = containerColor,
                scrimColor = Color.Black.copy(alpha = tokens.scrimAlpha),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    icon?.let {
                        CompositionLocalProvider(LocalContentColor provides iconContentColor, content = it)
                        Spacer(Modifier.height(8.dp))
                    }
                    title?.let {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    text?.let {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.invoke()
                        if (dismissButton != null) Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Sets the underlying dialog window's dim amount to [alpha] — [AlertDialog]/[Dialog] have no
 * direct scrim-alpha param, unlike [ModalBottomSheet]'s `scrimColor`, so this reaches into the
 * window directly. Must be called from within a [Dialog]'s content.
 */
@Composable
private fun ApplyDialogScrim(alpha: Float) {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(alpha)
    }
}
