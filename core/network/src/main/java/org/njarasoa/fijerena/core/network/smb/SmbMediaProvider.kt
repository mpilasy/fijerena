package org.njarasoa.fijerena.core.network.smb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MovieDetail
import org.njarasoa.fijerena.core.player.domain.PlayableStream
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.player.domain.SeriesDetail

class SmbMediaProvider(
    override val providerId: Long,
    private val smbClient: SmbClient,
) : MediaProvider {
    private val TAG = "SmbMediaProvider"

    override val capabilities =
        ProviderCapabilities(
            supportedContentTypes = setOf(ContentType.MOVIES),
            supportsEpg = false,
            supportsSearch = true,
            supportsAuthentication = true,
            supportsProgressSync = false,
        )

    private val VIDEO_EXTENSIONS =
        arrayOf(
            "mp4",
            "mkv",
            "avi",
            "mov",
            "wmv",
            "flv",
            "m4v",
            "ts",
            "mpg",
            "mpeg",
            "webm",
            "3gp",
            "ogv",
        )

    private var categories = emptyList<MediaCategory>()
    private var items = emptyList<MediaItem>()

    override suspend fun connect(): Result<Unit> {
        val result = smbClient.connect()
        if (result.isSuccess) {
            scanShare()
        }
        return result
    }

    override suspend fun disconnect() {
        smbClient.disconnect()
        categories = emptyList()
        items = emptyList()
    }

    override fun isConnected(): Boolean = smbClient.isConnected()

    override suspend fun getCategories(contentType: String): Result<List<MediaCategory>> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull()!!)
        }
        return Result.success(categories)
    }

    override suspend fun getItems(
        categoryId: String,
        contentType: String,
    ): Result<List<MediaItem>> {
        if (!isConnected()) {
            val connectResult = connect()
            if (connectResult.isFailure) return Result.failure(connectResult.exceptionOrNull()!!)
        }
        return Result.success(items.filter { it.categoryId == categoryId })
    }

    override suspend fun getSeriesDetail(seriesId: String): Result<SeriesDetail> =
        Result.failure(UnsupportedOperationException("SMB does not support series"))

    override suspend fun getMovieDetail(movieId: String): Result<MovieDetail> {
        val item =
            items.find { it.id == movieId }
                ?: return Result.failure(NoSuchElementException("Item not found: $movieId"))
        return Result.success(
            MovieDetail(id = item.id, name = item.name, coverUrl = item.thumbnailUrl),
        )
    }

    override suspend fun resolvePlayableStream(
        itemId: String,
        contentType: String,
        episodeId: String?,
        extension: String?,
    ): Result<PlayableStream> {
        val item =
            items.find { it.id == itemId }
                ?: return Result.failure(NoSuchElementException("Item not found: $itemId"))
        val smbPath =
            item.providerData["smbPath"]
                ?: return Result.failure(IllegalStateException("No SMB path for item: $itemId"))
        return Result.success(
            PlayableStream(
                uri = "smb://$smbPath",
                isLive = false,
                title = item.name,
            ),
        )
    }

    private suspend fun scanShare() =
        withContext(Dispatchers.IO) {
            val catList = mutableListOf<MediaCategory>()
            val itemList = mutableListOf<MediaItem>()
            var hasRootFiles = false
            val rootCategoryId = "smb_root"

            try {
                val rootEntries = smbClient.listDirectory("")
                for (entry in rootEntries) {
                    val fileName = entry.fileName
                    if (smbClient.isDirectory(fileName)) {
                        val catId = "smb_dir_${catList.size}"
                        catList.add(MediaCategory(id = catId, name = fileName))
                        scanDirectory(fileName, catId, itemList)
                    } else if (isVideoFile(fileName)) {
                        if (!hasRootFiles) {
                            catList.add(0, MediaCategory(id = rootCategoryId, name = "Root"))
                            hasRootFiles = true
                        }
                        itemList.add(
                            MediaItem(
                                id = "smb_file_${itemList.size}",
                                name = fileName.substringBeforeLast('.'),
                                mediaType = MediaType.VIDEO_FILE,
                                categoryId = rootCategoryId,
                                providerData = mapOf("smbPath" to fileName),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during root share scan", e)
                // Partial scan is acceptable
            }

            categories = catList
            items = itemList
        }

    private fun scanDirectory(
        path: String,
        categoryId: String,
        itemList: MutableList<MediaItem>,
    ) {
        try {
            val entries = smbClient.listDirectory(path)
            for (entry in entries) {
                val fileName = entry.fileName
                val fullPath = "$path/$fileName"
                if (smbClient.isDirectory(fullPath)) {
                    scanDirectory(fullPath, categoryId, itemList)
                } else if (isVideoFile(fileName)) {
                    itemList.add(
                        MediaItem(
                            id = "smb_file_${itemList.size}",
                            name = fileName.substringBeforeLast('.'),
                            mediaType = MediaType.VIDEO_FILE,
                            categoryId = categoryId,
                            providerData = mapOf("smbPath" to fullPath),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory: $path", e)
            // Skip inaccessible directories
        }
    }

    // ⚡ Bolt: Zero-allocation file extension matching to avoid substring and lowercase allocations.
    private fun isVideoFile(name: String): Boolean {
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex == -1 || dotIndex == name.length - 1) return false

        val extLength = name.length - dotIndex - 1
        for (i in VIDEO_EXTENSIONS.indices) {
            val ext = VIDEO_EXTENSIONS[i]
            if (ext.length == extLength && name.regionMatches(dotIndex + 1, ext, 0, extLength, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
