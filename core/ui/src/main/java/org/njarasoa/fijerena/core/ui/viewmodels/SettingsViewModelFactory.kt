package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class SettingsViewModelFactory(
    context: Context,
    private val contentType: String = "ALL",
    // EPG is provider-scoped: the EPG management screen is opened for one specific provider.
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(EpgManagementViewModel::class.java) -> {
                EpgManagementViewModel(appContext, providerId) as T
            }
            modelClass.isAssignableFrom(EpgBrowserViewModel::class.java) -> {
                val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(appContext)
                EpgBrowserViewModel(appContext, container.providerRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    context = appContext,
                    appSettings = AppSettings(appContext),
                    providerRepo = ProviderRepository(appContext),
                    exportManager = SettingsExportManager(appContext),
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(appContext, contentType) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
