package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.ProfileEditDialog
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * "Who's watching?" — pick the profile this phone uses. Reached from the home header's avatar;
 * mobile doesn't show it at launch. See `docs/plans/archive/20260929_live-sync-plan.md` → User profiles.
 * [onProfileChosen] runs once the switch has happened; the caller rebuilds the back stack.
 */
@Composable
fun ProfilePickerScreen(onProfileChosen: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val switchingTo by viewModel.switchingTo.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(MobileDimensions.safeMarginHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(CinemaSpacing.xl))
        Text(
            text = stringResource(R.string.profile_picker_title),
            style = MaterialTheme.typography.headlineMedium,
            color = CinemaTextPrimary,
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.xl))
        switchingTo?.let { name ->
            // A switch can take a while (it re-applies the profile's category filters): say so,
            // instead of a list that looks like it ignored the tap.
            androidx.compose.material3.CircularProgressIndicator()
            Spacer(modifier = Modifier.height(CinemaSpacing.md))
            Text(
                text = stringResource(R.string.profile_switching, name),
                style = MaterialTheme.typography.titleMedium,
                color = CinemaTextPrimary,
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(CinemaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            items(profiles, key = { it.id }) { profile ->
                PickerCard(label = profile.name, onClick = { viewModel.switchTo(profile.id, onProfileChosen) }) {
                    ProfileAvatar(
                        name = profile.name,
                        colorIndex = profile.colorIndex,
                        size = MobileDimensions.profilePickerAvatar,
                        fontSize = MaterialTheme.typography.displaySmall.fontSize,
                    )
                }
            }
            item {
                PickerCard(label = stringResource(R.string.settings_profiles_add), onClick = { adding = true }) {
                    Box(
                        modifier =
                            Modifier
                                .size(MobileDimensions.profilePickerAvatar)
                                .clip(CircleShape)
                                .background(CinemaSurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CinemaIcons.Add,
                            contentDescription = null,
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(MobileDimensions.iconXLarge),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        ProfileEditDialog(
            title = stringResource(R.string.profile_dialog_add_title),
            initialName = "",
            initialColorIndex = remember { viewModel.nextFreeColorIndex() },
            onSave = { name, color, _ ->
                viewModel.addProfile(name, color)
                adding = false
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun PickerCard(
    label: String,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(CinemaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        avatar()
        Spacer(modifier = Modifier.height(CinemaSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
