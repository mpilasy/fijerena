package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The profile's avatar with a small gear badge at its lower end: the avatar opens the profile menu,
 * which has Settings (settings flow review, SF-D4). The bottom bar's profile slot and the top bar's
 * avatar (when there is no bottom bar) use it.
 */
@Composable
fun ProfileSettingsAvatar(
    name: String,
    colorIndex: Int,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ProfileAvatar(name = name, colorIndex = colorIndex, size = size, fontSize = fontSize)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(MobileDimensions.profileSettingsBadge)
                    .clip(CinemaTheme.shapes.identity)
                    .background(CinemaTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                CinemaIcons.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = CinemaTheme.colors.textPrimary,
                modifier = Modifier.size(MobileDimensions.profileSettingsBadgeIcon),
            )
        }
    }
}
