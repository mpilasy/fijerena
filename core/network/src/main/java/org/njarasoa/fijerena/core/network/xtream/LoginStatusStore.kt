package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import org.json.JSONObject

/**
 * The last status each login's panel gave ([XtreamLoginCheck.status]), per source, so a source's
 * Logins list shows it at once and keeps showing it while the panel can't be reached. This device
 * only: not synced or backed up.
 */
class LoginStatusStore(
    context: Context,
) {
    data class Saved(
        val status: XtreamLoginCheck.Status,
        val checkedAtMs: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(
        providerId: Long,
        username: String,
    ): Saved? =
        prefs.getString(key(providerId, username), null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                Saved(
                    XtreamLoginCheck.Status(
                        active = json.getBoolean("active"),
                        statusText = json.getString("statusText"),
                        expiresAtSec = json.optLong("expiresAtSec", -1).takeIf { it > 0 },
                        activeCons = json.optInt("activeCons", -1).takeIf { it >= 0 },
                        maxConnections = json.optInt("maxConnections", -1).takeIf { it >= 0 },
                    ),
                    json.getLong("checkedAtMs"),
                )
            }.getOrNull()
        }

    fun put(
        providerId: Long,
        username: String,
        status: XtreamLoginCheck.Status,
        checkedAtMs: Long,
    ) {
        val json =
            JSONObject()
                .put("active", status.active)
                .put("statusText", status.statusText)
                .put("expiresAtSec", status.expiresAtSec ?: -1)
                .put("activeCons", status.activeCons ?: -1)
                .put("maxConnections", status.maxConnections ?: -1)
                .put("checkedAtMs", checkedAtMs)
        prefs.edit().putString(key(providerId, username), json.toString()).apply()
    }

    private fun key(
        providerId: Long,
        username: String,
    ) = "${providerId}_$username"

    private companion object {
        const val PREFS = "login_status"
    }
}
