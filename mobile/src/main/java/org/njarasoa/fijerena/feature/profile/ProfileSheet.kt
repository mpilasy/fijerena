package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ProvideUiScaledDensity
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.ui.components.PinDialog
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The profile menu's sheet (the dock's profile slot): the profiles, the one in use marked — tapping another
 * switches to it and then calls [onProfileChosen], for the caller to start over on its last tab —
 * and Settings, where profiles are added and edited. Under them, a "Browse <source>" row for each
 * of the profile's sources outside the merged tabs ([browse]: Jellyfin, M3U;
 * docs/plans/20261012_multi-source-search-history-plan.md → O1); [onBrowse] opens the one tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    viewModel: ProfilesViewModel,
    onProfileChosen: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    browse: List<BrowseSource> = emptyList(),
    onBrowse: (BrowseSource) -> Unit = {},
    /**
     * Asking who is watching (the profile in use was deleted on another device): only the
     * profiles, and the sheet stays until one is picked — no swipe, scrim tap or Back closes it.
     */
    mustChoose: Boolean = false,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val switchingTo by viewModel.switchingTo.collectAsStateWithLifecycle()
    val rowColors = ListItemDefaults.colors(containerColor = CinemaTheme.colors.clear)
    ModalBottomSheet(
        onDismissRequest = { if (!mustChoose) onDismiss() },
        sheetState = rememberModalBottomSheetState(confirmValueChange = { !mustChoose || it != SheetValue.Hidden }),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !mustChoose),
    ) {
        ProvideUiScaledDensity {
            Column(modifier = Modifier.padding(bottom = CinemaSpacing.md)) {
                val switchingName = switchingTo
                if (switchingName != null) {
                    // A switch can take a while (it re-applies the profile's category filters): say
                    // so, instead of a list that looks like it ignored the tap.
                    ListItem(
                        headlineContent = { CinemaText(stringResource(R.string.profile_switching, switchingName)) },
                        leadingContent = {
                            CinemaSpinner(modifier = Modifier.size(MobileDimensions.iconLarge))
                        },
                        colors = rowColors,
                    )
                } else {
                    profiles.forEach { profile ->
                        ListItem(
                            headlineContent = {
                                CinemaText(
                                    profile.name,
                                    role = TextRole.Heading,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                ProfileAvatar(
                                    name = profile.name,
                                    colorIndex = profile.colorIndex,
                                    size = MobileDimensions.iconLarge,
                                    fontSize = textStyleOf(TextRole.Label).fontSize,
                                )
                            },
                            trailingContent =
                                if (profile.isActive) {
                                    {
                                        Icon(
                                            CinemaIcons.CheckCircle,
                                            stringResource(R.string.provider_active_label),
                                            tint = CinemaTheme.colors.accent,
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
                    if (mustChoose) return@Column
                    ListItem(
                        headlineContent = { CinemaText(stringResource(R.string.settings_title)) },
                        leadingContent = { Icon(CinemaIcons.Settings, contentDescription = null) },
                        colors = rowColors,
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                                onSettings()
                            },
                    )
                    if (browse.isNotEmpty()) CinemaDivider()
                    browse.forEach { source ->
                        ListItem(
                            headlineContent = {
                                CinemaText(
                                    stringResource(R.string.browse_source, source.name),
                                    role = TextRole.Heading,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Icon(CinemaIcons.Folder, contentDescription = null) },
                            colors = rowColors,
                            modifier =
                                Modifier.clickable {
                                    onDismiss()
                                    onBrowse(source)
                                },
                        )
                    }
                }
            }
        }
    }
}
