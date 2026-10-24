package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.ui.R

/**
 * The profile page's PIN row (the profile pin plan, Phase 2), the same on TV
 * and phone: without a PIN it chooses one; with one, the profile's own page asks for it first,
 * then offers Change or Remove — an Expert on another profile's page goes straight to those (the
 * "forgot my PIN" path). Remove asks for a confirm first. Saved at once, like the theme.
 */
@Stable
class ProfilePinFlow internal constructor(
    private val pins: ProfilePins,
    private val profileId: String,
    private val own: Boolean,
    private val currentTitle: String,
    private val warning: String?,
) {
    enum class Step { CURRENT, OPTIONS, CHOOSE, CONFIRM_REMOVE }

    var hasPin by mutableStateOf(pins.hasPin(profileId))
        private set
    var step by mutableStateOf<Step?>(null)
        private set

    /** The prompt [step] shows, for [CURRENT][Step.CURRENT] and [CHOOSE][Step.CHOOSE]. */
    val request: PinRequest?
        get() =
            when (step) {
                Step.CURRENT -> PinRequest.Check(profileId, currentTitle)
                Step.CHOOSE -> PinRequest.Choose(profileId, warning)
                else -> null
            }

    fun start() {
        step =
            when {
                !hasPin -> Step.CHOOSE
                own -> Step.CURRENT
                else -> Step.OPTIONS
            }
    }

    /** The prompt finished: the current PIN was right, or the new one is saved. */
    fun promptDone() {
        if (step == Step.CHOOSE) {
            hasPin = true
            step = null
        } else {
            step = Step.OPTIONS
        }
    }

    fun change() {
        step = Step.CHOOSE
    }

    /** Remove PIN: the confirm first ([confirmRemove] removes it). */
    fun remove() {
        step = Step.CONFIRM_REMOVE
    }

    fun confirmRemove() {
        pins.remove(profileId)
        hasPin = false
        step = null
    }

    fun cancel() {
        step = null
    }
}

/**
 * [profileId]'s PIN row flow; [own] when it is the profile in use's own page, [lastExpert] when
 * it is the only Expert profile (choosing its PIN then warns how hard a forgotten one is).
 */
@Composable
fun rememberProfilePinFlow(
    pins: ProfilePins,
    profileId: String,
    own: Boolean,
    lastExpert: Boolean,
): ProfilePinFlow {
    val currentTitle = stringResource(R.string.pin_enter_current)
    val warning = if (lastExpert) stringResource(R.string.pin_last_expert_warning) else null
    return remember(profileId, own, warning) { ProfilePinFlow(pins, profileId, own, currentTitle, warning) }
}
