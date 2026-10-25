package org.njarasoa.fijerena.core.ui.utils

/**
 * Tapping the version row in About 7 times makes the profile in use Expert, as on Android itself
 * (the public release plan, P1), but only while no profile is Expert (the profile rules plan,
 * PR-3). The app always keeps an Expert ([org.njarasoa.fijerena.core.network.profile.ProfileRepository.ensureAnExpert]),
 * so in practice the taps do nothing and say nothing. Only an Expert makes another profile Expert,
 * on that profile's page.
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
    }

    /** What the tap after [tapsSoFar] taps does. */
    fun onTap(
        tapsSoFar: Int,
        expertExists: Boolean,
    ): Outcome {
        val taps = tapsSoFar + 1
        return when {
            expertExists -> Outcome.Nothing
            taps >= TAPS_TO_ENABLE -> Outcome.Enable
            taps >= HINT_FROM_TAP -> Outcome.TapsLeft(TAPS_TO_ENABLE - taps)
            else -> Outcome.Nothing
        }
    }

    /**
     * Whether a profile is Expert. [profileIds] is null until the list has loaded, which counts as
     * yes: the taps stay quiet until the list says otherwise.
     */
    fun expertExists(
        profileIds: List<String>?,
        isExpert: (String) -> Boolean,
    ): Boolean = profileIds == null || profileIds.any(isExpert)
}
