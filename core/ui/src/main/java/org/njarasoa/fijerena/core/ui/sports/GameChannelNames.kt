package org.njarasoa.fijerena.core.ui.sports

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.ui.components.CinemaBadge

// A game's channels as the Live TV lists show them: the name without its tags, the tags as badges.

/**
 * A channel's name without the tags its badges show: "US| ESPN DEPORTES ᴴᴰ" is "ESPN DEPORTES". A
 * separator left at an end goes too (":Paramount+ 19" is "Paramount+ 19").
 */
fun shownChannelName(name: String): String = parseDisplayTitle(name).title.trim(*NAME_EDGE).ifBlank { name }

private val NAME_EDGE = charArrayOf(' ', ':', '|', '-')

/** The letter in place of a channel's missing logo: the first of its shown name ("E", not the "U" of "US| ESPN"). */
fun channelLetter(name: String): Char? = shownChannelName(name).firstOrNull(Char::isLetterOrDigit)

/** "+n" after a game's best channel: the number of its other channels. Nothing for one channel. */
@Composable
fun MoreChannelsBadge(
    channels: List<GameChannel>?,
    modifier: Modifier = Modifier,
) {
    val more = (channels?.size ?: 0) - 1
    if (more > 0) CinemaBadge("+$more", modifier, maxLines = 1)
}
