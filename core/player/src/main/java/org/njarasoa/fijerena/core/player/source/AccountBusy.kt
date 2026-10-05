package org.njarasoa.fijerena.core.player.source

/**
 * HTTP statuses IPTV providers send when the account already has its maximum number of streams
 * open ("connection limit"). Retrying at once never helps: the slot frees only when the other
 * stream ends — and some providers (bears: 460) keep a stopped stream counted for about five
 * minutes when the next one comes from a different internet address (a phone on mobile data, a
 * device routed through a VPN), so switching devices is refused for that long.
 */
object AccountBusy {
    private val CODES = setOf(456, 458, 460, 511)

    fun isAccountBusy(httpStatus: Int?): Boolean = httpStatus in CODES

    /** How often a refused stream is tried again while the account is busy. */
    const val RETRY_INTERVAL_MS = 20_000L

    /** Longer than the ~5 minutes a provider can keep a stopped stream counted. */
    const val MAX_WAIT_MS = 6 * 60_000L
}

/**
 * The waiting period for one playback refused as [AccountBusy]. [next] is called on each refusal
 * and says whether to try again or give up. A refusal long after the last one (playback started
 * in between, then was refused again) starts a new waiting period.
 */
class AccountBusyWait {
    private var sinceMs = NONE
    private var lastMs = NONE

    sealed class Decision {
        data object RetryLater : Decision()

        data object GiveUp : Decision()
    }

    fun next(nowMs: Long): Decision {
        if (sinceMs == NONE || nowMs - lastMs > AccountBusy.RETRY_INTERVAL_MS * 3) sinceMs = nowMs
        lastMs = nowMs
        if (nowMs - sinceMs < AccountBusy.MAX_WAIT_MS) return Decision.RetryLater
        reset()
        return Decision.GiveUp
    }

    fun reset() {
        sinceMs = NONE
        lastMs = NONE
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}

/**
 * What to do with [AlternateLogin.next]'s answer to a refusal once it arrives (it can take a few
 * seconds): play the other login's URL, wait on the refused one ([AccountBusyWait]), or drop the
 * answer because the playback it was asked for is gone (a new stream, Stop, the service released).
 */
object LoginSwitch {
    sealed class Decision {
        data class Switch(
            val url: String,
        ) : Decision()

        data object Wait : Decision()

        data object Drop : Decision()
    }

    fun decide(
        nextUrl: String?,
        askedSession: String,
        currentSession: String,
        refusedUrl: String,
        currentUrl: String,
        released: Boolean,
    ): Decision =
        when {
            released || askedSession != currentSession || refusedUrl != currentUrl -> Decision.Drop
            nextUrl == null -> Decision.Wait
            else -> Decision.Switch(nextUrl)
        }
}
