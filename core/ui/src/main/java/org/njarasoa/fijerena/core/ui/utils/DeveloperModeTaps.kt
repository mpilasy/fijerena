package org.njarasoa.fijerena.core.ui.utils

/**
 * Developer mode is turned on by tapping the version row in About 7 times, as on Android itself
 * (docs/plans/20261011_public-release-fdroid-readiness-plan.md, P1): it exposes unfinished source
 * types and diagnostics, so it is kept out of a new user's way. It is turned off on the profile's
 * page, which shows the switch only while it is on.
 */
object DeveloperModeTaps {
    const val TAPS_TO_ENABLE = 7

    /** From this tap on, each tap says how many are left. */
    private const val HINT_FROM_TAP = 4

    sealed interface Outcome {
        data object Nothing : Outcome

        data class TapsLeft(
            val count: Int,
        ) : Outcome

        data object Enable : Outcome

        data object AlreadyOn : Outcome
    }

    /** What the tap after [tapsSoFar] taps does. */
    fun onTap(
        tapsSoFar: Int,
        alreadyOn: Boolean,
    ): Outcome {
        val taps = tapsSoFar + 1
        return when {
            alreadyOn -> Outcome.AlreadyOn
            taps >= TAPS_TO_ENABLE -> Outcome.Enable
            taps >= HINT_FROM_TAP -> Outcome.TapsLeft(TAPS_TO_ENABLE - taps)
            else -> Outcome.Nothing
        }
    }
}
