package org.njarasoa.fijerena.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaDivider
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
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
            actionIconContentColor = c.textSecondary,
        )
    Column(modifier) {
        if (centred) {
            // look-ok: the component that wraps Material's top bar
            CenterAlignedTopAppBar(
                title = title,
                navigationIcon = navigation,
                actions = actions,
                expandedHeight = height,
                windowInsets = mobileClockTopBarInsets(),
                colors = colors,
            )
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

/** Back: a plain icon action on a page; over artwork, a filled button on a dark disc so it reads. */
@Composable
private fun HeaderBack(
    onBack: () -> Unit,
    ground: HeaderGround,
) {
    val label = stringResource(R.string.common_back)
    if (ground == HeaderGround.Page) {
        IconAction(onClick = onBack, icon = CinemaIcons.ArrowBack, label = label)
    } else {
        CinemaIconButton(
            modifier = Modifier.detailBackBacking(solid = ground == HeaderGround.Covered),
            onClick = onBack,
            icon = { CinemaIcon(CinemaIcons.ArrowBack, label, tone = TextTone.Primary) },
        )
    }
}
