package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The profile's avatar with a small gear badge on its lower end corner, mostly outside it: the avatar opens the profile menu,
 * which has Settings (settings flow review, SF-D4). Each tab root's top bar shows it, last. A child's
 * profile menu has no Settings, so its avatar has no badge ([showSettingsBadge] false).
 */
@Composable
fun ProfileSettingsAvatar(
    name: String,
    colorIndex: Int,
    size: Dp,
    initialRole: TextRole,
    modifier: Modifier = Modifier,
    showSettingsBadge: Boolean = true,
) {
    Box(modifier = modifier) {
        ProfileAvatar(name = name, colorIndex = colorIndex, size = size, initialRole = initialRole)
        if (!showSettingsBadge) return@Box
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    // Mostly outside the avatar's corner: its letters reach the edge.
                    .offset(
                        x = MobileDimensions.profileSettingsBadge * BADGE_OUTSIDE_SHARE,
                        y = MobileDimensions.profileSettingsBadge * BADGE_OUTSIDE_SHARE,
                    ).size(MobileDimensions.profileSettingsBadge)
                    .clip(CinemaTheme.shapes.identity)
                    .background(CinemaTheme.colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            CinemaIcon(
                CinemaIcons.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tone = TextTone.Primary,
                modifier = Modifier.size(MobileDimensions.profileSettingsBadgeIcon),
            )
        }
    }
}

/** How much of the gear badge lies outside the avatar, across and down. */
private const val BADGE_OUTSIDE_SHARE = 0.6f
