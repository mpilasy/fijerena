package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * Dependency Injection container for the app.
 *
 * Provides singletons for repositories to avoid redundant instantiation and
 * to ensure consistent state across the app.
 */
class AppContainer(private val context: Context) {

    /**
     * Singleton instance of ProviderRepository.
     * Manages all configured media providers and their settings.
     */
    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(context.applicationContext)
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
