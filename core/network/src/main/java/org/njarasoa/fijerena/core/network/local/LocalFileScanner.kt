package org.njarasoa.fijerena.core.network.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType

object LocalFileScanner {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "m4v",
        "ts", "mpg", "mpeg", "webm", "3gp", "ogv"
    )

    fun scanDirectory(context: Context, rootUri: Uri): Pair<List<MediaCategory>, List<MediaItem>> {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return Pair(emptyList(), emptyList())
        val categories = mutableListOf<MediaCategory>()
        val items = mutableListOf<MediaItem>()

        // Root-level video files go into an "Uncategorized" category
        val rootCategoryId = "local_dir_root"
        var hasRootFiles = false

        rootDoc.listFiles().forEach { file ->
            if (file.isDirectory) {
                val catId = "local_dir_${categories.size}"
                val dirName = file.name ?: "Unknown Folder"
                categories.add(
                    MediaCategory(
                        id = catId,
                        name = dirName
                    )
                )
                scanSubdirectory(context, file, catId, items)
            } else if (file.isFile && isVideoFile(file.name)) {
                if (!hasRootFiles) {
                    categories.add(0, MediaCategory(id = rootCategoryId, name = "Uncategorized"))
                    hasRootFiles = true
                }
                items.add(fileToMediaItem(context, file, rootCategoryId, items.size))
            }
        }

        return Pair(categories, items)
    }

    private fun scanSubdirectory(
        context: Context,
        directory: DocumentFile,
        categoryId: String,
        items: MutableList<MediaItem>
    ) {
        directory.listFiles().forEach { file ->
            if (file.isFile && isVideoFile(file.name)) {
                items.add(fileToMediaItem(context, file, categoryId, items.size))
            }
            // Flatten nested directories into the same category
            if (file.isDirectory) {
                scanSubdirectory(context, file, categoryId, items)
            }
        }
    }

    private fun fileToMediaItem(
        context: Context,
        file: DocumentFile,
        categoryId: String,
        index: Int
    ): MediaItem {
        return MediaItem(
            id = "local_file_$index",
            name = file.name?.substringBeforeLast('.') ?: "Unknown",
            mediaType = MediaType.VIDEO_FILE,
            categoryId = categoryId,
            streamUri = file.uri.toString()
        )
    }

    private fun isVideoFile(name: String?): Boolean {
        if (name == null) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
