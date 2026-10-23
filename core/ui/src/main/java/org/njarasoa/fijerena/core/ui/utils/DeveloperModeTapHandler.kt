package org.njarasoa.fijerena.core.ui.utils

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R

/**
 * The version row's click on TV and phone: counts taps toward developer mode
 * ([DeveloperModeTaps]) and says where they stand in a short toast. [onEnable] returns whether it
 * is on now — false while it first asks an Expert's PIN, which says nothing yet.
 */
@Composable
fun rememberDeveloperModeTapHandler(
    isDevMode: Boolean,
    onEnable: () -> Boolean,
): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    var taps by remember { mutableIntStateOf(0) }
    return taps@{
        // A child profile is never Expert (docs/plans/20261019_tv-household-ux-plan.md): its taps count nothing.
        val settings = AppSettings(context.applicationContext)
        if (settings.isChildProfile(settings.activeProfileId)) return@taps
        val outcome = DeveloperModeTaps.onTap(taps, isDevMode)
        val message =
            when (outcome) {
                DeveloperModeTaps.Outcome.Nothing -> {
                    null
                }

                is DeveloperModeTaps.Outcome.TapsLeft -> {
                    resources.getString(R.string.settings_developer_mode_taps_left, outcome.count)
                }

                DeveloperModeTaps.Outcome.Enable -> {
                    if (onEnable()) resources.getString(R.string.settings_developer_mode_enabled) else null
                }

                DeveloperModeTaps.Outcome.AlreadyOn -> {
                    resources.getString(R.string.settings_developer_mode_already_on)
                }
            }
        taps = if (isDevMode || outcome == DeveloperModeTaps.Outcome.Enable) 0 else taps + 1
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
