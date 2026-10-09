@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.rail

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.lerp
import androidx.core.content.edit
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaAnimation
import org.njarasoa.fijerena.core.ui.theme.CinemaBackground
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.components.cards.TvListRowDefaults
import org.njarasoa.fijerena.ui.components.input.FOCUS_RETRY_MAX_FRAMES
import org.njarasoa.fijerena.ui.components.input.currentIndicator
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * The TV navigation rail (docs/plans/archive/20261008_tv-nav-rail-plan.md).
 *
 * - **At rest:** a column of faint [TvNavRailDefaults.itemSize] items inside the screen's empty
 *   left margin ([TvNavRailDefaults.restWidth]), [TvNavRailDefaults.edgeInset] from the edge, on a
 *   dark left-edge gradient so the icons read over a full-bleed hero picture. Items sit from the
 *   top safe margin down, so the avatar lines up with the screens' headers; Settings sits at the
 *   bottom. [TvNavRailState.current] is white with the accent bar (`Modifier.currentIndicator`).
 * - **Focused** (focus anywhere in it sets [TvNavRailState.expanded]): slides out over the content
 *   to [TvNavRailDefaults.expandedWidth], icons and labels on a near-opaque surface, and a scrim
 *   dims the rest of the screen. Focus enters at [TvNavRailState.entry], on the current item (the
 *   first when there is none); a geometric Left into the rail is sent there too, and Up / Down from
 *   the content never enter it. Up / Down stop at
 *   the ends, Left does nothing, **Right** returns to [TvNavRailState.returnTo] (or moves Right when
 *   it can't take focus), **Back** goes Home ([onSelect] with [RailItem.HOME]) or, on Home, back to
 *   the content. **OK** calls [onSelect]; the new screen takes focus itself, and if focus is still
 *   in the rail about half a second later the rail moves it Right.
 * - **First launch:** once per device, the first time it shows, it slides out on its own without
 *   taking focus, with "Press Left for sections" beside it, for [TvNavRailDefaults.hintDurationMs].
 * - Draws nothing while [TvNavRailState.visible] is false.
 *
 * It fills the screen (the scrim does) but is not focusable outside its items: put it after the
 * NavHost in the same full-screen `Box`, so a screen's first focusable still comes first.
 *
 * @param items what the rail offers, top to bottom, sections already filtered to the source's.
 * @param profileInitial the avatar's letter for [RailItem.PROFILE].
 * @param onSelect called with the item picked; the nav host navigates.
 */
@Composable
fun TvNavRail(
    state: TvNavRailState,
    items: List<RailItem>,
    profileInitial: String,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.visible) {
        RailOverlay(state, items, profileInitial, onSelect, modifier)
    }
}

/** Where focus enters the rail: [current] when the rail offers it, else the first item. */
internal fun railEntryItem(
    current: RailItem?,
    items: List<RailItem>,
): RailItem? = current?.takeIf { it in items } ?: items.firstOrNull()

private const val HINT_PREFS = "tv_nav_rail"
private const val KEY_HINT_SHOWN = "hint_shown"

/** How long the rail stays on screen before the first-launch hint shows (the screen has settled). */
private const val HINT_SETTLE_MS = 1_500L

