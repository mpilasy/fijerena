@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncRunner
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.ContinueWatchingItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaDialogTextButton
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.ShimmerPlaceholder
import org.njarasoa.fijerena.core.ui.components.staggeredEntrance
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaGlassBorder
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaLive
import org.njarasoa.fijerena.core.ui.theme.CinemaOrange
import org.njarasoa.fijerena.core.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.contentselection.components.HomeClock
import org.njarasoa.fijerena.feature.contentselection.components.SourceSyncStatusLine
import org.njarasoa.fijerena.feature.contentselection.components.TvContinueWatchingShelf
import org.njarasoa.fijerena.feature.contentselection.components.sourceSyncStatus
import org.njarasoa.fijerena.ui.components.AmbientBackdrop
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.TvOptionRow
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.core.navigation.ContentType as NavContentType
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (NavContentType) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit = {},
    onEpgBrowser: () -> Unit = {},
    onProviderChanged: () -> Unit = {},
    onCapabilitiesResolved: (Set<String>) -> Unit = {},
    onContinueWatchingSelected: (ContinueWatchingItem) -> Unit = {},
    onChooseProfile: () -> Unit = {},
    onSignInRequired: (providerId: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val signInResources = LocalResources.current
    val profilesViewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val activeProfile by profilesViewModel.activeProfile.collectAsStateWithLifecycle()
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember {
        mutableStateOf<Set<String>>(
            setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
        )
    }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<org.njarasoa.fijerena.core.network.provider.ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    // The active provider is a Jellyfin server this profile hasn't signed in to: home shows a
    // sign-in panel in place of the library (docs/plans/archive/20260929_live-sync-plan.md → User profiles).
    var needsSignIn by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // The source pill's status (TV home overhaul plan, Phase 1): the active source's last catalogue
    // sync, re-read when a sync of it ends and on every ON_RESUME.
    var lastSyncedAtMs by remember { mutableLongStateOf(0L) }
    var lastSyncError by remember { mutableStateOf<String?>(null) }
    var syncStatsReload by remember { mutableIntStateOf(0) }
    val runningSyncs by ProviderSyncRunner.running.collectAsStateWithLifecycle(initialValue = emptySet())
    val syncing = activeProviderId in runningSyncs

    // Category counts per content type: Pair(filtered, total) — null while loading
    var liveTvCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moviesCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tvShowsCounts by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var mediaProviderRef by remember { mutableStateOf<MediaProvider?>(null) }
    var mediaRepositoryRef by remember { mutableStateOf<MediaRepository?>(null) }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }
    var continueWatchingItems by remember { mutableStateOf<List<ContinueWatchingItem>>(emptyList()) }

    // Show EPG Browser button when EPG index has data. Collected live (not a one-shot
    // `remember`) so a source that finishes indexing while this screen is on-screen shows the
    // icon immediately, instead of waiting for the composable to be torn down and rebuilt.
    val epgIndexState by remember {
        org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
            .getInstance(context.applicationContext)
            .state
    }.collectAsStateWithLifecycle()
    val hasEpgData = epgIndexState is org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexState.Indexed

    LaunchedEffect(refreshTrigger) {
        // Reset counts so stale values don't linger during provider switch
        liveTvCounts = null
        moviesCounts = null
        tvShowsCounts = null
        val resolvedTypes =
            withContext(Dispatchers.IO) {
                val providerRepo = ProviderRepository(context.applicationContext)
                allProviders = providerRepo.getAllProvidersList()
                val activeProvider = providerRepo.getActiveProvider()
                if (activeProvider != null) {
                    providerName = activeProvider.name
                    providerType = activeProvider.type
                    activeProviderId = activeProvider.id
                    lastSyncedAtMs = activeProvider.lastSyncedAtMs
                    lastSyncError = activeProvider.lastSyncError
                    // A Jellyfin server this profile hasn't signed in to: each profile is its own
                    // Jellyfin user (docs/plans/archive/20260929_live-sync-plan.md → User profiles). No
                    // repository is built — it could only fail to authenticate — and the first time
                    // per process the sign-in screen opens by itself; after that the panel stays.
                    needsSignIn = !providerRepo.hasLogin(activeProvider)
                    if (needsSignIn) {
                        mediaRepositoryRef = null
                        mediaProviderRef = null
                        if (AppContainer.getInstance(context.applicationContext).shouldPromptSignIn(activeProvider.id)) {
                            val message = signInResources.getString(R.string.profile_jellyfin_sign_in_prompt, activeProvider.name)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast
                                    .makeText(context, message, android.widget.Toast.LENGTH_LONG)
                                    .show()
                                onSignInRequired(activeProvider.id)
                            }
                        }
                        null
                    } else {
                        // Reuse the app-wide managed repository/provider instead of creating an
                        // unmanaged standalone one: same cached auth session, and connect() has
                        // already been run for it.
                        val repo = AppContainer.getInstance(context.applicationContext).getMediaRepository(activeProvider.id)
                        mediaRepositoryRef = repo
                        val mediaProvider = repo.getProvider()
                        if (mediaProvider != null) {
                            supportedContentTypes = mediaProvider.capabilities.supportedContentTypes
                            mediaProviderRef = mediaProvider
                        }
                        mediaProvider?.capabilities?.supportedContentTypes
                    }
                } else {
                    needsSignIn = false
                    providerName = appSettings.providerName
                    null
                }
            }
        resolvedTypes?.let(onCapabilitiesResolved)
    }

    LaunchedEffect(activeProviderId, syncing, syncStatsReload) {
        if (activeProviderId == 0L || syncing) return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) { ProviderRepository(context.applicationContext).getProviderById(activeProviderId) }
        if (provider != null) {
            lastSyncedAtMs = provider.lastSyncedAtMs
            lastSyncError = provider.lastSyncError
        }
    }

    // Pull a recently-watched poster for the ambient backdrop wash — falls back to the plain
    // gradient (AmbientBackdrop's default) if there's no watch history yet or the provider
    // doesn't support it.
    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            backdropImageUrl =
                listOf(ContentType.MOVIES, ContentType.TV_SHOWS, ContentType.LIVE_TV).firstNotNullOfOrNull { contentType ->
                    mp
                        .getRecentlyPlayed(contentType)
                        ?.getOrNull()
                        ?.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }
                        ?.thumbnailUrl
                }
        }
    }

    LaunchedEffect(mediaProviderRef) {
        val mp = mediaProviderRef ?: return@LaunchedEffect
        // getCategories() already excludes filtered-out categories at the DB layer, so its size
        // IS the visible count — the total (for "X of Y") needs the unfiltered count separately.
        val xtream = mp as? org.njarasoa.fijerena.core.network.XtreamMediaProvider
        withContext(Dispatchers.IO) {
            if (ContentType.LIVE_TV in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.LIVE_TV).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.LIVE_TV) ?: cats.size
                    liveTvCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.MOVIES in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.MOVIES).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.MOVIES) ?: cats.size
                    moviesCounts = Pair(cats.size, total)
                }
            }
            if (ContentType.TV_SHOWS in mp.capabilities.supportedContentTypes) {
                mp.getCategories(ContentType.TV_SHOWS).onSuccess { cats ->
                    val total = xtream?.getCategoryTotalCount(ContentType.TV_SHOWS) ?: cats.size
                    tvShowsCounts = Pair(cats.size, total)
                }
            }
        }
    }

    // "Jump Back In" shelf — reload whenever the repository changes (provider switch) and again
    // on every ON_RESUME, so returning from playback immediately reflects updated progress.
    LaunchedEffect(mediaRepositoryRef) {
        continueWatchingItems = mediaRepositoryRef?.getContinueWatchingItems() ?: emptyList()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaRepositoryRef) {
        val repo = mediaRepositoryRef
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) syncStatsReload++
                if (event == Lifecycle.Event.ON_RESUME && repo != null) {
                    coroutineScope.launch {
                        continueWatchingItems = repo.getContinueWatchingItems()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiScale by remember { mutableStateOf(appSettings.uiScale) }

    // Back from whatever a card or header button opened lands on that card or button, not on the
    // first focusable (the "Switch Source" chip). A Continue Watching card waits for the shelf to
    // reload and is scrolled into view first; a card that left the shelf (finished) falls back to
    // the first hero card.
    val returnFocus = rememberNavReturnFocus()
    val shelfListState = rememberLazyListState()

    // The hero cards' focus (UX overhaul plan Part II Phase 4). Home opens on the first card with
    // content, not the "Switch Source" chip (F-H-1). Down from any header button goes back to the
    // card focused last, default the leftmost: the buttons sit top-right, so the geometric search
    // picked the rightmost card and Up/Down were not reversible (F-H-2, R8). A Live TV card with
    // no channels is dimmed and cannot take focus (F-H-3).
    val heroCardFocus =
        remember {
            mapOf(RETURN_LIVE_TV to FocusRequester(), RETURN_MOVIES to FocusRequester(), RETURN_TV_SHOWS to FocusRequester())
        }
    var lastHeroCard by rememberSaveable { mutableStateOf<String?>(null) }
    val liveTvEmpty = liveTvCounts?.first == 0
    val heroCards = focusableHeroCards(supportedContentTypes, liveTvCounts)
    val headerDownCard = lastHeroCard?.takeIf { it in heroCards } ?: heroCards.firstOrNull()
    val heroFallbackFocus = heroCards.firstOrNull()?.let(heroCardFocus::getValue)
    LaunchedEffect(Unit) {
        // Back hands focus to the control that was left (NavReturnFocusEffect below).
        if (returnFocus.isReturn) return@LaunchedEffect
        // Wait for the Live TV count, so an empty Live TV card is not the one focused.
        withTimeoutOrNull(ENTRY_FOCUS_WAIT_MS) {
            snapshotFlow { needsSignIn || ContentType.LIVE_TV !in supportedContentTypes || liveTvCounts != null }
                .first { it }
        }
        if (needsSignIn) return@LaunchedEffect
        focusableHeroCards(supportedContentTypes, liveTvCounts)
            .firstOrNull()
            ?.let { heroCardFocus.getValue(it).requestFocusWithRetry() }
    }
    NavReturnFocusEffect(returnFocus, fallback = heroFallbackFocus) { key ->
        if (key.startsWith(RETURN_CONTINUE_WATCHING_PREFIX)) {
            val itemId = key.removePrefix(RETURN_CONTINUE_WATCHING_PREFIX)
            val items =
                withTimeoutOrNull(RETURN_SHELF_WAIT_MS) {
                    snapshotFlow { continueWatchingItems }.first { items -> items.any { it.id == itemId } }
                }
            val index = items?.indexOfFirst { it.id == itemId } ?: -1
            if (index >= 0) shelfListState.scrollToItem(index)
        }
    }
    val leaveTo: (String, () -> Unit) -> Unit = { key, navigate ->
        returnFocus.leaveFrom(key)
        navigate()
    }

    CompositionLocalProvider(LocalUiScale provides uiScale) {
        val scale = LocalUiScale.current

        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackdrop(modifier = Modifier.fillMaxSize(), imageUrl = backdropImageUrl)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = Spacing.tvSafeMarginHorizontal,
                            vertical = Spacing.tvSafeMarginVertical,
                        ),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = Spacing.lg.scaled(scale)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.staggeredEntrance(0),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                text = stringResource(R.string.login_app_name),
                                style =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontSize =
                                            MaterialTheme.typography.headlineSmall.fontSize
                                                .scaled(scale),
                                    ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            HomeClock()
                        }
                        Row(
                            // Applies to every button in the row (none is a focus group).
                            modifier =
                                if (needsSignIn || headerDownCard == null) {
                                    Modifier
                                } else {
                                    Modifier.focusProperties { down = heroCardFocus.getValue(headerDownCard) }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            // Shown with one source too, for its sync status; only a picker (and
                            // focusable) with two or more.
                            if (providerName.isNotEmpty()) {
                                val canPick = allProviders.size > 1
                                val syncStatus = sourceSyncStatus(syncing, lastSyncedAtMs, lastSyncError)
                                val displayName =
                                    if (appSettings.isDevMode && providerType.isNotEmpty()) {
                                        "$providerName ($providerType)"
                                    } else {
                                        providerName
                                    }
                                val switchProviderDescription =
                                    stringResource(R.string.content_switch_provider_description_format, displayName)
                                var providerPillFocused by remember { mutableStateOf(false) }
                                val pillScale by animateFloatAsState(
                                    targetValue = if (providerPillFocused) TvFocusTokens.focusedScaleSubtle else TvFocusTokens.defaultScale,
                                    animationSpec =
                                        tween(
                                            durationMillis = org.njarasoa.fijerena.core.ui.theme.CinemaAnimation.focusDurationMs,
                                        ),
                                    label = "provider_pill_scale",
                                )
                                GlassPanel(
                                    modifier =
                                        Modifier
                                            .scale(pillScale)
                                            .border(
                                                width = TvFocusTokens.focusBorderWidth,
                                                color =
                                                    if (providerPillFocused) {
                                                        CinemaAccentLight
                                                    } else {
                                                        androidx.compose.ui.graphics.Color.Transparent
                                                    },
                                                shape = RoundedCornerShape(CinemaCornerRadius.large),
                                            ).then(
                                                if (canPick) {
                                                    Modifier
                                                        .onFocusChanged { providerPillFocused = it.isFocused }
                                                        .clickable(role = Role.DropdownList) { showProviderPicker = true }
                                                        .semantics { contentDescription = switchProviderDescription }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = Spacing.md,
                                                vertical = Spacing.xs,
                                            ),
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                        )
                                        SourceSyncStatusLine(
                                            status = syncStatus,
                                            lastSyncedAtMs = lastSyncedAtMs,
                                            modifier = Modifier.padding(start = Spacing.sm),
                                        )
                                        if (canPick) {
                                            Icon(
                                                imageVector = CinemaIcons.ArrowDropDown,
                                                contentDescription = null,
                                                tint = if (providerPillFocused) CinemaTextPrimary else CinemaAccentLight,
                                                modifier = Modifier.padding(start = Spacing.xs),
                                            )
                                        }
                                    }
                                }
                            }
                            if (hasEpgData) {
                                CinemaIconButton(
                                    onClick = { leaveTo(RETURN_EPG_BROWSER, onEpgBrowser) },
                                    modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_EPG_BROWSER),
                                    icon = {
                                        Icon(
                                            imageVector = CinemaIcons.MenuBook,
                                            contentDescription = stringResource(R.string.epg_browser_title),
                                            tint = CinemaTextPrimary,
                                        )
                                    },
                                )
                            }
                            CinemaIconButton(
                                onClick = { leaveTo(RETURN_SEARCH, onSearch) },
                                modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_SEARCH),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Search,
                                        contentDescription = stringResource(R.string.content_search_all_description),
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                            // Always shown, even with one profile, so profiles are discoverable.
                            activeProfile?.let { profile ->
                                val switchLabel = stringResource(R.string.profile_switch_description, profile.name)
                                CinemaIconButton(
                                    onClick = { leaveTo(RETURN_PROFILE, onChooseProfile) },
                                    modifier =
                                        Modifier
                                            .semantics { contentDescription = switchLabel }
                                            .navReturnFocusTarget(returnFocus, RETURN_PROFILE),
                                    icon = {
                                        ProfileAvatar(
                                            name = profile.name,
                                            colorIndex = profile.colorIndex,
                                            size = TvDimensions.iconMedium,
                                            fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                        )
                                    },
                                )
                            }
                            CinemaIconButton(
                                onClick = { leaveTo(RETURN_SETTINGS, onSettings) },
                                modifier = Modifier.navReturnFocusTarget(returnFocus, RETURN_SETTINGS),
                                icon = {
                                    Icon(
                                        imageVector = CinemaIcons.Settings,
                                        contentDescription = stringResource(R.string.settings_title),
                                        tint = CinemaTextPrimary,
                                    )
                                },
                            )
                        }
                    }

                    if (needsSignIn) {
                        JellyfinSignInPanel(
                            providerName = providerName,
                            onSignIn = { leaveTo(RETURN_SIGN_IN) { onSignInRequired(activeProviderId) } },
                            scale = scale,
                            signInButtonFocusRequester = returnFocus.requesterFor(RETURN_SIGN_IN),
                        )
                    } else {
                        // Content type hero cards. Scrollable, not just fillMaxSize: the hero row plus
                        // the "Jump Back In" shelf below it can exceed a lower-density TV's viewport
                        // height, and an unscrollable Center-arranged Column clips whatever doesn't fit
                        // off both edges instead of making it reachable. Arrangement.Center still centers
                        // this content when it's shorter than the viewport, same as before — verticalScroll
                        // only takes over once content is taller than the space it's given.
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xl.scaled(scale)),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val isDevMode = appSettings.isDevMode
                                var cardIndex = 1
                                val heroCardModifier: (String) -> Modifier = { key ->
                                    Modifier
                                        .focusRequester(heroCardFocus.getValue(key))
                                        .onFocusChanged { if (it.hasFocus) lastHeroCard = key }
                                }
                                if (ContentType.LIVE_TV in supportedContentTypes) {
                                    ContentTypeHeroCard(
                                        title = stringResource(R.string.provider_live_tv_label),
                                        subtitle = stringResource(R.string.content_type_live_tv_subtitle_short),
                                        icon = CinemaIcons.LiveTv,
                                        categoryCounts = liveTvCounts,
                                        showTotal = isDevMode,
                                        showLivePulse = !liveTvEmpty,
                                        emptyLabel = if (liveTvEmpty) stringResource(R.string.content_type_live_tv_no_channels) else null,
                                        gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                                        onClick = { leaveTo(RETURN_LIVE_TV) { onContentTypeSelected(NavContentType.LIVE_TV) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_LIVE_TV))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_LIVE_TV),
                                    )
                                }

                                if (ContentType.MOVIES in supportedContentTypes) {
                                    ContentTypeHeroCard(
                                        title = stringResource(R.string.provider_movies_label),
                                        subtitle = stringResource(R.string.content_type_movies_subtitle_short),
                                        icon = CinemaIcons.Movie,
                                        categoryCounts = moviesCounts,
                                        showTotal = isDevMode,
                                        gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                                        onClick = { leaveTo(RETURN_MOVIES) { onContentTypeSelected(NavContentType.MOVIES) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_MOVIES))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_MOVIES),
                                    )
                                }

                                if (ContentType.TV_SHOWS in supportedContentTypes) {
                                    ContentTypeHeroCard(
                                        title = stringResource(R.string.provider_tv_shows_label),
                                        subtitle = stringResource(R.string.content_type_tv_shows_subtitle_short),
                                        icon = CinemaIcons.Tv,
                                        categoryCounts = tvShowsCounts,
                                        showTotal = isDevMode,
                                        gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                                        onClick = { leaveTo(RETURN_TV_SHOWS) { onContentTypeSelected(NavContentType.TV_SHOWS) } },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .then(heroCardModifier(RETURN_TV_SHOWS))
                                                .staggeredEntrance(cardIndex++)
                                                .navReturnFocusTarget(returnFocus, RETURN_TV_SHOWS),
                                    )
                                }
                            }

                            if (continueWatchingItems.isNotEmpty()) {
                                TvContinueWatchingShelf(
                                    items = continueWatchingItems,
                                    onItemSelected = { item ->
                                        leaveTo(RETURN_CONTINUE_WATCHING_PREFIX + item.id) { onContinueWatchingSelected(item) }
                                    },
                                    listState = shelfListState,
                                    itemModifier = { item ->
                                        Modifier.navReturnFocusTarget(returnFocus, RETURN_CONTINUE_WATCHING_PREFIX + item.id)
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = Spacing.xl.scaled(scale)),
                                )
                            }
                        }
                    }
                }

                if (showProviderPicker && allProviders.size > 1) {
                    // A new dialog window starts with focus on the Close button *below* the list, so
                    // D-pad Down had nowhere to go and Center just closed the dialog. Land on the
                    // current provider's row instead (F-38).
                    val pickerInitialFocus = remember { FocusRequester() }
                    val pickerFocusId = (allProviders.firstOrNull { it.id == activeProviderId } ?: allProviders.first()).id
                    CinemaAlertDialog(
                        initialFocus = pickerInitialFocus,
                        onDismissRequest = { showProviderPicker = false },
                        containerColor = CinemaSurface,
                        titleContentColor = CinemaTextPrimary,
                        textContentColor = CinemaTextSecondary,
                        title = { androidx.compose.material3.Text(stringResource(R.string.content_switch_provider_title)) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                // The pill only says "Update failed"; the reason is here. It already
                                // carries the raw detail in developer mode (ProviderSyncRunner).
                                lastSyncError?.takeIf { !syncing }?.let { error ->
                                    androidx.compose.material3.Text(
                                        text = stringResource(R.string.home_source_update_failed_detail, error),
                                        color = CinemaError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(bottom = Spacing.sm),
                                    )
                                }
                                allProviders.forEach { provider ->
                                    val isActive = provider.id == activeProviderId
                                    val label =
                                        if (appSettings.isDevMode) {
                                            "${provider.name} (${provider.type})"
                                        } else {
                                            provider.name
                                        }
                                    TvOptionRow(
                                        title = label,
                                        selected = isActive,
                                        activeLabel = stringResource(R.string.provider_active_label),
                                        onClick = {
                                            if (!isActive) {
                                                coroutineScope.launch {
                                                    val providerRepo = ProviderRepository(context.applicationContext)
                                                    providerRepo.pickProvider(provider.id)
                                                    showProviderPicker = false
                                                    refreshTrigger++
                                                    onProviderChanged()
                                                }
                                            } else {
                                                showProviderPicker = false
                                            }
                                        },
                                        modifier =
                                            if (provider.id ==
                                                pickerFocusId
                                            ) {
                                                Modifier.focusRequester(pickerInitialFocus)
                                            } else {
                                                Modifier
                                            },
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            CinemaDialogTextButton(onClick = { showProviderPicker = false }) {
                                androidx.compose.material3.Text(stringResource(R.string.common_close), color = CinemaAccent)
                            }
                        },
                    )
                }
            }
        }
    }
}

