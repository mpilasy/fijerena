package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.StoredError

/**
 * A kept error ([StoredError]: a guide source's or a source sync's last error) as a message in the
 * app language in use, with its raw text below it while Developer Mode is on. An old value (message
 * text from before the code format) shows the generic message.
 */
@Composable
fun storedErrorText(stored: String): String {
    val context = LocalContext.current
    val devMode = remember(context) { AppSettings(context.applicationContext).isDevMode }
    return StoredError.message(stored, context, devMode)
}
