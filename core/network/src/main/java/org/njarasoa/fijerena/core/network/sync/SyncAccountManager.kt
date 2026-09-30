package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Linking this device to a sync account: creating one, pairing with one, or leaving it. See
 * `docs/plans/20260929_live-sync-plan.md` → Security (pairing). Phase 8 adds the account key and
 * the QR code; Phase 9 the settings screen.
 */
class SyncAccountManager(
    private val context: Context,
    private val api: SyncApi = SyncApi(),
    private val store: SyncAccountStore = SyncAccountStore(context),
) {
    /** A new account on [serverUrl], with this device as its first. */
    suspend fun createAccount(
        serverUrl: String,
        deviceName: String,
        setupSecret: String? = null,
    ) {
        checkServer(serverUrl)
        link(serverUrl, api.createAccount(serverUrl, deviceName, setupSecret))
    }

    /** Joins the account a pairing code (from [createPairingCode] on another device) belongs to. */
    suspend fun pair(
        serverUrl: String,
        code: String,
        deviceName: String,
    ) {
        checkServer(serverUrl)
        link(serverUrl, api.pair(serverUrl, code, deviceName))
    }

    /** A single-use code, valid for ten minutes, for another device to join this account. */
    suspend fun createPairingCode(): SyncWire.Pairing {
        val link = store.link ?: error("Not linked to a sync account")
        return api.createPairing(link.serverUrl, link.deviceToken)
    }

    /** Stops syncing; local data stays as it is. */
    fun unlink() = store.unlink()

    private suspend fun checkServer(serverUrl: String) {
        val info = api.info(serverUrl)
        if (info.service != "fijerena-sync" || info.protocol != SyncWire.PROTOCOL) {
            throw SyncApiException(0, "Not a compatible Fijerena sync server (${info.service}, protocol ${info.protocol})")
        }
    }

    private suspend fun link(
        serverUrl: String,
        credentials: SyncWire.DeviceCredentials,
    ) {
        store.saveLink(serverUrl, credentials)
        // A server that has seen nothing from here: every version is to be sent again.
        SettingsDatabase.getInstance(context).settingsSyncDao().markAllPending()
        XtreamDatabase.getInstance(context).syncVersionDao().markAllPending()
    }
}