@Composable
private fun RailOverlay(
    state: TvNavRailState,
    items: List<RailItem>,
    profileInitial: String,
    onSelect: (RailItem) -> Unit,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    val hintShowing = rememberFirstLaunchHint()
    val motion = tween<Float>(TvNavRailDefaults.animationMs, easing = CinemaAnimation.StandardEasing)
    // Read in layout and draw only, so sliding never recomposes the items.
    val openProgress = animateFloatAsState(if (state.expanded || hintShowing) 1f else 0f, motion, label = "railOpen")
    val scrimProgress = animateFloatAsState(if (state.expanded) 1f else 0f, motion, label = "railScrim")

    // The rail leaves the tree with focus in it when a screen hides it: nothing reports that.
    DisposableEffect(state) { onDispose { state.expanded = false } }

    val returnToContent = {
        val target = state.returnTo
        if (target == null || !target.requestFocus()) focusManager.moveFocus(FocusDirection.Right)
    }

    // After a pick, the new screen lands focus itself; if it hasn't once the screen transition is
    // over (growing gaps, as requestFocusWithRetry), focus is moved off the rail to the content.
    var leaveRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(leaveRequests) {
        if (leaveRequests > 0) {
            var frames = 0
            var gap = 1
            while (state.expanded && frames < FOCUS_RETRY_MAX_FRAMES) {
                repeat(gap) { withFrameNanos { } }
                frames += gap
                gap *= 2
            }
            if (state.expanded) focusManager.moveFocus(FocusDirection.Right)
        }
    }
    val select = { item: RailItem ->
        onSelect(item)
        leaveRequests++
    }
    val back = { if (state.current == RailItem.HOME) returnToContent() else select(RailItem.HOME) }
    // Fallback only: a focused Surface can swallow the first Back before BackHandler sees it, so
    // the rail's onPreviewKeyEvent below takes Back (NAVIGATION_GUIDE → "TV Back on Detail Screens").
    BackHandler(enabled = state.expanded) { back() }

    val scrim = CinemaBackground.copy(alpha = CinemaAlpha.scrim)
    val edgeShade = Brush.horizontalGradient(listOf(CinemaBackground.copy(alpha = CinemaAlpha.imageOverlay), Color.Transparent))
    val panel = CinemaSurface.copy(alpha = CinemaAlpha.overlayHeavy)
    val entryItem = railEntryItem(state.current, items)

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(scrim, alpha = scrimProgress.value) })
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val width = lerp(TvNavRailDefaults.restWidth, TvNavRailDefaults.expandedWidth, openProgress.value).roundToPx()
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    }.drawBehind {
                        val open = openProgress.value
                        drawRect(edgeShade, alpha = 1f - open)
                        drawRect(panel, alpha = open)
                    }.onPreviewKeyEvent { event ->
                        when {
                            event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown -> {
                                returnToContent()
                                true
                            }

                            event.key == Key.Back && event.type == KeyEventType.KeyUp -> {
                                back()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }.onFocusChanged { state.expanded = it.hasFocus }
                    .focusProperties {
                        // Only Left (or a request) enters: a geometric Left from the content may reach
                        // any item, so it lands on the current one; Up / Down off the end of a short
                        // list would otherwise fall into the rail beside it.
                        onEnter = {
                            when (requestedFocusDirection) {
                                FocusDirection.Enter -> Unit
                                FocusDirection.Left -> state.entry.requestFocus()
                                else -> cancelFocusChange()
                            }
                        }
                        // Up / Down stop at the ends, Left goes nowhere; Right is the key handler's.
                        onExit = {
                            val direction = requestedFocusDirection
                            if (direction == FocusDirection.Up || direction == FocusDirection.Down || direction == FocusDirection.Left) {
                                cancelFocusChange()
                            }
                        }
                    }.focusGroup()
                    .padding(horizontal = TvNavRailDefaults.edgeInset, vertical = Spacing.tvSafeMarginVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items.forEach { item ->
                key(item) {
                    if (item == RailItem.SETTINGS) Spacer(modifier = Modifier.weight(1f))
                    RailEntry(
                        item = item,
                        state = state,
                        profileInitial = profileInitial,
                        openProgress = openProgress,
                        onClick = { select(item) },
                        modifier = if (item == entryItem) Modifier.focusRequester(state.entry) else Modifier,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = hintShowing,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = TvNavRailDefaults.expandedWidth + Spacing.md),
            enter = fadeIn(tween(TvNavRailDefaults.animationMs)),
            exit = fadeOut(tween(TvNavRailDefaults.animationMs)),
        ) {
            Text(
                text = stringResource(R.string.tv_nav_rail_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = CinemaTextPrimary,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(panel.copy(alpha = 1f), RoundedCornerShape(CornerRadius.large))
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

/** One item: an icon (the avatar for [RailItem.PROFILE]) and its label, revealed as the rail slides out. */
@Composable
private fun RailEntry(
    item: RailItem,
    state: TvNavRailState,
    profileInitial: String,
    openProgress: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val isCurrent = item == state.current
    val profileName = state.profileName ?: profileInitial
    val label =
        when (item) {
            RailItem.PROFILE -> profileName
            RailItem.HOME -> stringResource(R.string.tv_nav_rail_home)
            RailItem.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
            RailItem.MOVIES -> stringResource(R.string.provider_movies_label)
            RailItem.TV_SHOWS -> stringResource(R.string.provider_tv_shows_label)
            RailItem.SEARCH -> stringResource(R.string.common_search)
            RailItem.SETTINGS -> stringResource(R.string.settings_title)
        }
    val description = if (item == RailItem.PROFILE) stringResource(R.string.profile_switch_description, profileName) else label
    val shape = RoundedCornerShape(CornerRadius.small)
    val lit = isCurrent || focused
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TvNavRailDefaults.itemSize)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = description },
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = CinemaTextPrimary,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                focusedContentColor = CinemaTextPrimary,
                pressedContainerColor = TvFocusTokens.focusedContainer,
                pressedContentColor = CinemaTextPrimary,
            ),
        scale =
            ClickableSurfaceDefaults.scale(
                scale = TvFocusTokens.defaultScale,
                focusedScale = TvFocusTokens.focusedScaleSubtle,
                pressedScale = TvFocusTokens.pressedScaleSubtle,
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline), shape = shape),
            ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = TvFocusTokens.focusedGlow),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().currentIndicator(isCurrent, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(TvNavRailDefaults.itemSize)
                        .graphicsLayer {
                            alpha =
                                if (lit) {
                                    1f
                                } else {
                                    val open = openProgress.value
                                    TvNavRailDefaults.restAlpha + (TvNavRailDefaults.expandedAlpha - TvNavRailDefaults.restAlpha) * open
                                }
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (item == RailItem.PROFILE) {
                    ProfileAvatar(
                        name = profileInitial,
                        colorIndex = state.profileColorIndex,
                        size = TvDimensions.iconMedium,
                        fontSize = MaterialTheme.typography.titleSmall.fontSize,
                    )
                } else {
                    Icon(
                        imageVector = item.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(TvDimensions.iconSmall),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = TvListRowDefaults.titleColor(isCurrent, focused),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = Spacing.xs)
                        .graphicsLayer { alpha = openProgress.value * if (lit) 1f else TvNavRailDefaults.expandedAlpha },
            )
        }
    }
}

/** The item's icon; [RailItem.PROFILE] draws the avatar instead. */
@Composable
private fun RailItem.icon(): ImageVector =
    when (this) {
        RailItem.PROFILE, RailItem.HOME -> CinemaIcons.Home
        RailItem.LIVE_TV -> CinemaIcons.LiveTv
        RailItem.MOVIES -> CinemaIcons.Movie
        RailItem.TV_SHOWS -> CinemaIcons.Tv
        RailItem.SEARCH -> CinemaIcons.Search
        RailItem.SETTINGS -> CinemaIcons.Settings
    }

/**
 * Whether the first-launch hint is up: true for [TvNavRailDefaults.hintDurationMs] the first time
 * the rail has stayed on screen for [HINT_SETTLE_MS] on this device, then never again. The rail is
 * composed only while visible, so a screen that hides it right away (the profile picker at launch
 * hides it one frame after it first shows) cancels this before the hint is spent; it is recorded
 * only once it has been shown in full.
 */
@Composable
private fun rememberFirstLaunchHint(): Boolean {
    val context = LocalContext.current
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HINT_SHOWN, false)) {
            delay(HINT_SETTLE_MS)
            showing = true
            delay(TvNavRailDefaults.hintDurationMs)
            showing = false
            prefs.edit { putBoolean(KEY_HINT_SHOWN, true) }
        }
    }
    return showing
}
