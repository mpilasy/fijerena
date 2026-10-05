package org.njarasoa.fijerena.core.player.source

/**
 * Another login for a refused stream. A source can hold several logins on one panel (shared
 * logins); when the provider refuses a stream as [AccountBusy], the player asks this for the same
 * stream on another free login before waiting. `core:network` implements it (it holds the logins)
 * and sets [installed]; this module can't depend on that one. See
 * docs/plans/20261005_shared-logins-plan.md → Phase 3.
 */
interface AlternateLogin {
    /**
     * The same stream as [refusedUri] on another free login of its source, or null when there is
     * none (no extra logins, all busy, not an Xtream URL). Marks [refusedUri]'s login busy on this
     * device, so it isn't offered again for a while.
     */
    suspend fun next(refusedUri: String): String?

    /** How many logins [uri]'s source has: 1 when it has no extra ones or isn't known. */
    fun loginCount(uri: String): Int

    companion object {
        @Volatile
        var installed: AlternateLogin? = null
    }
}
