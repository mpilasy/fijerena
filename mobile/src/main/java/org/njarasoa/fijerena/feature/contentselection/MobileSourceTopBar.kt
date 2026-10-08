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
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.home.SourceSyncStatus
import org.njarasoa.fijerena.core.ui.home.sourceSyncStatus
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.contentselection.components.MobileSourceLine
import org.njarasoa.fijerena.feature.profile.ProfileSheet
import org.njarasoa.fijerena.ui.components.buttons.IconAction
import org.njarasoa.fijerena.ui.theme.CinemaError
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * The source the phone's tabs show, as their top bar and the bottom bar need it
 * (docs/plans/20261007_phone-home-overhaul-plan.md → Redesign: no Home on the phone).
 */
data class ActiveSource(
    val id: Long,
    val name: String,
    val type: String,
    /**
     * A Jellyfin server this profile hasn't signed in to: each profile is its own Jellyfin user
     * (docs/plans/archive/20260929_live-sync-plan.md → User profiles). The tab roots show a
     * sign-in panel in place of the library.
     */
    val needsSignIn: Boolean,
    /** The sections the source has; null while [needsSignIn] (no repository is built for it). */
    val supportedTypes: Set<String>?,
    /** The source has Live TV but its last sync found no channels (see [loadActiveSource]). */
    val noChannels: Boolean = false,
) {
    /**
     * The sections the tabs offer: [supportedTypes] without a Live TV that has no channels — as on
     * TV, where that card is dimmed and skipped.
     */
    val sections: Set<String>?
        get() = if (noChannels) supportedTypes?.minus(ContentType.LIVE_TV) else supportedTypes
}

/**
 * The active source, or null with none. Its sections are read from the app-wide repository's
 * provider — kept from [known] when it is the same source, so a re-read is only a database lookup.
 */
suspend fun loadActiveSource(
    context: Context,
    known: ActiveSource? = null,
): ActiveSource? =
    withContext(Dispatchers.IO) {
        val providerRepo = ProviderRepository(context.applicationContext)
        val provider = providerRepo.getActiveProvider()
        if (provider == null) {
            null
        } else {
            // No repository for a Jellyfin server without a login: it could only fail to authenticate.
            val needsSignIn = !providerRepo.hasLogin(provider)
            val supportedTypes =
                when {
                    needsSignIn -> {
                        null
                    }

                    known?.id == provider.id && known.supportedTypes != null -> {
                        known.supportedTypes
                    }

                    else -> {
                        AppContainer
                            .getInstance(context.applicationContext)
                            .getMediaRepository(provider.id)
                            .getProvider()
                            ?.capabilities
                            ?.supportedContentTypes
                    }
                }
            // Re-read on every call, so channels a later sync brings show the tab again. Only for
            // Xtream once it has synced: a count of its stored live categories, no network call.
            // Not for M3U (listing its channels downloads the playlist) nor Jellyfin (no Live TV).
            val noChannels =
                provider.type == "XTREAM" &&
                    provider.lastSyncedAtMs > 0 &&
                    supportedTypes?.contains(ContentType.LIVE_TV) == true &&
                    (
                        AppContainer
                            .getInstance(context.applicationContext)
                            .getMediaRepository(provider.id)
                            .getProvider() as? XtreamMediaProvider
                    )?.getCategoryTotalCount(ContentType.LIVE_TV) == 0
            ActiveSource(provider.id, provider.name, provider.type, needsSignIn, supportedTypes, noChannels)
        }
    }

