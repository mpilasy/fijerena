package org.njarasoa.fijerena.core.network.sports

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** The live channel a reminder tunes: a stream of the profile's sources. */
data class ReminderChannel(
    val providerId: Long,
    val streamId: Int,
    val categoryId: String,
    val name: String,
)

/** A reminder that rang: the game, and its best channel when one was found. */
data class GameReminderAlert(
    val profileId: String,
    val source: String,
    val gameId: String,
    val homeName: String,
    val awayName: String?,
    val competitionName: String,
    val startEpochSec: Long,
    val channel: ReminderChannel?,
)

/** "Tune this channel for this profile": from a tapped reminder notification or the TV banner's Watch. */
data class ReminderTune(
    val profileId: String,
    val channel: ReminderChannel,
)

/**
 * Where a ringing reminder goes in the app process, and the channel a tapped notification asks to
 * tune. See the sports teams plan → Reminders.
 *
 * The TV app's banner host counts itself in [bannerHostStarted] / [bannerHostStopped] while the app
 * is on screen: a reminder then shows as [banner] instead of a notification. The phone has no
 * host, so it always gets the notification.
 *
 * MainActivity of each app hands a tapped notification's intent to [handleLaunchIntent]; the nav
 * host takes [tune] once it can navigate and tunes the channel as a guide result does.
 */
object GameReminderAlerts {
    private const val EXTRA_PROFILE_ID = "org.njarasoa.fijerena.reminder.PROFILE_ID"
    private const val EXTRA_PROVIDER_ID = "org.njarasoa.fijerena.reminder.PROVIDER_ID"
    private const val EXTRA_STREAM_ID = "org.njarasoa.fijerena.reminder.STREAM_ID"
    private const val EXTRA_CATEGORY_ID = "org.njarasoa.fijerena.reminder.CATEGORY_ID"
    private const val EXTRA_CHANNEL_NAME = "org.njarasoa.fijerena.reminder.CHANNEL_NAME"

    private val hosts = AtomicInteger(0)
    private val bannerState = MutableStateFlow<GameReminderAlert?>(null)
    private val tuneState = MutableStateFlow<ReminderTune?>(null)

    /** The reminder the TV banner shows; null when none. A new one replaces it. */
    val banner: StateFlow<GameReminderAlert?> = bannerState.asStateFlow()

    /** A channel waiting to be tuned; the nav host takes it with [takeTune]. */
    val tune: StateFlow<ReminderTune?> = tuneState.asStateFlow()

    /** Whether a banner host is on screen. */
    val hasBannerHost: Boolean get() = hosts.get() > 0

    fun bannerHostStarted() {
        hosts.incrementAndGet()
    }

    fun bannerHostStopped() {
        hosts.updateAndGet { maxOf(0, it - 1) }
    }

    /** Shows [alert] in the banner; false when no banner host is on screen. */
    fun showBanner(alert: GameReminderAlert): Boolean {
        if (!hasBannerHost) return false
        bannerState.value = alert
        return true
    }

    /** Closes the banner when it still shows [alert]. */
    fun dismissBanner(alert: GameReminderAlert) {
        bannerState.compareAndSet(alert, null)
    }

    fun requestTune(tune: ReminderTune) {
        tuneState.value = tune
    }

    /** The waiting channel, taken once: null when another caller took it first. */
    fun takeTune(): ReminderTune? = tuneState.getAndSetNull()

    /** Puts [tune] into an intent that opens the app ([handleLaunchIntent] reads it). */
    fun putTune(
        intent: Intent,
        tune: ReminderTune,
    ): Intent =
        intent
            .putExtra(EXTRA_PROFILE_ID, tune.profileId)
            .putExtra(EXTRA_PROVIDER_ID, tune.channel.providerId)
            .putExtra(EXTRA_STREAM_ID, tune.channel.streamId)
            .putExtra(EXTRA_CATEGORY_ID, tune.channel.categoryId)
            .putExtra(EXTRA_CHANNEL_NAME, tune.channel.name)

    /** The channel a tapped reminder notification asks to tune, or null. */
    fun tuneOf(intent: Intent?): ReminderTune? {
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID) ?: return null
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return null
        val streamId = intent.getIntExtra(EXTRA_STREAM_ID, -1).takeIf { it >= 0 } ?: return null
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1L).takeIf { it >= 0 } ?: return null
        return ReminderTune(
            profileId,
            ReminderChannel(providerId, streamId, categoryId, intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()),
        )
    }

    /**
     * MainActivity's `onCreate` (not on a recreation) and `onNewIntent`: a tapped reminder asks to
     * tune its channel. The extras are removed, so the same intent never tunes twice.
     */
    fun handleLaunchIntent(intent: Intent?) {
        val tune = tuneOf(intent) ?: return
        listOf(
            EXTRA_PROFILE_ID,
            EXTRA_PROVIDER_ID,
            EXTRA_STREAM_ID,
            EXTRA_CATEGORY_ID,
            EXTRA_CHANNEL_NAME,
        ).forEach { intent?.removeExtra(it) }
        requestTune(tune)
    }

    private fun <T> MutableStateFlow<T?>.getAndSetNull(): T? {
        while (true) {
            val current = value ?: return null
            if (compareAndSet(current, null)) return current
        }
    }
}
