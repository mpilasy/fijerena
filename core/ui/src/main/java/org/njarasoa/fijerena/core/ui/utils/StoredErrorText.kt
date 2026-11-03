package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.StoredError
import org.njarasoa.fijerena.core.ui.history.LocalNerd

/**
 * A kept error ([StoredError]: a guide source's or a source sync's last error) as a message in the
 * app language in use, with its raw text below it while the profile is a Nerd ([LocalNerd]). An old
 * value (message text from before the code format) shows the generic message.
 */
@Composable
fun storedErrorText(stored: String): String {
    val context = LocalContext.current
    return StoredError.message(stored, context, includeRaw = LocalNerd.current)
}
