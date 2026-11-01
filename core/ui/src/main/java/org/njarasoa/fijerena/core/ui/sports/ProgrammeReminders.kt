package org.njarasoa.fijerena.core.ui.sports

import org.njarasoa.fijerena.core.network.sports.ProgrammeReminder
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.xmltv.EpgBrowserAiring

/**
 * The reminder of a Search the Guide airing, for its Remind Me: the programme [title] on the airing's
 * matched channel. Null for an airing with no channel of the profile, or one that has started at
 * [nowSec].
 */
fun programmeReminderOf(
    title: String,
    airing: EpgBrowserAiring,
    nowSec: Long,
): ProgrammeReminder? {
    val stream = airing.matchedStream ?: return null
    if (airing.startEpoch <= nowSec) return null
    val providerId = stream.providerId.takeIf { it != 0L } ?: airing.providerId
    return ProgrammeReminder(
        title = title,
        startEpochSec = airing.startEpoch,
        channel = ReminderChannel(providerId, stream.streamId, stream.categoryId, shownChannelName(stream.streamName)),
    )
}
