package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sync.MoveBlock
import org.njarasoa.fijerena.core.network.sync.MoveState
import org.njarasoa.fijerena.core.network.sync.OldServerState
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.history.LocalNerd
import java.text.NumberFormat

/*
 * The text of the move rows on the Live Sync page, the same on both apps (the sync server move
 * plan). Each app puts its own buttons around it.
 */

/**
 * The lines under Move to Another Server: how far the move is, how it ended, or else why the button
 * is disabled. A Nerd also sees the raw error of a move that stopped.
 */
@Composable
fun LiveSyncMoveLines(
    move: MoveState,
    block: MoveBlock?,
    blockLoaded: Boolean,
    blockError: String?,
) {
    when (move) {
        is MoveState.Running -> {
            CinemaText(liveSyncMoveStageText(move), role = TextRole.Body, tone = TextTone.Primary)
            val fraction =
                move.total?.takeIf { move.stage == MoveState.Stage.COPYING && it > 0 }?.let { total ->
                    (move.copied.toFloat() / total).coerceIn(0f, 1f)
                }
            CinemaProgressBar(progress = fraction?.let { f -> { f } }, modifier = Modifier.fillMaxWidth())
        }

        is MoveState.Failed -> {
            CinemaText(stringResource(R.string.live_sync_move_failed), role = TextRole.Body, tone = TextTone.Danger)
            NerdRawLine(move.message)
        }

        MoveState.Done -> {
            CinemaText(stringResource(R.string.live_sync_move_done), role = TextRole.Body, tone = TextTone.Success)
        }

        MoveState.Idle -> {
            MoveBlockLines(block, blockLoaded, blockError)
        }
    }
}

@Composable
private fun MoveBlockLines(
    block: MoveBlock?,
    blockLoaded: Boolean,
    blockError: String?,
) {
    when {
        blockError != null -> {
            CinemaText(blockError, role = TextRole.BodySmall, tone = TextTone.Danger)
        }

        !blockLoaded -> {
            CinemaText(stringResource(R.string.live_sync_move_checking_devices), role = TextRole.BodySmall, tone = TextTone.Secondary)
        }

        block is MoveBlock.UpdateFirst -> {
            CinemaText(
                stringResource(R.string.live_sync_move_update_first, block.deviceNames.joinToString(", ")),
                role = TextRole.BodySmall,
                tone = TextTone.Warning,
            )
            CinemaText(stringResource(R.string.live_sync_move_update_first_hint), role = TextRole.BodySmall, tone = TextTone.Secondary)
        }

        block == MoveBlock.MoveInProgress -> {
            CinemaText(stringResource(R.string.live_sync_move_in_progress), role = TextRole.BodySmall, tone = TextTone.Warning)
        }

        block == MoveBlock.OldServerLeft -> {
            CinemaText(stringResource(R.string.live_sync_move_old_server_left), role = TextRole.BodySmall, tone = TextTone.Warning)
        }
    }
}

/** "Checking the new server…", "Copying 1,200 of 4,000", "Copying… 1,200 so far" (no total yet), … */
@Composable
fun liveSyncMoveStageText(move: MoveState.Running): String {
    val number = NumberFormat.getIntegerInstance()
    return when (move.stage) {
        MoveState.Stage.CHECKING -> {
            stringResource(R.string.live_sync_move_checking)
        }

        MoveState.Stage.CREATING -> {
            stringResource(R.string.live_sync_move_creating)
        }

        MoveState.Stage.COPYING -> {
            move.total?.let { stringResource(R.string.live_sync_move_copying, number.format(move.copied), number.format(it)) }
                ?: stringResource(R.string.live_sync_move_copying_count, number.format(move.copied))
        }

        MoveState.Stage.SWITCHING -> {
            stringResource(R.string.live_sync_move_switching)
        }
    }
}

/**
 * The Old Server row's text: its address, then what the old server answered ([state] null while it
 * loads), then the error of the last Purge or Forget. A Nerd also sees the raw error of an old
 * server that did not answer.
 */
@Composable
fun LiveSyncOldServerLines(
    address: String,
    state: OldServerState?,
    error: String?,
) {
    CinemaText(stringResource(R.string.live_sync_old_server_title), role = TextRole.Heading, tone = TextTone.Primary)
    CinemaText(stringResource(R.string.live_sync_server_line, address), role = TextRole.BodySmall, tone = TextTone.Secondary)
    when (state) {
        null -> {
            CinemaText(stringResource(R.string.live_sync_old_server_checking), role = TextRole.Body, tone = TextTone.Secondary)
        }

        is OldServerState.NotMoved -> {
            CinemaText(
                stringResource(R.string.live_sync_old_server_not_moved, state.deviceNames.joinToString(", ")),
                role = TextRole.Body,
                tone = TextTone.Warning,
            )
            CinemaText(stringResource(R.string.live_sync_old_server_not_moved_hint), role = TextRole.BodySmall, tone = TextTone.Secondary)
        }

        OldServerState.Ready -> {
            CinemaText(stringResource(R.string.live_sync_old_server_ready), role = TextRole.Body, tone = TextTone.Primary)
        }

        OldServerState.NoPurge -> {
            CinemaText(stringResource(R.string.live_sync_old_server_no_purge), role = TextRole.Body, tone = TextTone.Secondary)
        }

        is OldServerState.Unreachable -> {
            CinemaText(stringResource(R.string.live_sync_old_server_unreachable), role = TextRole.Body, tone = TextTone.Danger)
            NerdRawLine(state.message)
        }
    }
    error?.let { CinemaText(it, role = TextRole.Body, tone = TextTone.Danger) }
}

/** The raw error under a friendly message, masked, for a Nerd only. */
@Composable
private fun NerdRawLine(message: String) {
    val raw = Redact.text(message)
    if (LocalNerd.current && raw.isNotBlank()) CinemaText("[dev] $raw", role = TextRole.BodySmall, tone = TextTone.Secondary)
}
