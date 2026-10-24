package org.njarasoa.fijerena.core.network.profile

/**
 * The child lock (the settings flow review plan, SF-D6 to SF-D9): a device that uses a child
 * profile leaves it only after an Expert's PIN, while at least one Expert has a PIN. A switch to
 * another child asks it too; picking the same child asks nothing. With no Expert PIN, there is no
 * lock.
 */
sealed interface SwitchLock {
    /** The switch needs no PIN. */
    data object Free : SwitchLock

    /** One of [pinHolders]' PINs (the Expert profiles with a PIN, oldest first) must be typed first. */
    data class AskPin(
        val pinHolders: List<String>,
    ) : SwitchLock

    /** The profiles could not be read while a child profile is in use: no switch (fail closed). */
    data object Refused : SwitchLock

    companion object {
        /**
         * The lock on a switch from [activeId] to [targetId]. [profileIds] are every profile, oldest
         * first, or null when they could not be read; [isChild] and [isAdminPinHolder] (Expert with
         * a PIN) read each profile's stored flags.
         */
        fun of(
            activeId: String,
            targetId: String,
            profileIds: List<String>?,
            isChild: (String) -> Boolean,
            isAdminPinHolder: (String) -> Boolean,
        ): SwitchLock {
            val holders = profileIds.orEmpty().filter(isAdminPinHolder)
            return when {
                targetId == activeId || !isChild(activeId) -> Free

                // An empty list is not a real household (there is always the child in use): fail closed.
                profileIds.isNullOrEmpty() -> Refused

                holders.isEmpty() -> Free

                else -> AskPin(holders)
            }
        }
    }
}
