package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.feature.provider.components.MobileCategoryFilterDialog
import org.njarasoa.fijerena.feature.settings.components.ProfileColourPicker
import org.njarasoa.fijerena.feature.settings.components.ProfileNameField
import org.njarasoa.fijerena.feature.settings.components.ProfileSwitchRow
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.SettingsScope
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A profile's page (`Screen.ProfileEdit`, D8): name, colour, its own Developer mode and Play next
 * episode automatically, Content filters › (the source in use, for this profile; Xtream only),
 * Switch to this profile, Save / Cancel, and Delete last. Content filters are saved by their own
 * editor's Save, at once; the rest by Save. See docs/plans/20261003_sources-guide-profiles-plan.md → P9.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileProfileEditScreen(
    profileId: String,
    // Settings' own: saving and deleting run in its scope, so leaving this page doesn't cancel them.
    viewModel: ProfilesViewModel,
    onBack: () -> Unit,
    onProfileSwitched: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val sourceInUse by viewModel.sourceInUse.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadSourceInUse() }
    val profile = profiles.firstOrNull { it.id == profileId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_dialog_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.player_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        // Until the list has loaded there is nothing to edit; a profile deleted elsewhere closes it.
        if (profile == null) {
            LaunchedEffect(profiles) { if (profiles.isNotEmpty()) onBack() }
        } else {
            val stored = remember(profileId) { viewModel.settingsOf(profileId) }
            var name by rememberSaveable(profileId) { mutableStateOf(profile.name) }
            var colorIndex by rememberSaveable(profileId) { mutableIntStateOf(profile.colorIndex) }
            var devMode by rememberSaveable(profileId) { mutableStateOf(stored.devMode) }
            var autoplayNextEpisode by rememberSaveable(profileId) { mutableStateOf(stored.autoplayNextEpisode) }
            var showNameError by remember { mutableStateOf(false) }
            var editingFilters by remember { mutableStateOf<CategoryFilters?>(null) }
            var confirmDelete by remember { mutableStateOf(false) }
            // Content filters act on Xtream catalogues only (as they did in Edit Source).
            val filtersSource = sourceInUse?.takeIf { it.type == "XTREAM" }
            // The profile in use and the last one left can't be deleted (ProfileRepository refuses both).
            val canDelete = !profile.isActive && profiles.size > 1

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            ) {
                ProfileNameField(
                    name = name,
                    onNameChange = {
                        name = it
                        showNameError = false
                    },
                    showError = showNameError,
                )
                ProfileColourPicker(colorIndex = colorIndex, onPick = { colorIndex = it })
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_developer_mode_title),
                    checked = devMode,
                    onCheckedChange = { devMode = it },
                )
                ProfileSwitchRow(
                    title = stringResource(R.string.settings_autoplay_next_episode_title),
                    checked = autoplayNextEpisode,
                    onCheckedChange = { autoplayNextEpisode = it },
                )
                if (filtersSource != null) {
                    SettingsListRow(
                        title = stringResource(R.string.profile_content_filters_title),
                        summary = filtersSource.name,
                        scope = SettingsScope.SOURCE,
                        onClick = { viewModel.loadCategoryFilters(filtersSource.id, profileId) { editingFilters = it } },
                    )
                }
                // Switching to the profile already in use would do nothing.
                if (!profile.isActive) {
                    CinemaOutlinedButton(
                        onClick = { viewModel.switchTo(profileId, onProfileSwitched) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.profile_switch_to_button)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    CinemaOutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    CinemaButton(
                        onClick = {
                            if (name.isBlank()) {
                                showNameError = true
                            } else {
                                viewModel.updateProfile(
                                    profileId,
                                    name,
                                    colorIndex,
                                    stored.copy(devMode = devMode, autoplayNextEpisode = autoplayNextEpisode),
                                )
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.profile_save_button)) }
                }
                if (canDelete) {
                    CinemaOutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_delete_button), color = CinemaError)
                    }
                }
            }

            val filters = editingFilters
            if (filters != null && filtersSource != null) {
                MobileCategoryFilterDialog(
                    title = stringResource(R.string.provider_section_content_filters_format, filtersSource.name),
                    currentFilters = filters,
                    onSave = { newFilters ->
                        viewModel.saveCategoryFilters(filtersSource.id, profileId, newFilters)
                        editingFilters = null
                    },
                    onDismiss = { editingFilters = null },
                )
            }

            if (confirmDelete) {
                CinemaAlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.profile_delete_confirm_title, profile.name)) },
                    text = { Text(stringResource(R.string.profile_delete_confirm_text)) },
                    confirmButton = {
                        CinemaDialogTextButton(onClick = {
                            confirmDelete = false
                            viewModel.deleteProfile(profileId)
                            onBack()
                        }) { Text(stringResource(R.string.profile_delete_button), color = CinemaError) }
                    },
                    dismissButton = {
                        CinemaDialogTextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
                    },
                )
            }
        }
    }
}