// Keys for the controls that navigate away from Home — see rememberNavReturnFocus.
private const val RETURN_LIVE_TV = "liveTv"
private const val RETURN_MOVIES = "movies"
private const val RETURN_TV_SHOWS = "tvShows"
private const val RETURN_EPG_BROWSER = "epgBrowser"
private const val RETURN_SEARCH = "search"
private const val RETURN_PROFILE = "profile"
private const val RETURN_SETTINGS = "settings"
private const val RETURN_SIGN_IN = "signIn"
private const val RETURN_CONTINUE_WATCHING_PREFIX = "cw:"

/** How long Back waits for the Continue Watching shelf to reload before giving up on its card. */
private const val RETURN_SHELF_WAIT_MS = 2_000L

/** How long a fresh open waits for the Live TV count before focusing the first card anyway. */
private const val ENTRY_FOCUS_WAIT_MS = 2_000L

/** The hero cards that can take focus, left to right: Live TV only when it has channels (or is still loading). */
private fun focusableHeroCards(
    supportedContentTypes: Set<String>,
    liveTvCounts: Pair<Int, Int>?,
): List<String> =
    buildList {
        if (ContentType.LIVE_TV in supportedContentTypes && liveTvCounts?.first != 0) add(RETURN_LIVE_TV)
        if (ContentType.MOVIES in supportedContentTypes) add(RETURN_MOVIES)
        if (ContentType.TV_SHOWS in supportedContentTypes) add(RETURN_TV_SHOWS)
    }

