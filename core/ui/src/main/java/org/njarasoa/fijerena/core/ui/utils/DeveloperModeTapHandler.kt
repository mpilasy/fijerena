package org.njarasoa.fijerena.core.ui.utils

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.ui.R

/**
 * The version row's click on TV and phone: counts taps toward developer mode
 * ([DeveloperModeTaps]) and says where they stand in a short toast.
 */
@Composable
fun rememberDeveloperModeTapHandler(
    isDevMode: Boolean,
    onEnable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    return {
        val outcome = DeveloperModeTaps.onTap(taps, isDevMode)
        val message =
            when (outcome) {
                DeveloperModeTaps.Outcome.Nothing -> {
                    null
                }

                is DeveloperModeTaps.Outcome.TapsLeft -> {
                    context.getString(R.string.settings_developer_mode_taps_left, outcome.count)
                }

                DeveloperModeTaps.Outcome.Enable -> {
                    onEnable()
                    context.getString(R.string.settings_developer_mode_enabled)
                }

                DeveloperModeTaps.Outcome.AlreadyOn -> {
                    context.getString(R.string.settings_developer_mode_already_on)
                }
            }
        taps = if (isDevMode || outcome == DeveloperModeTaps.Outcome.Enable) 0 else taps + 1
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
