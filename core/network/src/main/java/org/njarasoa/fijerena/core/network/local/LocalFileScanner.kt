package org.njarasoa.fijerena.core.network.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.njarasoa.fijerena.core.player.domain.MediaCategory
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.io.File

object LocalFileScanner {
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

    private val PROJECTION =
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

    fun scanDirectory(
        context: Context,
        rootUri: Uri,
    ): Pair<List<MediaCategory>, List<MediaItem>> {
        if (ContentResolver.SCHEME_FILE == rootUri.scheme) {
            return scanFileDirectory(rootUri)
        }

        // Try optimized ContentResolver scan for Tree URIs (SAF)
        try {
            if (DocumentsContract.isDocumentUri(context, rootUri)) {
                // If it is a document URI, it might be a tree URI.
                // However, isDocumentUri returns true for single documents too.
                // We assume it's a tree root if passed here.
                return scanContentDirectory(context, rootUri)
            }
            // If checking isDocumentUri fails or returns false (e.g. some providers),
            // we might still want to try scanContentDirectory if it looks like a tree URI,
            // or fall back to DocumentFile.
            // Let's rely on try-catch around scanContentDirectory.
            return scanContentDirectory(context, rootUri)
        } catch (e: Exception) {
            // Check if we can resolve the content URI to a direct file path
            val resolvedFile = tryResolveToFile(context, rootUri)
            if (resolvedFile != null && resolvedFile.isDirectory) {
                return scanFileDirectory(Uri.fromFile(resolvedFile))
            }

            // Fallback to DocumentFile implementation which handles various edge cases
            return scanDocumentFileDirectory(context, rootUri)
        }
    }

