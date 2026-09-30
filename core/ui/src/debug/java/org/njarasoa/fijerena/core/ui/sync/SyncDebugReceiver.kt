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
 * - `pairing` — logs a pairing code for another device
 * - `pair --es url <server> --es code <code> [--es name <device name>]` — join an account
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
            "pairing" -> Log.i(TAG, "pairing code: ${accounts.createPairingCode().code}")
            "pair" -> {
                accounts.pair(intent.getStringExtra("url")!!, intent.getStringExtra("code")!!, name)
                SyncManager.getInstance(app).onLinkChanged()
                Log.i(TAG, "pair: linked")
            }
            "now" -> Log.i(TAG, "now: ${SyncEngine(app).syncNow()}")
            "status" -> {
                val store = SyncAccountStore(app)
                Log.i(TAG, "status: link=${store.link?.let { "${it.serverUrl} account ${it.accountId} device ${it.deviceId}" }} cursor=${store.cursor} seeded=${store.seeded} lastSync=${store.lastSyncAt} lastError=${store.lastError} waiting=${store.deferred.size}")
            }
            "unlink" -> {
                accounts.unlink()
                SyncManager.getInstance(app).onLinkChanged()
                Log.i(TAG, "unlink: done")
            }
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
    }

    private companion object {
        const val ACTION = "org.njarasoa.fijerena.DEBUG_SYNC"
        const val TAG = "SyncDebug"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
