package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class EpgViewModelFactory(
    private val context: Context,
    private val categoryId: String,
    private val providerId: Long = 0L
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpgViewModel::class.java)) {
            val appContext = context.applicationContext
            val providerRepo = ProviderRepository(appContext)
            val entity = runBlocking {
                if (providerId > 0L) providerRepo.getProviderById(providerId)
                else providerRepo.getActiveProvider()
            }
            val resolvedId = entity?.id ?: providerId
            val mediaRepository = MediaRepository(appContext, resolvedId)
            if (entity != null) {
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                mediaRepository.setProvider(provider)
            }
            @Suppress("UNCHECKED_CAST")
            return EpgViewModel(mediaRepository, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
