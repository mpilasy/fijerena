package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.SettingsExportManager
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

class SettingsViewModelFactory(
    private val context: Context,
    private val contentType: String = "ALL",
    // EPG is provider-scoped: the EPG management screen is opened for one specific provider.
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(EpgManagementViewModel::class.java) -> {
                EpgManagementViewModel(context.applicationContext, providerId) as T
            }
            modelClass.isAssignableFrom(EpgBrowserViewModel::class.java) -> {
                val container = org.njarasoa.fijerena.core.ui.di.AppContainer.getInstance(context.applicationContext)
                EpgBrowserViewModel(context.applicationContext, container.providerRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                val appCtx = context.applicationContext
                SettingsViewModel(
                    context = appCtx,
                    appSettings = AppSettings(appCtx),
                    providerRepo = ProviderRepository(appCtx),
                    exportManager = SettingsExportManager(appCtx),
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(context.applicationContext, contentType) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
