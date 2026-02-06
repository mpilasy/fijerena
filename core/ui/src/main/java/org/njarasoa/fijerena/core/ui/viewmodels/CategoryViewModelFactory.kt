package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.ProviderSettings

class CategoryViewModelFactory(
    private val context: Context,
    private val contentType: String,
    private val providerId: Long = 0L,
    private val initialCategoryId: String? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            val appContext = context.applicationContext
            val providerRepo = ProviderRepository(appContext)
            val entity = runBlocking {
                if (providerId > 0L) providerRepo.getProviderById(providerId)
                else providerRepo.getActiveProvider()
            }
            val resolvedId = entity?.id ?: providerId
            val settings = runBlocking { providerRepo.getProviderSettings(resolvedId) }
            val mediaRepository = MediaRepository(appContext, resolvedId, settings)
            if (entity != null) {
                val password = providerRepo.getPassword(entity.id) ?: ""
                val provider = MediaProviderFactory.create(entity, appContext, password)
                mediaRepository.setProvider(provider)
            }
            return CategoryViewModel(mediaRepository, contentType, initialCategoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
