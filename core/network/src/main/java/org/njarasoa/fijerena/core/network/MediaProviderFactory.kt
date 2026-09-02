package org.njarasoa.fijerena.core.network

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinMediaProvider
import org.njarasoa.fijerena.core.network.local.LocalMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderSettings
import org.njarasoa.fijerena.core.network.remote.RemoteM3uMediaProvider
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
        password: String,
    ): MediaProvider {
        // Return cached provider if available
        providerCache[entity.id]?.let { return it }

        val provider =
            when (entity.type) {
                "XTREAM" -> createXtream(entity, context, password)
                "JELLYFIN" -> createJellyfin(entity, password, context)
                "SMB" -> createSmb(entity, password)
                "LOCAL" -> createLocal(entity, context)
                "REMOTE_M3U" -> createRemoteM3u(entity, context)
                else -> createXtream(entity, context, password)
            }

        // Cache the provider instance
        providerCache[entity.id] = provider
        return provider
    }

    /**
     * Whether a provider carries live TV channels, and can therefore have an XMLTV EPG
     * attached to it. EPG is a live-TV concept: Jellyfin and SMB serve on-demand media only,
     * and a local folder only has channels when an M3U playlist is configured.
     *
     * Derived from type + config alone - no credentials, no network, no provider instance - so
     * it is safe to call for every row of a provider list. Mirrors the `supportedContentTypes`
     * declared by each provider's `capabilities`; keep it in sync with [create] above.
     *
     * Note this is deliberately NOT `capabilities.supportsEpg`, which means *native* EPG
     * (Xtream's get_short_epg). A REMOTE_M3U provider has no native EPG but is exactly the
     * case that needs an external XMLTV source.
     */
    fun hasLiveTv(entity: ProviderEntity): Boolean =
        when (entity.type) {
            "XTREAM", "REMOTE_M3U" -> true
            "LOCAL" -> parseConfig(entity.config)["m3uPath"]?.isNotEmpty() == true
            "JELLYFIN", "SMB" -> false
            else -> true // unknown types fall back to Xtream in create()
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

    /**
     * Tells every cached provider to drop its in-memory detail/search caches, without evicting
     * the provider instances themselves (that would force a re-auth). Called on system memory
     * pressure — see [org.njarasoa.fijerena.core.ui.FijerenaApplication.onTrimMemory].
     */
    fun trimMemory() {
        providerCache.values.forEach { it.trimMemory() }
    }

    private fun createXtream(
        entity: ProviderEntity,
        context: Context,
        password: String,
    ): MediaProvider {
        val accountManager = AccountManager(context.applicationContext)
        val providerSettings = parseProviderSettings(entity.providerSettings)

        // Store credentials so XtreamRepository.restoreSession() can find them
        accountManager.storeBasicCredentials(entity.url, entity.username, password)

        val xtreamRepository =
            XtreamRepository(
                accountManager,
                context.applicationContext,
                entity.id,
                providerSettings,
            )
        return XtreamMediaProvider(entity.id, xtreamRepository)
    }

    private fun createJellyfin(
        entity: ProviderEntity,
        password: String,
        context: Context,
    ): MediaProvider {
        val deviceId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ) ?: "fijerena-${entity.id}"

        val prefs = getJellyfinSessionPrefs(context, entity.id)
        val savedToken = prefs?.getString("jellyfin_token", null)
        val savedUserId = prefs?.getString("jellyfin_user_id", null)

        return JellyfinMediaProvider(
            providerId = entity.id,
            serverUrl = entity.url.trimEnd('/'),
            username = entity.username,
            password = password,
            deviceId = deviceId,
            savedToken = savedToken,
            savedUserId = savedUserId,
            onSessionSaved = { token, userId ->
                prefs
                    ?.edit()
                    ?.putString("jellyfin_token", token)
                    ?.putString("jellyfin_user_id", userId)
                    ?.apply()
            },
            onSessionCleared = {
                prefs
                    ?.edit()
                    ?.remove("jellyfin_token")
                    ?.remove("jellyfin_user_id")
                    ?.apply()
            },
        )
    }

    // Jetpack Security Crypto is deprecated as of its first stable release (1.1.0). Still the
    // credential store; replacing it is tracked in docs/plans/secret-store-migration-plan.md.
    @Suppress("DEPRECATION")
    private fun getJellyfinSessionPrefs(
        context: Context,
        providerId: Long,
    ): android.content.SharedPreferences? {
        val fileName = "provider_creds_$providerId"
        val prefs =
            try {
                val masterKey =
                    MasterKey
                        .Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.deleteSharedPreferences(fileName)
                null
            }
        return prefs
    }

    private fun createSmb(
        entity: ProviderEntity,
        password: String,
    ): MediaProvider {
        val config = parseConfig(entity.config)
        val host = config["host"] ?: ""
        val share = config["share"] ?: ""
        val domain = config["domain"] ?: "WORKGROUP"

        val smbClient =
            SmbClient(
                host = host,
                shareName = share,
                domain = domain,
                username = entity.username.ifEmpty { null },
                password = password.ifEmpty { null },
            )
        return SmbMediaProvider(entity.id, smbClient)
    }

    private fun createLocal(
        entity: ProviderEntity,
        context: Context,
    ): MediaProvider {
        val config = parseConfig(entity.config)
        val rootPaths = config["rootPaths"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val m3uPath = config["m3uPath"]?.ifEmpty { null }

        return LocalMediaProvider(
            providerId = entity.id,
            context = context.applicationContext,
            config =
                LocalMediaProvider.LocalProviderConfig(
                    rootPaths = rootPaths,
                    m3uPath = m3uPath,
                ),
        )
    }

    private fun createRemoteM3u(
        entity: ProviderEntity,
        context: Context,
    ): MediaProvider =
        RemoteM3uMediaProvider(
            providerId = entity.id,
            m3uUrl = entity.url,
            context = context.applicationContext,
        )

    private fun parseConfig(configStr: String): Map<String, String> {
        if (configStr.isBlank()) return emptyMap()
        return try {
            val jsonObj = json.parseToJsonElement(configStr).jsonObject
            jsonObj.mapValues { it.value.jsonPrimitive.content }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Parse provider settings from JSON string.
     * Returns default settings if parsing fails.
     */
    private fun parseProviderSettings(settingsJson: String): ProviderSettings {
        if (settingsJson.isBlank() || settingsJson == "{}") return ProviderSettings.DEFAULT
        return try {
            json.decodeFromString<ProviderSettings>(settingsJson)
        } catch (_: Exception) {
            ProviderSettings.DEFAULT
        }
    }
}
