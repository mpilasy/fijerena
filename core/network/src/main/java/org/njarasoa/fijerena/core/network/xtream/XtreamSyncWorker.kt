package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class XtreamSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val providerRepo = ProviderRepository(applicationContext)
        val providers = providerRepo.getAllProvidersList()

        providers.filter { it.type == "XTREAM" }.forEach { provider ->
             val password = providerRepo.getPassword(provider.id)
             if (password != null) {
                 try {
                     val mediaProvider = MediaProviderFactory.create(provider, applicationContext, password)
                     if (mediaProvider is XtreamMediaProvider) {
                         if (!mediaProvider.isConnected()) {
                             mediaProvider.connect()
                         }

                         if (mediaProvider.isConnected()) {
                             mediaProvider.syncAll()
                         }
                     }
                 } catch (e: Exception) {
                     android.util.Log.e("XtreamSyncWorker", "Error during sync", e)
                 }
             }
        }

        return Result.success()
    }
}
