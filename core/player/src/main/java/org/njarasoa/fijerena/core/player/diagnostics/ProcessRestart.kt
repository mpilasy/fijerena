package org.njarasoa.fijerena.core.player.diagnostics

import android.app.Activity
import android.content.Intent

/**
 * Starts the app again in a fresh process: whatever `Application.onCreate()` decided for this one
 * (safe mode, a blocked database) is decided again.
 */
fun restartProcess(activity: Activity) {
    activity.startActivity(Intent.makeRestartActivityTask(activity.componentName))
    Runtime.getRuntime().exit(0)
}
