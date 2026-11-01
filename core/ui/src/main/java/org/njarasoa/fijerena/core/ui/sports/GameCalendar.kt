package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import org.njarasoa.fijerena.core.network.sports.GameChannel
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.ui.utils.openAddToCalendarEvent

/** A game without a guide programme lasts this long in the calendar. */
private const val GAME_CALENDAR_SEC = 3 * 3600L

/**
 * Hands [game] on [channel] to the calendar app as a new event, as the guide does for a programme:
 * the game's title, its competition, the channel's name as the place, and the channel's guide
 * programme's end (else 3 hours after the start).
 */
fun addGameToCalendar(
    context: Context,
    game: SportsGame,
    channel: GameChannel,
) {
    val end = channel.programme?.endSec?.takeIf { it > game.startEpochSec } ?: (game.startEpochSec + GAME_CALENDAR_SEC)
    openAddToCalendarEvent(
        context = context,
        title = gameTitle(context.resources, game),
        description = game.competition.name,
        location = shownChannelName(channel.stream.name),
        startEpochSeconds = game.startEpochSec,
        endEpochSeconds = end,
    )
}
