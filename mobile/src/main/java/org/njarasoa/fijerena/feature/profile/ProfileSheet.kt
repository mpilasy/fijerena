package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The profile avatar's sheet on a tab root: the profiles, the one in use marked — tapping another
 * switches to it and then calls [onProfileChosen], for the caller to start over on its last tab —
 * and Settings, where profiles are added and edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    viewModel: ProfilesViewModel,
    onProfileChosen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val switchingTo by viewModel.switchingTo.collectAsStateWithLifecycle()
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ProvideUiScaledDensity {
            Column(modifier = Modifier.padding(bottom = CinemaSpacing.md)) {
                val switchingName = switchingTo
                if (switchingName != null) {
                    // A switch can take a while (it re-applies the profile's category filters): say
                    // so, instead of a list that looks like it ignored the tap.
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.profile_switching, switchingName)) },
                        leadingContent = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MobileDimensions.iconLarge),
                                strokeWidth = MobileDimensions.strokeWidth,
                            )
                        },
                        colors = rowColors,
                    )
                } else {
                    profiles.forEach { profile ->
                        ListItem(
                            headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                ProfileAvatar(
                                    name = profile.name,
                                    colorIndex = profile.colorIndex,
                                    size = MobileDimensions.iconLarge,
                                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                )
                            },
                            trailingContent =
                                if (profile.isActive) {
                                    {
                                        Icon(
                                            CinemaIcons.CheckCircle,
                                            stringResource(R.string.provider_active_label),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    null
                                },
                            colors = rowColors,
                            modifier =
                                Modifier.clickable {
                                    if (profile.isActive) onDismiss() else viewModel.switchTo(profile.id, onProfileChosen)
                                },
                        )
                    }
                }
            }
        }
    }
}
