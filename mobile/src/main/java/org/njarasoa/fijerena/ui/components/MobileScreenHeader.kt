package org.njarasoa.fijerena.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/** What is under a [MobileScreenHeader]. */
enum class HeaderGround {
    /** The page starts under the bar: the Look and Feel decides the bar's fill. */
    Page,

    /** Artwork runs up under the bar and shows through it; the back button gets a dark disc. */
    Artwork,

    /** The page scrolls up under the bar: the bar is solid, so the title stays readable. */
    Covered,
}

/**
 * The top bar of a phone screen, the phone side of TV's `TvScreenHeader`: [title] (and a short
 * [subtitle] under it), Back when [onBack] is set, and icon [actions] at the end (`IconAction`).
 * The Look and Feel decides how it looks (`UiStyle.topBar`: height, title role, title in the middle
 * or at the start, fill, line); the clock keeps its corner. A blank [title] shows no text.
 *
 * [ground]: what is under the bar. A film or show page gives [HeaderGround.Artwork] while its
 * backdrop is under the bar and [HeaderGround.Covered] once the page has scrolled past it.
 */
@Composable
fun MobileScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    ground: HeaderGround = HeaderGround.Page,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = LocalUiStyle.current.topBar
    HeaderBar(
        modifier = modifier,
        centred = tokens.centredTitle,
        height = tokens.height,
        ground = ground,
        title = {
            if (title.isNotBlank()) {
                Column {
                    CinemaText(title, role = tokens.titleRole, tone = TextTone.Inherit, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        CinemaText(it, role = TextRole.BodySmall, tone = TextTone.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        navigation = {
            if (onBack != null) HeaderBack(onBack, ground)
        },
        actions = actions,
    )
}

/**
 * A [MobileScreenHeader] with its own [content] across the whole bar (a tab root: the profile
 * avatar, the title, its icons and the sync line). No Back; the content starts at the start of the
 * bar in every Look. [height]: the Look's height when not set.
 */
@Composable
fun MobileScreenHeader(
    modifier: Modifier = Modifier,
    height: Dp = LocalUiStyle.current.topBar.height,
    content: @Composable () -> Unit,
) {
    HeaderBar(
        modifier = modifier,
        centred = false,
        height = height,
        ground = HeaderGround.Page,
        title = content,
        navigation = {},
        actions = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeaderBar(
    modifier: Modifier,
    centred: Boolean,
    height: Dp,
    ground: HeaderGround,
    title: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalUiStyle.current.topBar
    val c = CinemaTheme.colors
    val fill =
        when (ground) {
            HeaderGround.Page -> if (tokens.filled) c.surface else c.clear
            HeaderGround.Artwork -> c.clear
            HeaderGround.Covered -> c.surface
        }
    val container by animateColorAsState(fill, label = "header_fill")
    val colors =
        // look-ok: the component that wraps Material's top bar
        TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            scrolledContainerColor = container,
            navigationIconContentColor = c.textPrimary,
            titleContentColor = c.textPrimary,
            // Action icons are in the Primary tone (Accent while on), as everywhere.
            actionIconContentColor = c.textPrimary,
        )
    Column(modifier) {
        if (centred) {
            CentredBar(container = container, height = height, title = title, navigation = navigation, actions = actions)
        } else {
            // look-ok: the component that wraps Material's top bar
            TopAppBar(
                title = title,
                navigationIcon = navigation,
                actions = actions,
                expandedHeight = height,
                windowInsets = mobileClockTopBarInsets(),
                colors = colors,
            )
        }
        // No line over artwork: it would cut across the picture.
        if (tokens.divider && ground != HeaderGround.Artwork) CinemaDivider()
    }
}

/**
 * A bar with its title in the middle of the screen, not in the middle of the room the clock leaves:
 * Material's centred bar centred the title in the bar less the clock's room, and moved a long one
 * to the start, so the titles of Licences or the Guide looked left-aligned. The title is in the
 * middle of the bar's full width while it fits between Back and the actions, else as near the
 * middle as it can be; it ends with "…" when the room between them is too small.
 */
@Composable
private fun CentredBar(
    container: Color,
    height: Dp,
    title: @Composable () -> Unit,
    navigation: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = CinemaTheme.colors
    val insets = mobileClockTopBarInsets()
    val edge = CinemaSpacing.xxs
    Layout(
        content = {
            Box(Modifier.layoutId(SLOT_NAVIGATION), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides c.textPrimary) { navigation() }
            }
            Box(Modifier.layoutId(SLOT_TITLE), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides c.textPrimary) { title() }
            }
            Row(Modifier.layoutId(SLOT_ACTIONS), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides c.textPrimary) { actions() }
            }
        },
        // look-ok: the bar component sets its own fill, as Material's bar did
        modifier = Modifier.fillMaxWidth().background(container),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val top = insets.getTop(this)
        val start = insets.getLeft(this, layoutDirection) + edge.roundToPx()
        val end = width - insets.getRight(this, layoutDirection) - edge.roundToPx()
        val barHeight = height.roundToPx()
        val loose = Constraints(maxWidth = (end - start).coerceAtLeast(0), maxHeight = barHeight)
        val nav = measurables.first { it.layoutId == SLOT_NAVIGATION }.measure(loose)
        val act =
            measurables.first { it.layoutId == SLOT_ACTIONS }.measure(
                loose.copy(maxWidth = (end - start - nav.width).coerceAtLeast(0)),
            )
        val titleStart = start + nav.width
        val titleEnd = end - act.width
        val titleRoom = (titleEnd - titleStart).coerceAtLeast(0)
        val titlePlaceable = measurables.first { it.layoutId == SLOT_TITLE }.measure(loose.copy(maxWidth = titleRoom))
        val titleX = ((width - titlePlaceable.width) / 2).coerceIn(titleStart, (titleEnd - titlePlaceable.width).coerceAtLeast(titleStart))
        layout(width, top + barHeight) {
            nav.place(start, top + (barHeight - nav.height) / 2)
            act.place(titleEnd, top + (barHeight - act.height) / 2)
            titlePlaceable.place(titleX, top + (barHeight - titlePlaceable.height) / 2)
        }
    }
}

private const val SLOT_NAVIGATION = "navigation"
private const val SLOT_TITLE = "title"
private const val SLOT_ACTIONS = "actions"

/** Back: a plain icon action on a solid bar; over artwork, a filled button on a dark disc so it reads. */
@Composable
private fun HeaderBack(
    onBack: () -> Unit,
    ground: HeaderGround,
) {
    val label = stringResource(R.string.common_back)
    if (ground != HeaderGround.Artwork) {
        IconAction(onClick = onBack, icon = CinemaIcons.ArrowBack, label = label)
    } else {
        CinemaIconButton(
            modifier = Modifier.detailBackBacking(),
            onClick = onBack,
            icon = { CinemaIcon(CinemaIcons.ArrowBack, label, tone = TextTone.Primary) },
        )
    }
}
