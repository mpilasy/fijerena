package org.njarasoa.fijerena.core.network.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * The sources whose server refused the saved password on this device. Each app's root shows a
 * pop-up for the first one, with a shortcut to the source's password field. A device that is still
 * signed in does not use the password, so a wrong password can travel the sync group unseen until
 * a device that must sign in again gets refused.
 *
 * Only Jellyfin reports here: an Xtream server also refuses an expired account, where a new
 * password does not help.
 */
object RefusedLogins {
    private val refused = MutableStateFlow<List<Long>>(emptyList())

    // Not Now: no pop-up for the source again until it signs in, its login changes or the app
    // starts again.
    private val dismissed = ConcurrentHashMap.newKeySet<Long>()

    /** The source the pop-up is for, or null. */
    val first: Flow<Long?> = refused.map { it.firstOrNull() }.distinctUntilChanged()

    /** The server of source [providerId] refused its saved password. Id 0 is the source form's test login. */
    fun report(providerId: Long) {
        if (providerId <= 0 || providerId in dismissed) return
        refused.update { if (providerId in it) it else it + providerId }
    }

    /** Source [providerId] signed in or its login changed: a later refusal shows the pop-up again. */
    fun forget(providerId: Long) {
        dismissed.remove(providerId)
        refused.update { it - providerId }
    }

    /** The pop-up for source [providerId] closed: no pop-up for it until it signs in. */
    fun dismiss(providerId: Long) {
        dismissed.add(providerId)
        refused.update { it - providerId }
    }
}
