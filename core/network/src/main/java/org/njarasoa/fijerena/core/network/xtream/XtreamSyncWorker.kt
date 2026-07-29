package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class XtreamSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val providerRepo = ProviderRepository(applicationContext)
        val providers = providerRepo.getAllProvidersList()

        var anyTransientFailure = false
        providers.filter { it.type == "XTREAM" }.forEach { provider ->
            val password = providerRepo.getPassword(provider.id)
            if (password != null) {
                val startTime = System.currentTimeMillis()
                val outcome = ProviderSyncRunner.syncProvider(applicationContext, provider, password)
                if (outcome is ProviderSyncRunner.Outcome.Transient) {
                    anyTransientFailure = true
                }
                val endTime = System.currentTimeMillis()
                providerRepo.updateSyncStats(provider.id, endTime, endTime - startTime, outcome.errorOrNull())
            }
        }

        // Only a transient failure is worth a WorkManager-backed retry; permanent failures
        // (bad credentials) won't fix themselves, and their error is already recorded.
        return if (anyTransientFailure && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val MAX_RETRIES = 5
    }
}
