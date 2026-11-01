package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaSpinner
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.ui.components.CinemaSheet
import org.njarasoa.fijerena.ui.components.LeaveChildPinDialog
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The profile menu's sheet (the top bar's profile avatar): the profiles, the one in use marked — tapping another
 * switches to it and then calls [onProfileChosen], for the caller to start over on its last tab —
 * and Settings (not for a child profile, which has none). Under them, a "Browse <source>" row for each
 * of the profile's sources outside the merged tabs ([browse]: Jellyfin, M3U;
 * docs/plans/20261012_multi-source-search-history-plan.md → O1); [onBrowse] opens the one tapped.
 */
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
    // Leaving a child profile asks an Expert's PIN (the child lock); Cancel keeps the child.
    LeaveChildPinDialog(viewModel, onProfileChosen)
    CinemaSheet(onDismissRequest = onDismiss, dismissible = !mustChoose) {
        Column(modifier = Modifier.padding(bottom = CinemaSpacing.md)) {
            val switchingName = switchingTo
            if (switchingName != null) {
                // A switch can take a while (it re-applies the profile's category filters): say
                // so, instead of a list that looks like it ignored the tap.
                SettingsListRow(
                    title = stringResource(R.string.profile_switching, switchingName),
                    leading = { CinemaSpinner(modifier = Modifier.size(MobileDimensions.iconLarge)) },
                )
            } else {
                profiles.forEach { profile ->
                    SettingsListRow(
                        title = profile.name,
                        titleRole = TextRole.Heading,
                        leading = {
                            ProfileAvatar(
                                name = profile.name,
                                colorIndex = profile.colorIndex,
                                size = MobileDimensions.iconLarge,
                                initialRole = TextRole.Label,
                            )
                        },
                        // The active profile's check; no chevron on the others: a tap switches, it opens no page.
                        trailing = {
                            if (profile.isActive) {
                                CinemaIcon(
                                    CinemaIcons.CheckCircle,
                                    stringResource(R.string.provider_active_label),
                                    tone = TextTone.Accent,
                                )
                            }
                        },
                        onClick = {
                            if (profile.isActive) onDismiss() else viewModel.switchTo(profile.id, onProfileChosen)
                        },
                    )
                }
                if (mustChoose) return@Column
                // A child profile has no Settings; it still switches profile above.
                val child = profiles.firstOrNull { it.isActive }?.let { viewModel.settingsOf(it.id).child } == true
                if (!child) {
                    SettingsListRow(
                        title = stringResource(R.string.settings_title),
                        leading = { CinemaIcon(CinemaIcons.Settings, contentDescription = null, tone = TextTone.Secondary) },
                        onClick = {
                            onDismiss()
                            onSettings()
                        },
                    )
                }
                if (browse.isNotEmpty()) CinemaDivider()
                browse.forEach { source ->
                    SettingsListRow(
                        title = stringResource(R.string.browse_source, source.name),
                        titleRole = TextRole.Heading,
                        leading = { CinemaIcon(CinemaIcons.Folder, contentDescription = null, tone = TextTone.Secondary) },
                        onClick = {
                            onDismiss()
                            onBrowse(source)
                        },
                    )
                }
            }
        }
    }
}
