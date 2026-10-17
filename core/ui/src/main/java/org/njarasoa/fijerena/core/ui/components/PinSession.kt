package org.njarasoa.fijerena.core.ui.components

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which profiles' PINs were typed in this session (docs/plans/archive/20261017_profile-pin-plan.md): a
 * profile with a PIN is used only while unlocked here. Kept in memory, so a new process asks again;
 * the app spending [RELOCK_AFTER_MS] or more in the background locks everything, and changing the
 * profile in use keeps only that profile unlocked (a switch back asks again). Compose state, so the
 * nav hosts' lock gate follows it.
 */
object PinSession {
    const val RELOCK_AFTER_MS = 5 * 60_000L

    private var unlocked by mutableStateOf(emptySet<String>())
    private var started = 0
    private var stoppedAt = 0L

    fun isUnlocked(profileId: String): Boolean = profileId in unlocked

    fun unlock(profileId: String) {
        unlocked = unlocked + profileId
    }

    /** The profile in use is now [profileId]: every other profile locks. */
    fun onActiveProfile(profileId: String) {
        if (unlocked.any { it != profileId }) unlocked = unlocked.intersect(setOf(profileId))
    }

    /** Counts the app's started activities, to lock after a long enough time in the background. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    if (started++ == 0) onForeground(SystemClock.elapsedRealtime())
                }

                override fun onActivityStopped(activity: Activity) {
                    if (--started == 0) onBackground(SystemClock.elapsedRealtime())
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    internal fun onForeground(now: Long) {
        if (stoppedAt > 0L && now - stoppedAt >= RELOCK_AFTER_MS) unlocked = emptySet()
        stoppedAt = 0L
    }

    internal fun onBackground(now: Long) {
        stoppedAt = now
    }
}
