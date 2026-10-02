package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.sync.PairingQr
import org.njarasoa.fijerena.core.network.sync.SyncAccountManager
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncEngine

/**
 * Debug builds only: drives live sync over adb until Phase 9's settings screen exists. Results go
 * to logcat under `SyncDebug`.
 *
 *     adb shell am broadcast -a org.njarasoa.fijerena.DEBUG_SYNC -p org.njarasoa.fijerena --es cmd <cmd> [...]
 *
 * - `setup --es url <server> [--es secret <setup secret>] [--es name <device name>]` — new account
 * - `invite` — logs the text of an invite QR code (this account's key included — debug only)
 * - `scan --es qr '<qr text>' [--es name <device name>]` — what scanning a QR code does: joins an
 *   invite's account, or hands this account to the device showing a handoff code
 * - `handoff --es url <server> [--es name <device name>]` — logs the text of a handoff QR code,
 *   then waits (up to ten minutes) for a device of an account to scan it, and joins
 * - `now` — a sync pass; `status` — link, cursor, last sync; `unlink`
 */
class SyncDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        val app = context.applicationContext as Application
        val pending = goAsync()
        scope.launch {
            try {
                run(app, intent)
            } catch (e: Exception) {
                Log.e(TAG, "${intent.getStringExtra("cmd")} failed: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun run(
        app: Application,
        intent: Intent,
    ) {
        val accounts = SyncAccountManager(app)
        val name = intent.getStringExtra("name") ?: Build.MODEL
        when (val cmd = intent.getStringExtra("cmd")) {
            "setup" -> {
                accounts.createAccount(intent.getStringExtra("url")!!, name, intent.getStringExtra("secret"))
                SyncManager.getInstance(app).onLinkChanged()
                Log.i(TAG, "setup: linked to a new account")
            }

            "invite" -> {
                Log.i(TAG, "invite qr: ${PairingQr.encode(accounts.createInvite())}")
            }

            "scan" -> {
                val qr = PairingQr.decode(intent.getStringExtra("qr")!!) ?: error("not a Fijerena pairing code")
                accounts.scanned(qr, name)
                SyncManager.getInstance(app).onLinkChanged()
                Log.i(TAG, "scan: done (${qr::class.simpleName})")
            }

            "handoff" -> {
                val handoff = accounts.startHandoff(intent.getStringExtra("url")!!)
                Log.i(TAG, "handoff qr: ${PairingQr.encode(handoff.qr)}")
                // Waits well past a broadcast's lifetime: on its own, outside goAsync().
                scope.launch {
                    try {
                        accounts.awaitHandoff(handoff, name)
                        SyncManager.getInstance(app).onLinkChanged()
                        Log.i(TAG, "handoff: joined")
                    } catch (e: Exception) {
                        Log.e(TAG, "handoff failed: ${e.message}", e)
                    }
                }
            }

            "now" -> {
                Log.i(TAG, "now: ${SyncEngine(app).syncNow()}")
            }

            "status" -> {
                val store = SyncAccountStore(app)
                Log.i(
                    TAG,
                    "status: link=${store.link?.let {
                        "${it.serverUrl} account ${it.accountId} device ${it.deviceId}"
                    }} cursor=${store.cursor} seeded=${store.seeded} lastSync=${store.lastSyncAt} lastError=${store.lastError} waiting=${store.deferred.size}",
                )
            }

            "unlink" -> {
                accounts.leave()
                SyncManager.getInstance(app).onLinkChanged()
                Log.i(TAG, "unlink: done")
            }

            else -> {
                Log.w(TAG, "unknown cmd $cmd")
            }
        }
    }

    private companion object {
        const val ACTION = "org.njarasoa.fijerena.DEBUG_SYNC"
        const val TAG = "SyncDebug"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
