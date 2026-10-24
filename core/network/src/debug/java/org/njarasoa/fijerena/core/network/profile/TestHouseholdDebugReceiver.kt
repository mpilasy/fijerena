package org.njarasoa.fijerena.core.network.profile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.ProviderSyncManager
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes
import kotlin.coroutines.cancellation.CancellationException

/**
 * Debug builds only: sets up the test household of the nightly TV walk on a fresh install, with no
 * UI (`scripts/seed-test-household.sh` sends it). Results go to logcat under `TestHousehold`: one
 * line `done`, `refused: …` or `failed: …` at the end. The receiver requires
 * `android.permission.DUMP`, which the adb shell holds and other apps don't.
 *
 *     adb shell am broadcast --include-stopped-packages -a org.njarasoa.fijerena.DEBUG_TEST_HOUSEHOLD \
 *         -p <app id> --es url http://10.0.2.2:8090 [--es username test] [--es password test]
 *
 * See [TestHousehold.seed] for what it adds; then it loads the source's catalogue and waits for it.
 */
class TestHouseholdDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val url = intent.getStringExtra("url")
        val username = intent.getStringExtra("username") ?: DEFAULT_LOGIN
        val password = intent.getStringExtra("password") ?: DEFAULT_LOGIN
        if (url == null) {
            Log.e(TAG, "failed: no --es url")
        } else {
            // The catalogue load takes longer than a broadcast may run: on its own, outside goAsync().
            scope.launch {
                try {
                    val providerId = TestHousehold.seed(app, url, username, password)
                    if (providerId == null) {
                        Log.w(TAG, "refused: the app has a source already (seed a fresh install only)")
                    } else {
                        Log.i(TAG, "source and profiles added, loading the catalogue")
                        val result = ProviderSyncManager.getInstance(app).startManualSync(providerId).await()
                        if (result is ProviderSyncManager.SyncResult.Failed) {
                            Log.e(TAG, "failed: catalogue load: ${result.message}")
                        } else {
                            Log.i(TAG, "done")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "failed: ${e.message}", e)
                }
            }
        }
    }

    private companion object {
        const val ACTION = "org.njarasoa.fijerena.DEBUG_TEST_HOUSEHOLD"
        const val TAG = "TestHousehold"
        const val DEFAULT_LOGIN = "test"
        val scope = AppScopes.create("TestHouseholdDebugReceiver", Dispatchers.IO)
    }
}

/** The test household of the nightly TV walk. Debug builds only. */
internal object TestHousehold {
    const val SOURCE_NAME = "Fake Xtream"
    const val ADMIN = "Admin"
    const val GUEST = "Guest"
    const val KID = "Kid"

    /** Profiles sort by creation time, to the millisecond: a gap keeps Guest before Kid. */
    private const val CREATED_AT_GAP_MS = 10L

    /**
     * Adds one Xtream source at [url] for the profile in use, renames that profile [ADMIN] and makes
     * it Expert, then adds [GUEST] (a grown-up, not Expert) and [KID] (a child profile), which get
     * the source as a profile added in Settings does. Returns the source's id, or null (and changes
     * nothing) when the app has a source already: this is for a fresh install, never a device with data.
     */
    suspend fun seed(
        context: Context,
        url: String,
        username: String,
        password: String,
    ): Long? =
        withContext(Dispatchers.IO) {
            val providers = ProviderRepository(context)
            if (providers.getProviderCount() > 0) {
                null
            } else {
                val settings = AppSettings(context)
                val profiles = ProfileRepository(context)
                val admin = settings.activeProfileId
                profiles.updateProfile(admin, ADMIN, colorIndex = 0)
                settings.setDevMode(admin, true)
                val providerId = providers.addProvider(SOURCE_NAME, url, username, password)
                profiles.addProfile(GUEST, colorIndex = 1)
                delay(CREATED_AT_GAP_MS)
                profiles.addProfile(KID, colorIndex = 2, child = true)
                providerId
            }
        }
}