/**
 * The top bar of every tab root on the phone: no back arrow; [title] (the section's name) over one
 * line with the source and its sync status, which switches source when there are two or more. Then
 * Search the guide ([onSearchGuide], Live TV only, once the guide index is ready), the section's
 * Search ([onSearch]; null while signed out of the source) and the profile avatar, whose sheet
 * switches profile ([onProfileChosen] runs after the switch) and opens Settings. [onSourcePicked]
 * runs after another source was made the active one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSourceTopBar(
    title: String,
    source: ActiveSource?,
    onSourcePicked: () -> Unit,
    onSearch: (() -> Unit)?,
    onSearchGuide: (() -> Unit)?,
    onProfileChosen: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sourceId = source?.id ?: 0L
    var allProviders by remember { mutableStateOf<List<ProviderEntity>>(emptyList()) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    // The status on the source line (phone home overhaul plan, Phase 2): the source's last
    // catalogue sync, re-read when a sync of it ends and on every ON_RESUME.
    var lastSyncedAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncError by remember { mutableStateOf<String?>(null) }
    var syncStatsReload by remember { mutableIntStateOf(0) }
    var showSyncError by remember { mutableStateOf(false) }
    val runningSyncs by ProviderSyncRunner.running.collectAsStateWithLifecycle(initialValue = emptySet())
    val syncing = sourceId in runningSyncs
    val syncStatus = sourceSyncStatus(syncing, lastSyncedAtMs, lastSyncError)

    // Collected live, so a guide that finishes indexing while the tab is up shows the button at once.
    val epgIndexState by remember { EpgIndexer.getInstance(context.applicationContext).state }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is EpgIndexState.Indexed

    LaunchedEffect(sourceId) {
        allProviders = withContext(Dispatchers.IO) { ProviderRepository(context.applicationContext).getAllProvidersList() }
    }

    LaunchedEffect(sourceId, syncing, syncStatsReload) {
        if (sourceId == 0L || syncing) return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) { ProviderRepository(context.applicationContext).getProviderById(sourceId) }
        if (provider != null) {
            lastSyncedAtMs = provider.lastSyncedAtMs
            lastSyncError = provider.lastSyncError
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) syncStatsReload++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val appName = stringResource(R.string.login_app_name)
    val displayName =
        buildString {
            append(source?.name.orEmpty().ifEmpty { appName })
            if (appSettings.isDevMode && source != null) {
                append(" (${source.type})")
            }
        }
    // The picker only opens with two or more sources; with one, the line opens the failure's reason.
    val hasPicker = allProviders.size > 1
    val onSourceLineClick: (() -> Unit)? =
        when {
            hasPicker -> {
                { showProviderPicker = true }
            }

            syncStatus == SourceSyncStatus.FAILED -> {
                { showSyncError = true }
            }

            else -> {
                null
            }
        }
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MobileSourceLine(
                    name = displayName,
                    status = syncStatus,
                    lastSyncedAtMs = lastSyncedAtMs,
                    showsPicker = hasPicker,
                    onClick = onSourceLineClick,
                )
            }
        },
        // The title plus the source line's 48 dp touch row, whatever the status.
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
            onSettings = {
                showProfileSheet = false
                onSettings()
            },
            onDismiss = { showProfileSheet = false },
        )
    }

    if (showSyncError) {
        // The line only says "Update failed"; the reason is here. It already carries the raw detail
        // in developer mode (ProviderSyncRunner).
        CinemaAlertDialog(
            onDismissRequest = { showSyncError = false },
            title = { Text(stringResource(R.string.home_source_update_failed)) },
            text = { Text(lastSyncError.orEmpty()) },
            confirmButton = {
                CinemaDialogTextButton(onClick = { showSyncError = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    if (showProviderPicker && hasPicker) {
        CinemaAlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text(stringResource(R.string.content_switch_provider_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    // The line only says "Update failed"; the reason is here, as on TV. It already
                    // carries the raw detail in developer mode (ProviderSyncRunner).
                    lastSyncError?.takeIf { syncStatus == SourceSyncStatus.FAILED }?.let { error ->
                        Text(
                            text = stringResource(R.string.home_source_update_failed_detail, error),
                            color = CinemaError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = CinemaSpacing.sm),
                        )
                    }
                    allProviders.forEach { provider ->
                        val isActive = provider.id == sourceId
                        val label =
                            if (appSettings.isDevMode) {
                                "${provider.name} (${provider.type})"
                            } else {
                                provider.name
                            }
                        Surface(
                            onClick = {
                                if (!isActive) {
                                    coroutineScope.launch {
                                        ProviderRepository(context.applicationContext).pickProvider(provider.id)
                                        showProviderPicker = false
                                        onSourcePicked()
                                    }
                                } else {
                                    showProviderPicker = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            shape = RoundedCornerShape(CinemaCornerRadius.small),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(CinemaSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (isActive) {
                                    Text(
                                        text = stringResource(R.string.provider_active_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                CinemaDialogTextButton(onClick = { showProviderPicker = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

/** In place of a tab's library while this profile has no login for the active Jellyfin server. */
@Composable
fun JellyfinSignInPanel(
    providerName: String,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(CinemaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_jellyfin_sign_in_prompt, providerName),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CinemaSpacing.lg))
        Button(onClick = onSignIn) { Text(stringResource(R.string.profile_jellyfin_sign_in_button)) }
    }
}
