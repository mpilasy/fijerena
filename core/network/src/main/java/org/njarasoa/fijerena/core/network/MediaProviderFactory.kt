package org.njarasoa.fijerena.core.network

import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinMediaProvider
import org.njarasoa.fijerena.core.network.local.LocalMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.smb.SmbClient
import org.njarasoa.fijerena.core.network.smb.SmbMediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaProvider

/**
 * Resolves the correct [MediaProvider] implementation based on the provider entity type.
 *
 * Caches provider instances by ID to ensure the same authenticated provider is reused
 * across all screens, preventing session conflicts (especially for Jellyfin).
 */
object MediaProviderFactory {

    private val json = Json { ignoreUnknownKeys = true }

    // Cache of provider instances by provider ID
    // Prevents multiple auth sessions that can invalidate each other
    private val providerCache = mutableMapOf<Long, MediaProvider>()

    /**
     * Get or create a [MediaProvider] for the given [ProviderEntity].
     * Reuses cached instances to avoid creating duplicate auth sessions.
     *
     * @param entity The provider entity from Room database
     * @param context Application context
     * @param password The decrypted password from ProviderRepository
     */
    fun create(
        entity: ProviderEntity,
        context: Context,
        password: String
    ): MediaProvider {
        // Return cached provider if available
        providerCache[entity.id]?.let { return it }

        val provider = when (entity.type) {
            "XTREAM" -> createXtream(entity, context, password)
            "JELLYFIN" -> createJellyfin(entity, password, context)
            "SMB" -> createSmb(entity, password)
            "LOCAL" -> createLocal(entity, context)
            else -> createXtream(entity, context, password)
        }

        // Cache the provider instance
        providerCache[entity.id] = provider
        return provider
    }

    /**
     * Clear cached provider for a specific ID (e.g., when credentials change).
     */
    fun clearCache(providerId: Long) {
        providerCache.remove(providerId)
    }

    /**
     * Clear all cached providers (e.g., on logout or provider switch).
     */
    fun clearAllCaches() {
        providerCache.clear()
    }

    private fun createXtream(
        entity: ProviderEntity,
        context: Context,
        password: String
    ): MediaProvider {
        val accountManager = AccountManager(context.applicationContext)
        val xtreamRepository = XtreamRepository(
            accountManager,
            context.applicationContext,
            entity.id
        )
        return XtreamMediaProvider(entity.id, xtreamRepository)
    }

    private fun createJellyfin(
        entity: ProviderEntity,
        password: String,
        context: Context
    ): MediaProvider {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "fijerena-${entity.id}"

        return JellyfinMediaProvider(
            providerId = entity.id,
            serverUrl = entity.url.trimEnd('/'),
            username = entity.username,
            password = password,
            deviceId = deviceId
        )
    }

    private fun createSmb(entity: ProviderEntity, password: String): MediaProvider {
        val config = parseConfig(entity.config)
        val host = config["host"] ?: ""
        val share = config["share"] ?: ""
        val domain = config["domain"] ?: "WORKGROUP"

        val smbClient = SmbClient(
            host = host,
            shareName = share,
            domain = domain,
            username = entity.username.ifEmpty { null },
            password = password.ifEmpty { null }
        )
        return SmbMediaProvider(entity.id, smbClient)
    }

    private fun createLocal(entity: ProviderEntity, context: Context): MediaProvider {
        val config = parseConfig(entity.config)
        val rootPaths = config["rootPaths"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val m3uPath = config["m3uPath"]?.ifEmpty { null }

        return LocalMediaProvider(
            providerId = entity.id,
            context = context.applicationContext,
            config = LocalMediaProvider.LocalProviderConfig(
                rootPaths = rootPaths,
                m3uPath = m3uPath
            )
        )
    }

    private fun parseConfig(configStr: String): Map<String, String> {
        if (configStr.isBlank()) return emptyMap()
        return try {
            val jsonObj = json.parseToJsonElement(configStr).jsonObject
            jsonObj.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
