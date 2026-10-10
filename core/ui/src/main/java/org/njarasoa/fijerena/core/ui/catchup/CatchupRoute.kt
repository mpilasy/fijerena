package org.njarasoa.fijerena.core.ui.catchup

import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.player.domain.CatchupAvailability
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgProgram

/**
 * The player route that replays [program] from [channel]'s archive (catch-up), from the start of
 * its window ([CatchupAvailability.window]). See docs/plans/20261010_catchup-plan.md.
 */
fun catchupRoute(
    program: EpgProgram,
    channel: MediaItem,
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
    )
}
