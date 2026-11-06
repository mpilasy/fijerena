package org.njarasoa.fijerena.core.ui.epg

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.NumberUtils

/** A guide source card's line while its data is cleared (Clear guide data), TV and phone. */
@Composable
fun guideClearText(progress: EpgIndexer.ClearProgress): String {
    val resources = LocalContext.current.resources
    val text =
        when {
            progress is EpgIndexer.ClearProgress.Deleting && progress.total > 0 -> {
                stringResource(
                    R.string.epg_phase_clearing_count,
                    NumberUtils.formatCount(resources, progress.done),
                    NumberUtils.formatCount(resources, progress.total),
                )
            }

            progress is EpgIndexer.ClearProgress.FreeingSpace -> {
                stringResource(R.string.epg_phase_freeing_space)
            }

            else -> {
                stringResource(R.string.epg_phase_clearing)
            }
        }
    return stringResource(R.string.epg_source_phase_format, text)
}

/** The bar's fill while [progress] deletes rows; null (a moving bar) before the count and while space is freed. */
fun guideClearFraction(progress: EpgIndexer.ClearProgress): Float? =
    (progress as? EpgIndexer.ClearProgress.Deleting)
        ?.takeIf { it.total > 0 }
        ?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
