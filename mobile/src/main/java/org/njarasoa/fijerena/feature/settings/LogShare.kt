package org.njarasoa.fijerena.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The cache folder that the FileProvider shares (res/xml/file_paths.xml). */
internal const val SHARED_CACHE_DIR = "shared"

private const val LOG_FILE_PREFIX = "fijerena-logs-"

/**
 * Writes [text] to a new file in the cache's shared folder and deletes the older log files there,
 * so only the last shared log stays on the device. Call it off the main thread.
 */
internal fun writeLogShareFile(
    cacheDir: File,
    text: String,
    nowMs: Long,
): File {
    val dir = File(cacheDir, SHARED_CACHE_DIR)
    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
    dir.listFiles { file -> file.name.startsWith(LOG_FILE_PREFIX) }?.forEach { it.delete() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs))
    val file = File(dir, "$LOG_FILE_PREFIX$stamp.txt")
    file.writeText(text)
    return file
}

/**
 * The chooser that shares the log file at [uri]. The text goes as a file, never in EXTRA_TEXT:
 * a large log in an extra is over the binder limit and the app crashed (TransactionTooLargeException).
 */
internal fun buildLogShareIntent(
    uri: Uri,
    subject: String,
    chooserTitle: String,
): Intent {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(subject, uri)
    return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** The content URI of [file] through the app's FileProvider. */
internal fun logShareUri(
    context: Context,
    file: File,
): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * Builds the log text with [buildText] and writes it to a file off the main thread, then opens the
 * share chooser. A failure shows a message ([AppNotices]), with the raw text when [showRawError].
 */
internal suspend fun shareLog(
    context: Context,
    showRawError: Boolean,
    buildText: () -> String,
) {
    val uri =
        try {
            withContext(Dispatchers.IO) {
                logShareUri(context, writeLogShareFile(context.cacheDir, buildText(), System.currentTimeMillis()))
            }
        } catch (e: IOException) {
            showShareFailure(context, R.string.settings_diagnostics_share_failed, e, showRawError)
            null
        } catch (e: IllegalArgumentException) {
            // The file is outside the FileProvider's paths.
            showShareFailure(context, R.string.settings_diagnostics_share_failed, e, showRawError)
            null
        }
    if (uri != null) {
        val chooser =
            buildLogShareIntent(
                uri = uri,
                subject = context.getString(R.string.settings_diagnostics_share_subject),
                chooserTitle = context.getString(R.string.settings_diagnostics_share),
            )
        try {
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            showShareFailure(context, R.string.settings_diagnostics_share_no_app, e, showRawError)
        }
    }
}

private fun showShareFailure(
    context: Context,
    messageRes: Int,
    error: Exception,
    showRawError: Boolean,
) {
    val message = context.getString(messageRes)
    val raw = error.message?.takeIf { showRawError }?.let { "[dev] ${Redact.text(it)}" }
    AppNotices.show(listOfNotNull(message, raw).joinToString("\n"))
}
