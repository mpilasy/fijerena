package org.njarasoa.fijerena.core.network.local
import android.content.Context
import androidx.core.net.toUri
import org.njarasoa.fijerena.core.network.BaseM3uMediaProvider
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities

class LocalMediaProvider(
    override val providerId: Long,
    private val context: Context,
    private val config: LocalProviderConfig,
) : BaseM3uMediaProvider() {
    data class LocalProviderConfig(
        val rootPaths: List<String> = emptyList(),
        val m3uPath: String? = null,
    )

    override val capabilities =
        ProviderCapabilities(
            supportedContentTypes =
                buildSet {
                    add(ContentType.MOVIES)
                    if (config.m3uPath != null) add(ContentType.LIVE_TV)
                },
            supportsEpg = false,
            supportsSearch = true,
            supportsAuthentication = false,
            supportsProgressSync = false,
        )

    override suspend fun connect(): Result<Unit> =
        try {
            val cats = mutableListOf<MediaCategory>()
            val its = mutableListOf<MediaItem>()

            // Parse M3U if configured
            if (config.m3uPath != null) {
                val m3uUri = config.m3uPath.toUri()
                val m3uData =
                    context.contentResolver
                        .openInputStream(m3uUri)
                        ?.bufferedReader()
                        ?.use { M3uParser.processEntries(it) }

                if (m3uData != null) {
                    val (m3uCategories, m3uItems) = m3uData
                    cats.addAll(m3uCategories)
                    its.addAll(m3uItems)
                }
            }

            // Scan local directories
            for (rootPath in config.rootPaths) {
                val rootUri = rootPath.toUri()
                val (dirCategories, dirItems) = LocalFileScanner.scanDirectory(context, rootUri)
                cats.addAll(dirCategories)
                its.addAll(dirItems)
            }

            categories = cats
            items = its
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
