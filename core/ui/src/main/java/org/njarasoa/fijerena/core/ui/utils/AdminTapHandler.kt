package org.njarasoa.fijerena.core.ui.utils

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
 * The version row's click on TV and phone: while no profile is Admin, counts taps toward
 * Admin ([AdminTaps]) and says where they stand in a short toast. While one is, it does and
 * says nothing. [onEnable] returns whether Admin is on now.
 */
@Composable
fun rememberAdminTapHandler(
    adminExists: Boolean,
    onEnable: () -> Boolean,
): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    var taps by remember { mutableIntStateOf(0) }
    return taps@{
        // A child profile is never Admin (the household UX plan): its taps count nothing.
        val settings = AppSettings(context.applicationContext)
        if (settings.isChildProfile(settings.activeProfileId)) return@taps
        val outcome = AdminTaps.onTap(taps, adminExists)
        val message =
            when (outcome) {
                AdminTaps.Outcome.Nothing -> {
                    null
                }

                is AdminTaps.Outcome.TapsLeft -> {
                    resources.getString(R.string.settings_admin_taps_left, outcome.count)
                }

                AdminTaps.Outcome.Enable -> {
                    if (onEnable()) resources.getString(R.string.settings_admin_enabled) else null
                }
            }
        taps = if (adminExists || outcome == AdminTaps.Outcome.Enable) 0 else taps + 1
        message?.let(AppNotices::show)
    }
}