    private fun tryResolveToFile(
        context: Context,
        uri: Uri,
    ): File? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it) }
        }
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                // _data column is deprecated in API 29+ but still widely used to get absolute paths
                // for local media from older content providers
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val path = cursor.getString(0)
                        if (path != null) {
                            val file = File(path)
                            if (file.exists()) return file
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back
            }
        }
        return null
    }

    // --- File Scheme Optimization ---

    private fun scanFileDirectory(rootUri: Uri): Pair<List<MediaCategory>, List<MediaItem>> {
        val rootPath = rootUri.path ?: return Pair(emptyList(), emptyList())
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) return Pair(emptyList(), emptyList())

        val categories = mutableListOf<MediaCategory>()
        val items = mutableListOf<MediaItem>()
        val rootCategoryId = "local_dir_root"
        var hasRootFiles = false

        rootFile.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val catId = "local_dir_${categories.size}"
                categories.add(MediaCategory(id = catId, name = file.name))
                scanFileSubdirectory(file, catId, items)
            } else if (file.isFile && isVideoFile(file.name)) {
                if (!hasRootFiles) {
                    categories.add(0, MediaCategory(id = rootCategoryId, name = "Uncategorized"))
                    hasRootFiles = true
                }
                items.add(createFileMediaItem(file, rootCategoryId, items.size))
            }
        }
        return Pair(categories, items)
    }

    private fun scanFileSubdirectory(
        directory: File,
        categoryId: String,
        items: MutableList<MediaItem>,
    ) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isVideoFile(file.name)) {
                items.add(createFileMediaItem(file, categoryId, items.size))
            } else if (file.isDirectory) {
                scanFileSubdirectory(file, categoryId, items)
            }
        }
    }

    private fun createFileMediaItem(
        file: File,
        categoryId: String,
        index: Int,
    ): MediaItem =
        MediaItem(
            id = "local_file_${index}_${file.name.hashCode()}",
            name = file.name.substringBeforeLast('.'),
            mediaType = MediaType.VIDEO_FILE,
            categoryId = categoryId,
            streamUri = Uri.fromFile(file).toString(),
        )

    // --- Content Resolver Optimization (SAF) ---

    private data class ChildDoc(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val isVideo: Boolean,
    )

    private fun scanContentDirectory(
        context: Context,
        rootUri: Uri,
    ): Pair<List<MediaCategory>, List<MediaItem>> {
        val categories = mutableListOf<MediaCategory>()
        val items = mutableListOf<MediaItem>()
        val rootCategoryId = "local_dir_root"
        var hasRootFiles = false

        // This throws IllegalArgumentException if rootUri is not a valid Tree URI
        val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
        val children = queryChildren(context, rootUri, rootDocId)

        children.forEach { child ->
            if (child.isDirectory) {
                val catId = "local_dir_${categories.size}"
                categories.add(MediaCategory(id = catId, name = child.name))
                scanContentSubdirectory(context, rootUri, child.documentId, catId, items)
            } else if (child.isVideo) {
                if (!hasRootFiles) {
                    categories.add(0, MediaCategory(id = rootCategoryId, name = "Uncategorized"))
                    hasRootFiles = true
                }
                items.add(createContentMediaItem(rootUri, child, rootCategoryId, items.size))
            }
        }
        return Pair(categories, items)
    }

    private fun scanContentSubdirectory(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        categoryId: String,
        items: MutableList<MediaItem>,
    ) {
        val children = queryChildren(context, treeUri, parentDocId)
        children.forEach { child ->
            if (child.isVideo) {
                items.add(createContentMediaItem(treeUri, child, categoryId, items.size))
            } else if (child.isDirectory) {
                scanContentSubdirectory(context, treeUri, child.documentId, categoryId, items)
            }
        }
    }

    private fun queryChildren(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
    ): List<ChildDoc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val result = mutableListOf<ChildDoc>()

        try {
            context.contentResolver
                .query(
                    childrenUri,
                    PROJECTION,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) else "Unknown"
                        val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else ""

                        val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        val isVideo = !isDir && isVideoFile(name)

                        result.add(ChildDoc(docId, name, isDir, isVideo))
                    }
                }
        } catch (e: Exception) {
            // Logging can be added here if needed, but for now we swallow to match previous behavior
            // or let the caller catch it (but we return empty list for partial failure)
        }
        return result
    }

    private fun createContentMediaItem(
        treeUri: Uri,
        child: ChildDoc,
        categoryId: String,
        index: Int,
    ): MediaItem {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
        return MediaItem(
            id = "local_saf_${index}_${child.documentId.hashCode()}",
            name = child.name.substringBeforeLast('.'),
            mediaType = MediaType.VIDEO_FILE,
            categoryId = categoryId,
            streamUri = uri.toString(),
        )
    }

    // --- Legacy DocumentFile Fallback ---

    private fun scanDocumentFileDirectory(
        context: Context,
        rootUri: Uri,
    ): Pair<List<MediaCategory>, List<MediaItem>> {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return Pair(emptyList(), emptyList())
        val categories = mutableListOf<MediaCategory>()
        val items = mutableListOf<MediaItem>()

        val rootCategoryId = "local_dir_root"
        var hasRootFiles = false

        rootDoc.listFiles().forEach { file ->
            if (file.isDirectory) {
                val catId = "local_dir_${categories.size}"
                val dirName = file.name ?: "Unknown Folder"
                categories.add(
                    MediaCategory(
                        id = catId,
                        name = dirName,
                    ),
                )
                scanDocumentFileSubdirectory(context, file, catId, items)
            } else if (file.isFile && isVideoFile(file.name)) {
                if (!hasRootFiles) {
                    categories.add(0, MediaCategory(id = rootCategoryId, name = "Uncategorized"))
                    hasRootFiles = true
                }
                items.add(documentFileToMediaItem(context, file, rootCategoryId, items.size))
            }
        }

        return Pair(categories, items)
    }

    private fun scanDocumentFileSubdirectory(
        context: Context,
        directory: DocumentFile,
        categoryId: String,
        items: MutableList<MediaItem>,
    ) {
        directory.listFiles().forEach { file ->
            if (file.isFile && isVideoFile(file.name)) {
                items.add(documentFileToMediaItem(context, file, categoryId, items.size))
            }
            // Flatten nested directories into the same category
            if (file.isDirectory) {
                scanDocumentFileSubdirectory(context, file, categoryId, items)
            }
        }
    }

    private fun documentFileToMediaItem(
        context: Context,
        file: DocumentFile,
        categoryId: String,
        index: Int,
    ): MediaItem =
        MediaItem(
            id = "local_file_$index",
            name = file.name?.substringBeforeLast('.') ?: "Unknown",
            mediaType = MediaType.VIDEO_FILE,
            categoryId = categoryId,
            streamUri = file.uri.toString(),
        )

    private fun isVideoFile(name: String?): Boolean {
        if (name == null) return false
        // Zero-allocation extension matching: Avoids substring and lowercase allocations
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
