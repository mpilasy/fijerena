package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val appSettings = AppSettings(context.applicationContext)
            val providerRepo = ProviderRepository(context.applicationContext)
            val exportManager = SettingsExportManager(context.applicationContext)
            return SettingsViewModel(
                context = context.applicationContext,
                appSettings = appSettings,
                providerRepo = providerRepo,
                exportManager = exportManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
