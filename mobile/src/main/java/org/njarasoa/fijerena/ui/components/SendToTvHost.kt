package org.njarasoa.fijerena.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.player.domain.BrowseTarget
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.browseTarget
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.MenuAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.sync.SendToTvAppGateway
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.SendItem
import org.njarasoa.fijerena.core.ui.viewmodels.SendToTvSheet
import org.njarasoa.fijerena.core.ui.viewmodels.SendToTvViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.TvRow
import org.njarasoa.fijerena.core.ui.viewmodels.TvStatus

/** Send to TV for the phone's screens, from [rememberSendToTv]; null without the nav host. */
@Stable
class SendToTvController internal constructor(
    private val viewModel: SendToTvViewModel,
    private val availableState: State<Boolean>,
) {
    /** Linked to a sync group with a TV that has the feature: the action shows only then. */
    val available: Boolean get() = availableState.value

    /** Opens the picker or sends at once; see [SendToTvViewModel.send]. */
    fun send(
        item: SendItem,
        beforeSend: (suspend () -> Long?)? = null,
    ) = viewModel.send(item, beforeSend)

    internal val sheet get() = viewModel.sheet

    internal fun pick(row: TvRow) = viewModel.pick(row)

    internal fun closeSheet() = viewModel.closeSheet()
}

val LocalSendToTv = staticCompositionLocalOf<SendToTvController?> { null }

/** The Send to TV controller of the phone's nav host (the send to TV plan, ST-D11, ST-D12). */
@Composable
fun rememberSendToTv(): SendToTvController {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: SendToTvViewModel =
        viewModel(
            factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = SendToTvViewModel(SendToTvAppGateway(application)) as T
                },
        )
    val available = viewModel.available.collectAsStateWithLifecycle()
    return remember(viewModel, available) { SendToTvController(viewModel, available) }
}

/** The Send to TV menu action for [item]; null when the phone cannot send or [item] is not one film, episode or channel. */
@Composable
fun sendToTvMenuAction(item: SendItem?): MenuAction? {
    val controller = LocalSendToTv.current
    return if (controller?.available == true && item != null) {
        MenuAction(CinemaIcons.Tv, stringResource(R.string.send_to_tv_action), onClick = { controller.send(item) })
    } else {
        null
    }
}

/** A row as a [SendItem] when [target] is one film, episode or channel; null for a whole show or a category ref. */
fun sendItemOf(
    target: BrowseTarget,
    contentType: String,
    id: String,
    name: String,
    categoryId: String,
    providerId: Long,
    isCategoryRef: Boolean = false,
): SendItem? =
    when {
        isCategoryRef -> {
            null
        }

        target is BrowseTarget.Series -> {
            null
        }

        target is BrowseTarget.Episode -> {
            SendItem(
                providerId = providerId,
                streamId = target.episodeId.raw,
                streamName = name,
                categoryId = categoryId,
                contentType = ContentType.TV_SHOWS,
                episodeId = target.episodeId.raw,
                episodeExtension = target.extension,
                seriesId = target.seriesId?.raw,
                seriesName = target.seriesName,
                title = target.seriesName?.takeIf { it.isNotBlank() } ?: name,
                subtitle = name.takeIf { target.seriesName != null },
            )
        }

        else -> {
            SendItem(providerId = providerId, streamId = id, streamName = name, categoryId = categoryId, contentType = contentType)
        }
    }

/** [sendItemOf] for a list's row; [fallbackProviderId] is the list's source when the row has none. */
fun MediaItem.sendItem(
    contentType: String,
    fallbackProviderId: Long,
): SendItem? {
    val source = providerId.takeIf { it != 0L } ?: fallbackProviderId
    // A row on no source (a chart's TMDB-only card) cannot be played anywhere.
    val isRef = providerData["isCategoryRef"] == "true" || source == 0L
    return sendItemOf(browseTarget(contentType), contentType, id, name, categoryId, source, isCategoryRef = isRef)
}

/** The Send to TV sheet at the root of the phone's screens (the send to TV plan, ST-D12). */
@Composable
fun SendToTvSheetHost(controller: SendToTvController) {
    val sheet by controller.sheet.collectAsStateWithLifecycle()
    sheet?.let { SendToTvPicker(it, onPick = controller::pick, onDismiss = controller::closeSheet) }
}

/** The Send to TV sheet: one row per TV, with its status and what it plays when it shares that. */
@Composable
internal fun SendToTvPicker(
    sheet: SendToTvSheet,
    onPick: (TvRow) -> Unit,
    onDismiss: () -> Unit,
) {
    CinemaSheet(onDismissRequest = onDismiss, rows = true) {
        CinemaSheetHeader(title = stringResource(R.string.send_to_tv_action))
        Spacer(Modifier.height(CinemaSpacing.sm))
        if (sheet.rows.isEmpty()) {
            CinemaText(
                text = stringResource(R.string.send_to_tv_no_tvs),
                tone = TextTone.Secondary,
                modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.md),
            )
        }
        sheet.rows.forEach { row -> SendToTvPickerRow(row, onPick) }
    }
}

@Composable
private fun SendToTvPickerRow(
    row: TvRow,
    onPick: (TvRow) -> Unit,
) {
    val canPick = row.status == TvStatus.OPEN
    CinemaRow(onClick = { onPick(row) }, enabled = canPick) {
        CinemaIcon(CinemaIcons.Tv, null, tone = if (canPick) TextTone.Primary else TextTone.Secondary)
        Column(modifier = Modifier.weight(1f)) {
            CinemaText(
                text = row.name,
                role = TextRole.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                tone = if (canPick) TextTone.Primary else TextTone.Secondary,
            )
            CinemaText(
                text = statusLine(row),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusLine(row: TvRow): String {
    val status =
        when (row.status) {
            TvStatus.OPEN -> {
                stringResource(R.string.send_to_tv_open)
            }

            TvStatus.NOT_OPEN -> {
                listOf(
                    stringResource(R.string.send_to_tv_not_open),
                    stringResource(R.string.send_to_tv_open_the_app),
                ).joinToString(" · ")
            }

            TvStatus.NEEDS_UPDATE -> {
                stringResource(R.string.send_to_tv_needs_update)
            }
        }
    return listOfNotNull(status, row.playing).joinToString(" · ")
}
