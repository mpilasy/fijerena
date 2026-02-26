package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * Cache for MediaRepository instances per provider ID.
     */
    private val mediaRepositories = mutableMapOf<Long, MediaRepository>()
    private val mutex = Mutex()

    /**
     * Provides a MediaRepository instance for the specified provider ID.
     * If the ID is 0, the active provider is used.
     */
    suspend fun getMediaRepository(providerId: Long = 0L): MediaRepository {
        val resolvedId = if (providerId > 0L) {
            providerId
        } else {
            providerRepository.getActiveProvider()?.id ?: 0L
        }

        return mutex.withLock {
            mediaRepositories[resolvedId] ?: run {
                val settings = providerRepository.getProviderSettings(resolvedId)
                val repo = MediaRepository(context.applicationContext, resolvedId, settings)
                
                // Set the provider implementation
                val entity = if (providerId > 0L) {
                    providerRepository.getProviderById(providerId)
                } else {
                    providerRepository.getActiveProvider()
                }
                
                if (entity != null) {
                    val password = providerRepository.getPassword(entity.id) ?: ""
                    val provider = MediaProviderFactory.create(entity, context.applicationContext, password)
                    repo.setProvider(provider)
                }
                mediaRepositories[resolvedId] = repo
                repo
            }
        }
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
