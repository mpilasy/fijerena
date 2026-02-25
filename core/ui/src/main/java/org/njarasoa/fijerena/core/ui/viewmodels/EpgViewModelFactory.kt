package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
            @Suppress("UNCHECKED_CAST")
            return EpgViewModel(appContext, providerRepo, categoryId, providerId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
