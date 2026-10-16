package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.player.model.BufferingInfo
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.deviceinfo.currentConnectionLabel
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaWarning
import java.util.Locale

/**
 * Expert mode, under "Buffering…": how much video is held of what playback waits for, for how
 * long, the connection, the speed against what the stream needs and the server, and — only once
 * a load failed — the errors of this wait and what it received. Read from the playback service twice a second.
 */
@Composable
fun BufferingDetails(modifier: Modifier = Modifier) {
    var info by remember { mutableStateOf<BufferingInfo?>(null) }
    val connection = rememberConnectionLabel()
    LaunchedEffect(Unit) {
        while (true) {
            info = StreamingPlaybackService.getInstance()?.bufferingInfo()
            delay(POLL_MS)
        }
    }
    val current = info ?: return
    val style = MaterialTheme.typography.bodySmall
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (current.neededMs > 0) {
            LinearProgressIndicator(
                progress = { (current.bufferedMs.toFloat() / current.neededMs).coerceIn(0f, 1f) },
                modifier = Modifier.width(BAR_WIDTH).height(3.dp),
                color = CinemaAccent,
                trackColor = CinemaTextSecondary.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text =
                stringResource(
                    R.string.buffering_detail_progress,
                    BufferingFormat.seconds(current.bufferedMs),
                    BufferingFormat.seconds(current.neededMs),
                    (current.waitingMs / 1000).toString(),
                ),
            style = style,
            color = CinemaTextSecondary,
        )
        val speed =
            buildList {
                connection?.let { add(stringResource(it)) }
                add(stringResource(R.string.buffering_detail_receiving, speedText(current.receivingBps)))
                current.streamBps?.let { add(stringResource(R.string.buffering_detail_stream_needs, speedText(it))) }
                current.host?.let { add(it) }
            }.joinToString(SEPARATOR)
        Text(
            text = speed,
            style = style,
            color = if (current.tooSlow) CinemaWarning else CinemaTextSecondary,
            textAlign = TextAlign.Center,
        )
        if (current.hasTrouble) {
            val trouble =
                buildList {
                    add(pluralStringResource(R.plurals.buffering_detail_errors, current.errors, current.errors))
                    current.lastError?.let { add(it) }
                    add(stringResource(R.string.buffering_detail_received, sizeText(current.bytesReceived)))
                }.joinToString(SEPARATOR)
            Text(text = trouble, style = style, color = CinemaWarning, textAlign = TextAlign.Center)
        }
    }
}

/** The connection in use ("Ethernet", "Wi-Fi"…), read again every few seconds; null until known. */
@Composable
fun rememberConnectionLabel(): Int? {
    val context = LocalContext.current.applicationContext
    val label by produceState<Int?>(null) {
        while (true) {
            value = currentConnectionLabel(context)
            delay(CONNECTION_POLL_MS)
        }
    }
    return label
}

@Composable
private fun speedText(bps: Long): String =
    if (bps >= 1_000_000) {
        stringResource(R.string.buffering_mbps, BufferingFormat.oneDecimal(bps / 1_000_000.0))
    } else {
        stringResource(R.string.buffering_kbps, (bps / 1000).toString())
    }

@Composable
private fun sizeText(bytes: Long): String =
    if (bytes >= 1_000_000) {
        stringResource(R.string.buffering_mb, BufferingFormat.oneDecimal(bytes / 1_000_000.0))
    } else {
        stringResource(R.string.buffering_kb, (bytes / 1000).toString())
    }

/** The numbers of [BufferingDetails], one decimal in the device's language ("1.4", "1,4"). */
internal object BufferingFormat {
    fun seconds(ms: Long): String = oneDecimal(ms / 1000.0)

    fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
}

private const val POLL_MS = 500L
private const val CONNECTION_POLL_MS = 2_000L
private const val SEPARATOR = " · "
private val BAR_WIDTH = 240.dp
