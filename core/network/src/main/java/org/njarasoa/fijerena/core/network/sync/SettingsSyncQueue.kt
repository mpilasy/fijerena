package org.njarasoa.fijerena.core.network.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.player.diagnostics.AppScopes

/**
 * Queues changes to values kept outside `providers.db` — passwords and Jellyfin logins, category
 * filters, synced settings — which no trigger can see. Not in the same transaction as the change
 * (SharedPreferences has none), so a crash in between loses the entry; the next change to the same
 * value queues it again. Fire-and-forget on its own IO scope: callers include plain property
 * setters on the main thread. See `docs/plans/archive/20260929_live-sync-plan.md` → Flow.
 */
object SettingsSyncQueue {
    private val scope = AppScopes.create("SettingsSyncQueue", Dispatchers.IO)

    /** The provider record — its password lives in the encrypted prefs, not the row. */
    fun provider(
        context: Context,
        providerId: Long,
    ) = byProvider(context, SyncKind.PROVIDER, SyncKind.SHARED, providerId)

    fun providerLogin(
        context: Context,
        providerId: Long,
        profileId: String,
    ) = byProvider(context, SyncKind.PROVIDER_LOGIN, profileId, providerId)

    fun categoryFilters(
        context: Context,
        providerId: Long,
        profileId: String,
    ) = byProvider(context, SyncKind.CATEGORY_FILTERS, profileId, providerId)

    fun setting(
        context: Context,
        key: String,
        profileId: String = SyncKind.SHARED,
    ) = launch(context) { it.queue(SyncKind.SETTING, profileId, key) }

    private fun byProvider(
        context: Context,
        kind: String,
        profileId: String,
        providerId: Long,
    ) = launch(context) { dao -> dao.providerKey(providerId)?.let { dao.queue(kind, profileId, it) } }

    private fun launch(
        context: Context,
        block: suspend (org.njarasoa.fijerena.core.network.provider.SettingsSyncDao) -> Unit,
    ) {
        scope.launch {
            try {
                block(SettingsDatabase.getInstance(context).settingsSyncDao())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SettingsSyncQueue", "Couldn't queue a change for sync", e)
            }
        }
    }
}
