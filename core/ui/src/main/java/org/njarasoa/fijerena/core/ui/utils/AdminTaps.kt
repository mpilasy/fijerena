package org.njarasoa.fijerena.core.ui.utils

/**
 * Tapping the version row in About 7 times makes the profile in use Admin, as on Android itself
 * (the public release plan, P1), but only while no profile is Admin (the profile rules plan,
 * PR-3). The app always keeps an Admin ([org.njarasoa.fijerena.core.network.profile.ProfileRepository.ensureAnAdmin]),
 * so in practice the taps do nothing and say nothing. Only an Admin makes another profile Admin,
 * on that profile's page.
 */
object AdminTaps {
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
        adminExists: Boolean,
    ): Outcome {
        val taps = tapsSoFar + 1
        return when {
            adminExists -> Outcome.Nothing
            taps >= TAPS_TO_ENABLE -> Outcome.Enable
            taps >= HINT_FROM_TAP -> Outcome.TapsLeft(TAPS_TO_ENABLE - taps)
            else -> Outcome.Nothing
        }
    }

    /**
     * Whether a profile is Admin. [profileIds] is null until the list has loaded, which counts as
     * yes: the taps stay quiet until the list says otherwise.
     */
    fun adminExists(
        profileIds: List<String>?,
        isAdmin: (String) -> Boolean,
    ): Boolean = profileIds == null || profileIds.any(isAdmin)
}
