package org.njarasoa.fijerena.core.ui.catchup

import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.CatchupAvailability
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.sourceOr
import org.njarasoa.fijerena.core.player.model.EpgProgram

/**
 * The player route that replays [program] from [channel]'s archive (catch-up), from the start of
 * its window ([CatchupAvailability.window]), on [providerId]'s source. See
 * docs/plans/archive/20261010_catchup-plan.md.
 */
fun catchupRoute(
    program: EpgProgram,
    channel: MediaItem,
    providerId: Long,
): Screen.Player {
    val window = CatchupAvailability.window(program)
    return Screen.Player(
        streamId = channel.id,
        streamName = channel.name,
        categoryId = channel.categoryId,
        contentType = ContentType.LIVE_TV,
        catchupStartSec = window.startEpochSec,
        catchupDurationSec = window.durationSec,
        catchupOffsetSec = window.programOffsetSec,
        programTitle = program.title,
        providerId = providerId,
    )
}

/**
 * The Live TV route a TV Guide row opens — its channel, in its category — on the row's own source
 * ([sourceOr]; [guideSource], the guide's, for a row that leaves it 0): a Recent or Favourites
 * guide lists every source's channels.
 */
fun guideChannelRoute(
    channel: MediaItem,
    guideSource: Long,
): Screen.CategoryList =
    Screen.CategoryList(
        contentType = ContentType.LIVE_TV,
        initialCategoryId = channel.categoryId,
        initialStreamId = channel.id,
        providerId = channel.sourceOr(guideSource),
    )
