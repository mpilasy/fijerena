package org.njarasoa.fijerena.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextDisabled
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * A filled text button: [ButtonKind.Main] unless [kind] says otherwise. Every phone text button
 * goes through [CinemaDialogActionButton], so they all look as
 * [org.njarasoa.fijerena.core.ui.theme.ButtonStyle] says.
 */
@Composable
fun CinemaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Main,
    content: @Composable RowScope.() -> Unit,
) = CinemaDialogActionButton(onClick = onClick, modifier = modifier, enabled = enabled, kind = kind, content = content)

/** Any other action than the screen's main one ([ButtonKind.Other]). */
@Composable
fun CinemaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = CinemaDialogActionButton(onClick = onClick, modifier = modifier, enabled = enabled, kind = ButtonKind.Other, content = content)

/** As [CinemaOutlinedButton]: a text-only button no longer looks different. */
@Composable
fun CinemaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = CinemaDialogActionButton(onClick = onClick, modifier = modifier, enabled = enabled, kind = ButtonKind.Other, content = content)

/**
 * Circular button with icon, uses primary color scheme, identical to TV's aesthetic.
 */
@Composable
fun CinemaIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(PhoneButtonStyle.iconButtonSize),
        enabled = enabled,
        shape = CinemaTheme.shapes.control,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = PhoneButtonStyle.iconContainer,
                contentColor = PhoneButtonStyle.iconContent,
                disabledContainerColor = PhoneButtonStyle.disabledContainer,
                disabledContentColor = PhoneButtonStyle.disabledContent,
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (enabled) PhoneButtonStyle.iconContent else PhoneButtonStyle.disabledContent,
        ) {
            icon()
        }
    }
}

/**
 * Secondary detail-screen action: a circular icon button with a short caption underneath it —
 * the Plex/Netflix "Favorite / Restart / Trailer" row under a detail screen's primary Play button.
 * Only actions the app actually supports belong in that row (no dead Cast/Shuffle buttons).
 */
@Composable
fun DetailIconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CinemaTheme.colors.textPrimary,
) {
    // A capped width and a two-line, centred label: at a large system font size the one-line
    // labels of a row of actions ran into each other ("Add to FavoritesMark as watched…").
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = DETAIL_ACTION_MAX_WIDTH),
    ) {
        CinemaIconButton(onClick = onClick, icon = {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        })
        CinemaText(
            text = label,
            role = TextRole.Caption,
            tone = TextTone.Secondary,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = CinemaSpacing.xxs),
        )
    }
}

@Composable
fun CinemaDangerIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(PhoneButtonStyle.iconButtonSize),
        enabled = enabled,
        shape = CinemaTheme.shapes.control,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = PhoneButtonStyle.dangerIconContainer,
                contentColor = PhoneButtonStyle.iconContent,
                disabledContainerColor = PhoneButtonStyle.disabledContainer,
                disabledContentColor = PhoneButtonStyle.disabledContent,
            ),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides PhoneButtonStyle.iconContent,
        ) {
            icon()
        }
    }
}

/** The widest a labelled detail action gets; its label wraps inside it. */
private val DETAIL_ACTION_MAX_WIDTH = MobileDimensions.detailActionMaxWidth
