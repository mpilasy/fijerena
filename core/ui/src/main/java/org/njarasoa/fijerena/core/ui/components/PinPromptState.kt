package org.njarasoa.fijerena.core.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.ui.R

/** What a PIN prompt is for (the profile pin plan). */
sealed interface PinRequest {
    val profileId: String

    /** [profileId]'s PIN must be typed; [title] says why ("Enter Kid's PIN"). */
    data class Check(
        override val profileId: String,
        val title: String,
    ) : PinRequest

    /**
     * Any of [profileIds]' PINs — the Expert profiles' (admin, for now): the 7 taps on the version
     * and Add profile on TV's picker while an Expert has a PIN. [profileId] is the first of them.
     */
    data class AnyOf(
        val profileIds: List<String>,
        val title: String,
    ) : PinRequest {
        override val profileId: String get() = profileIds.first()
    }

    /**
     * The child lock: any of [profileIds]' PINs (the Expert profiles with a PIN) before this device
     * leaves a child profile for [targetId]. Wrong tries count on the child lock's own count. When
     * the PIN is [targetId]'s own, [targetId] is unlocked too, so its Settings ask nothing more.
     */
    data class LeaveChild(
        val profileIds: List<String>,
        val targetId: String,
        val title: String,
    ) : PinRequest {
        override val profileId: String get() = profileIds.first()
    }

    /** A new PIN for [profileId], typed twice; [warning] shows under the dots (the last Expert's). */
    data class Choose(
        override val profileId: String,
        val warning: String? = null,
    ) : PinRequest
}

/**
 * One PIN prompt, the same on TV (number pad) and phone (number keyboard): the digits typed so
 * far, the title and the message under the dots. A [PinRequest.Check] calls [onDone] once the
 * right PIN is typed; a [PinRequest.Choose] once the same new PIN is typed twice, after saving it.
 */
@Stable
class PinPromptState internal constructor(
    private val context: Context,
    private val pins: ProfilePins,
    val request: PinRequest,
    private val onDone: () -> Unit,
) {
    var typed by mutableStateOf("")
        private set
    var message by mutableStateOf((request as? PinRequest.Choose)?.warning)
        private set

    /** [message] reports a wrong or mismatched PIN (shown as an error), not a note. */
    var messageIsError by mutableStateOf(false)
        private set
    private var first: String? by mutableStateOf(null)

    val title: String
        get() =
            when (request) {
                is PinRequest.Check -> request.title
                is PinRequest.AnyOf -> request.title
                is PinRequest.LeaveChild -> request.title
                is PinRequest.Choose -> context.getString(if (first == null) R.string.pin_choose else R.string.pin_repeat)
            }

    fun type(digit: Char) {
        if (digit !in '0'..'9' || typed.length >= ProfilePins.LENGTH) return
        typed += digit
        if (typed.length == ProfilePins.LENGTH) submit(typed)
    }

    fun erase() {
        typed = typed.dropLast(1)
    }

    /** All of [text]'s digits at once — a phone keyboard's field. */
    fun replace(text: String) {
        typed = ""
        text.filter { it in '0'..'9' }.take(ProfilePins.LENGTH).forEach(::type)
    }

    private fun submit(pin: String) {
        typed = ""
        when (request) {
            is PinRequest.Check -> {
                val check = pins.check(request.profileId, pin)
                message = check.message(context)
                messageIsError = message != null
                if (check == ProfilePins.Check.Right) {
                    PinSession.unlock(request.profileId)
                    onDone()
                }
            }

            is PinRequest.AnyOf -> {
                val check = pins.checkAny(request.profileIds, pin)
                message = check.message(context)
                messageIsError = message != null
                if (check == ProfilePins.Check.Right) onDone()
            }

            is PinRequest.LeaveChild -> {
                val result = pins.checkToLeaveChild(request.profileIds, pin)
                message = result.check.message(context)
                messageIsError = message != null
                if (result.check == ProfilePins.Check.Right) {
                    // The switch keeps the new profile in use unlocked (PinSession.onActiveProfile).
                    if (result.pinOf == request.targetId) PinSession.unlock(request.targetId)
                    onDone()
                }
            }

            is PinRequest.Choose -> {
                val chosen = first
                when {
                    chosen == null -> {
                        first = pin
                        message = request.warning
                        messageIsError = false
                    }

                    chosen == pin -> {
                        pins.set(request.profileId, pin)
                        // Whoever just chose it knows it.
                        PinSession.unlock(request.profileId)
                        onDone()
                    }

                    else -> {
                        first = null
                        message = context.getString(R.string.pin_mismatch)
                        messageIsError = true
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPinPromptState(
    pins: ProfilePins,
    request: PinRequest,
    onDone: () -> Unit,
): PinPromptState {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    return remember(request) { PinPromptState(context, pins, request) { done() } }
}
