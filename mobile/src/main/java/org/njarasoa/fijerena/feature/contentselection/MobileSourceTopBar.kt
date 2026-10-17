package org.njarasoa.fijerena.feature.contentselection

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.home.rememberProfileSyncStates
import org.njarasoa.fijerena.core.ui.home.syncSummary
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.contentselection.components.MobileSourcesStatusSheet
import org.njarasoa.fijerena.feature.contentselection.components.MobileSyncSummaryLine
import org.njarasoa.fijerena.feature.profile.ProfileSheet
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The top bar of every tab root on the phone: no back arrow; [title] (the section's name) over one
 * line for all the profile's sources (O3 of docs/plans/20261012_multi-source-search-history-plan.md:
 * "Updated 2 hours ago", "Updating 1 of 3…", "1 failed"), which opens a sheet with each source's
 * status and reason; nothing when no source syncs. Then Search the guide ([onSearchGuide], Live TV
 * only, once the guide index is ready), the section's Search ([onSearch]) and the profile avatar,
 * whose sheet switches profile ([onProfileChosen] runs after the switch) and opens Settings, and
 * lists a "Browse <source>" row per source outside the merged tabs ([sections]' Browse entries,
 * O1): [onBrowseSection] opens the section picked (a dialog asks which when it has several),
 * [onBrowseSignIn] the source's sign-in while this profile has no login for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSourceTopBar(
    title: String,
    sections: ProfileSections?,
    onSearch: (() -> Unit)?,
    onSearchGuide: (() -> Unit)?,
    onProfileChosen: () -> Unit,
    onBrowseSection: (providerId: Long, contentType: String) -> Unit = { _, _ -> },
    onBrowseSignIn: (providerId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showProfileSheet by remember { mutableStateOf(false) }
    // A Browse entry's sections, when it has more than one: picked in a dialog.
    var browseChoice by remember { mutableStateOf<Pair<BrowseSource, List<String>>?>(null) }

    // The summary line (phone home overhaul plan, Phase 2; one line for all sources since
    // multi-source Phase 6): re-read as each source's sync starts, ends and records its result.
    val syncStates = rememberProfileSyncStates()
    val syncSummary = remember(syncStates) { syncSummary(syncStates) }
    var showSourcesSheet by remember { mutableStateOf(false) }

    // Collected live, so a guide that finishes indexing while the tab is up shows the button at once.
    val epgIndexState by remember { EpgIndexer.getInstance(context.applicationContext).state }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is EpgIndexState.Indexed

    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MobileSyncSummaryLine(
                    summary = syncSummary,
                    onClick = { showSourcesSheet = true },
                )
            }
        },
        // The title plus the summary line's 48 dp touch row, whatever the status.
        expandedHeight = MobileDimensions.homeTopBarHeight,
        actions = {
            if (onSearchGuide != null && hasEpgData) {
                IconAction(onClick = onSearchGuide, icon = CinemaIcons.MenuBook, label = stringResource(R.string.epg_browser_title))
            }
            if (onSearch != null) {
                IconAction(onClick = onSearch, icon = CinemaIcons.Search, label = stringResource(R.string.common_search))
            }
            // Always shown, even with one profile, so profiles are discoverable.
            activeProfile?.let { profile ->
                val switchLabel = stringResource(R.string.profile_switch_description, profile.name)
                IconButton(
                    onClick = { showProfileSheet = true },
                    modifier = Modifier.semantics { contentDescription = switchLabel },
                ) {
                    ProfileAvatar(
                        name = profile.name,
                        colorIndex = profile.colorIndex,
                        size = MobileDimensions.iconLarge,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    )
                }
            }
        },
    )

    if (showProfileSheet) {
        ProfileSheet(
            viewModel = profilesViewModel,
            onProfileChosen = onProfileChosen,
            onDismiss = { showProfileSheet = false },
            browse = sections?.browse.orEmpty(),
            onBrowse = { browsed ->
                coroutineScope.launch {
                    when (val opening = ProfileSections.opening(context, browsed.providerId)) {
                        ProfileSections.Opening.SignIn -> {
                            onBrowseSignIn(browsed.providerId)
                        }

                        is ProfileSections.Opening.Sections -> {
                            when (opening.contentTypes.size) {
                                0 -> Unit
                                1 -> onBrowseSection(browsed.providerId, opening.contentTypes.first())
                                else -> browseChoice = browsed to opening.contentTypes
                            }
                        }
                    }
                }
            },
        )
    }

    browseChoice?.let { (browsed, contentTypes) ->
        CinemaAlertDialog(
            onDismissRequest = { browseChoice = null },
            title = { Text(stringResource(R.string.browse_source, browsed.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    contentTypes.forEach { contentType ->
                        Surface(
                            onClick = {
                                browseChoice = null
                                onBrowseSection(browsed.providerId, contentType)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        when (contentType) {
                                            ContentType.LIVE_TV -> R.string.provider_live_tv_label
                                            ContentType.MOVIES -> R.string.provider_movies_label
                                            else -> R.string.provider_tv_shows_label
                                        },
                                    ),
                                modifier = Modifier.padding(CinemaSpacing.md),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                CinemaDialogActionButton(onClick = { browseChoice = null }, kind = ButtonKind.Other) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showSourcesSheet) {
        MobileSourcesStatusSheet(states = syncStates, onDismiss = { showSourcesSheet = false })
    }
}

/**
 * In place of a tab's library while the profile has no merged sections: [message] over one button
 * per action — a "Browse <source>" per source (O1), or Add Source with none.
 */
@Composable
fun MobileSourcesPanel(
    message: String,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(CinemaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        actions.forEachIndexed { index, (label, onClick) ->
            Spacer(modifier = Modifier.height(if (index == 0) CinemaSpacing.lg else CinemaSpacing.sm))
            CinemaDialogActionButton(onClick = onClick, kind = if (index == 0) ButtonKind.Main else ButtonKind.Other) { Text(label) }
        }
    }
}
