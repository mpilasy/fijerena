package org.njarasoa.fijerena.core.ui.sports

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.sports.GameReminderNotifications

/**
 * What a bell calls when it sets a reminder: asks for the notification permission (Android 13 and
 * later) when the app does not have it yet, so the first reminder can show. Does nothing when the
 * app has it. The reminder is set either way; without the permission it rings only as the TV
 * banner while the TV app is on screen. See the sports teams plan → Reminders.
 *
 * ```
 * val askNotifications = rememberGameReminderPermission()
 * onBell = { askNotifications(); viewModel.setReminder(game) }
 * ```
 */
@Composable
fun rememberGameReminderPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && GameReminderNotifications.needsPermission(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