@Composable
private fun ContentTypeHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    categoryCounts: Pair<Int, Int>?,
    showTotal: Boolean = false,
    showLivePulse: Boolean = false,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Non-null: nothing to open (Live TV with no channels). The card is dimmed, shows this in
    // place of the count and cannot take focus.
    emptyLabel: String? = null,
) {
    val scale = LocalUiScale.current
    Card(
        onClick = onClick,
        modifier =
            modifier
                .then(if (emptyLabel != null) Modifier.focusProperties { canFocus = false }.alpha(CinemaAlpha.textFaint) else Modifier)
                .height(TvDimensions.contentTypeCardHeight.scaled(scale)),
        colors =
            CardDefaults.colors(
                containerColor = CinemaSurface,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = CinemaSurface,
                focusedContentColor = CinemaTextPrimary,
            ),
        scale =
            CardDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.xLarge)),
        border =
            CardDefaults.border(
                border =
                    Border(
                        border = BorderStroke(TvFocusTokens.borderThin, CinemaGlassBorder),
                        shape = RoundedCornerShape(CinemaCornerRadius.xLarge),
                    ),
                focusedBorder =
                    Border(
                        border =
                            BorderStroke(
                                TvFocusTokens.focusBorderWidth,
                                CinemaTextPrimary,
                            ),
                        shape = RoundedCornerShape(CinemaCornerRadius.xLarge),
                    ),
            ),
        glow = CardDefaults.glow(glow = TvFocusTokens.restingGlow, focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        val brush = remember(gradientColors) { Brush.verticalGradient(colors = gradientColors) }
        // Diagonal gloss over the flat gradient fill — same "raised glass" cue as GlassPanel, so
        // the card reads as a lit surface rather than a solid block of color.
        val sheenBrush =
            remember {
                Brush.linearGradient(
                    colors = listOf(CinemaTextPrimary.copy(alpha = CinemaAlpha.heroSheen), Color.Transparent),
                )
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush = brush,
                        shape = RoundedCornerShape(CinemaCornerRadius.xLarge),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(sheenBrush, shape = RoundedCornerShape(CinemaCornerRadius.xLarge)),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Spacing.md),
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier =
                            Modifier
                                .size(TvDimensions.contentTypeIconSize.scaled(scale) + Spacing.sm.scaled(scale))
                                .background(
                                    CinemaTextPrimary.copy(alpha = CinemaAlpha.heroChipBackground),
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = CinemaTextPrimary,
                            modifier = Modifier.size(TvDimensions.contentTypeIconSize.scaled(scale)),
                        )
                    }
                    if (showLivePulse) {
                        val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
                        val pulseAlpha by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.3f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(CinemaAnimation.shimmerDurationMs),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "live_pulse_alpha",
                        )
                        // pulseAlpha is read inside graphicsLayer's lambda, never in the composable
                        // body. Read from the body (as `color.copy(alpha = pulseAlpha)` did) it
                        // invalidates *composition* every animation frame — and since this runs
                        // forever, Home recomposed at 60fps with nothing on screen changing:
                        // 242 recompositions per 4 idle seconds, measured on a Shield, versus 0 on
                        // every other screen. Every navigation out of Home therefore started with
                        // the main thread already saturated. In the lambda the read is deferred to
                        // draw, so the animation costs a layer redraw and no recomposition, and the
                        // colors stay constant instead of allocating two Color objects per frame.
                        Box(
                            modifier =
                                Modifier
                                    .size(TvDimensions.liveDotSize.scaled(scale))
                                    .graphicsLayer { alpha = pulseAlpha }
                                    .border(TvDimensions.borderThin, CinemaTextPrimary, CircleShape)
                                    .background(CinemaLive, shape = CircleShape),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontSize =
                                MaterialTheme.typography.headlineMedium.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = subtitle,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize =
                                MaterialTheme.typography.bodyLarge.fontSize
                                    .scaled(scale),
                        ),
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.xxs.scaled(scale)),
                )
                if (categoryCounts == null) {
                    ShimmerPlaceholder(
                        modifier =
                            Modifier
                                .padding(top = Spacing.xs)
                                .size(width = TvDimensions.contentTypeIconSize.scaled(scale), height = Spacing.md)
                                .clip(RoundedCornerShape(CinemaCornerRadius.small)),
                    )
                } else {
                    val (filtered, total) = categoryCounts
                    val countText =
                        emptyLabel ?: if (showTotal && filtered < total) {
                            stringResource(R.string.category_filtered_of_total_format, filtered, total)
                        } else {
                            stringResource(R.string.category_count_format, filtered)
                        }
                    Box(
                        modifier =
                            Modifier
                                .padding(top = Spacing.xs)
                                .background(
                                    CinemaTextPrimary.copy(alpha = CinemaAlpha.heroChipBackground),
                                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                                ).padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelLarge,
                            color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textLow),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** In place of the library while this profile has no login for the active Jellyfin server. */
@Composable
private fun JellyfinSignInPanel(
    providerName: String,
    onSignIn: () -> Unit,
    scale: Float,
    signInButtonFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_jellyfin_sign_in_prompt, providerName),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize =
                        MaterialTheme.typography.headlineSmall.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.lg.scaled(scale)))
        CinemaPrimaryButton(
            onClick = onSignIn,
            text = stringResource(R.string.profile_jellyfin_sign_in_button),
            modifier = signInButtonFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        )
    }
}
