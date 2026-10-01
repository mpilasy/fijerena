package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import java.security.KeyPair

/**
 * Linking this device to a sync account: creating one, joining one by scanning a QR code, or —
 * for a device that can't scan — being handed one by a device that can; and leaving it. See
 * `docs/plans/20260929_live-sync-plan.md` → Security (pairing). Phase 9 adds the screens.
 */
class SyncAccountManager(
    private val context: Context,
    private val api: SyncApi = SyncApi(),
    private val store: SyncAccountStore = SyncAccountStore(context),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** A new account on [serverUrl], with this device as its first and a fresh account key. */
    suspend fun createAccount(
        serverUrl: String,
        deviceName: String,
        setupSecret: String? = null,
    ) {
        checkServer(serverUrl)
        link(serverUrl, api.createAccount(serverUrl, deviceName, setupSecret), AccountKeyCrypto.newAccountKey())
    }

    /** A QR code for a device with a camera to join this account: shown only on demand. */
    suspend fun createInvite(): PairingQr.Invite {
        val link = store.link ?: error("Not linked to a sync account")
        val pairing = api.createPairing(link.serverUrl, link.deviceToken)
        return PairingQr.Invite(link.serverUrl, pairing.code, link.accountKey)
    }

    /**
     * Whatever a scanned pairing QR code asks for: an [PairingQr.Invite] joins its account; a
     * [PairingQr.HandoffRequest] (shown by a device that can't scan) hands it this device's account.
     */
    suspend fun scanned(
        qr: PairingQr,
        deviceName: String,
    ) {
        when (qr) {
            is PairingQr.Invite -> {
                checkServer(qr.serverUrl)
                link(qr.serverUrl, api.pair(qr.serverUrl, qr.pairingCode, deviceName), qr.accountKey)
            }
            is PairingQr.HandoffRequest -> handOver(qr)
        }
    }

    /** A device that can't scan, wanting to join: what it shows, and what it holds to finish. */
    class Handoff internal constructor(
        val qr: PairingQr.HandoffRequest,
        internal val keyPair: KeyPair,
        val expiresAt: Long,
    )

    /** Starts a handoff on [serverUrl]: show [Handoff.qr], then [awaitHandoff]. */
    suspend fun startHandoff(serverUrl: String): Handoff {
        checkServer(serverUrl)
        val keyPair = HandoffKeys.newKeyPair()
        val opened = api.openHandoff(serverUrl)
        return Handoff(PairingQr.HandoffRequest(serverUrl.trimEnd('/'), opened.handoffId, keyPair.public.encoded), keyPair, opened.expiresAt)
    }

    /** Waits for a device of an account to scan the QR code, then joins that account. */
    suspend fun awaitHandoff(
        handoff: Handoff,
        deviceName: String,
    ) {
        val url = handoff.qr.serverUrl
        val id = handoff.qr.handoffId
        while (true) {
            if (System.currentTimeMillis() > handoff.expiresAt) throw SyncApiException(410, "The pairing code expired")
            val collected = api.collectHandoff(url, id)
            if (collected.ready) {
                val key = HandoffKeys.sharedKey(handoff.keyPair.private, HandoffKeys.publicKey(B64URL.decode(collected.senderKey!!)), id)
                val opened = Aead.open(key, collected.sealed!!, id.toByteArray()) ?: throw SyncApiException(0, "The handed-over account couldn't be opened")
                val secret = json.decodeFromString<SyncWire.HandoffSecret>(String(opened))
                link(url, api.pair(url, secret.pairingCode, deviceName), B64URL.decode(secret.accountKey))
                return
            }
            delay(HANDOFF_POLL_MS)
        }
    }

    /** Whether [serverUrl] is a compatible sync server, and whether creating an account needs its setup secret. */
    suspend fun serverInfo(serverUrl: String): SyncWire.Info = checkServer(serverUrl)

    /** The account's devices, this one marked `current`. */
    suspend fun devices(): List<SyncWire.Device> {
        val link = store.link ?: error("Not linked to a sync account")
        return api.devices(link.serverUrl, link.deviceToken)
    }

    /**
     * Cuts a device off: the server refuses its token from now on. It still knows the account key
     * (rotating it is a later step — see the live-sync plan), but can no longer reach the records.
     */
    suspend fun revoke(deviceId: String) {
        val link = store.link ?: error("Not linked to a sync account")
        api.revoke(link.serverUrl, link.deviceToken, deviceId)
    }

    /**
     * Stops syncing; local data stays as it is. The device also takes itself off the account's
     * list, so it doesn't linger there as a device long gone — unless the server can't be reached,
     * which mustn't stop anyone leaving: then it stays listed until removed from another device.
     */
    suspend fun leave() {
        store.link?.let { link ->
            try {
                api.revoke(link.serverUrl, link.deviceToken, link.deviceId)
            } catch (e: SyncApiException) {
                android.util.Log.w("SyncAccountManager", "Left without removing this device from the server: ${e.message}")
            }
        }
        store.unlink()
    }

    private suspend fun handOver(request: PairingQr.HandoffRequest) {
        val link = store.link ?: error("Not linked to a sync account")
        if (request.serverUrl.trimEnd('/') != link.serverUrl) {
            throw SyncApiException(0, "That device is set up for another sync server (${request.serverUrl})")
        }
        val pairing = api.createPairing(link.serverUrl, link.deviceToken)
        val own = HandoffKeys.newKeyPair()
        val key = HandoffKeys.sharedKey(own.private, HandoffKeys.publicKey(request.publicKey), request.handoffId)
        val secret = json.encodeToString(SyncWire.HandoffSecret(pairing.code, B64URL.encodeToString(link.accountKey)))
        val sealed = Aead.seal(key, secret.toByteArray(), request.handoffId.toByteArray())
        api.fillHandoff(link.serverUrl, link.deviceToken, request.handoffId, sealed, B64URL.encodeToString(own.public.encoded))
    }

    private suspend fun checkServer(serverUrl: String): SyncWire.Info {
        val info = api.info(serverUrl)
        if (info.service != "fijerena-sync" || info.protocol != SyncWire.PROTOCOL) {
            throw SyncApiException(SyncApiException.INCOMPATIBLE, "Not a compatible Fijerena sync server (${info.service}, protocol ${info.protocol})")
        }
        return info
    }

    private suspend fun link(
        serverUrl: String,
        credentials: SyncWire.DeviceCredentials,
        accountKey: ByteArray,
    ) {
        store.saveLink(serverUrl, credentials, accountKey)
        // A server that has seen nothing from here: every version is to be sent again.
        SettingsDatabase.getInstance(context).settingsSyncDao().markAllPending()
        XtreamDatabase.getInstance(context).syncVersionDao().markAllPending()
    }

    private companion object {
        const val HANDOFF_POLL_MS = 2_000L
    }
}
