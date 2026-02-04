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
 */
object MediaProviderFactory {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a [MediaProvider] for the given [ProviderEntity].
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
        return when (entity.type) {
            "XTREAM" -> createXtream(entity, context, password)
            "JELLYFIN" -> createJellyfin(entity, password, context)
            "SMB" -> createSmb(entity, password)
            "LOCAL" -> createLocal(entity, context)
            else -> createXtream(entity, context, password)
        }
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
