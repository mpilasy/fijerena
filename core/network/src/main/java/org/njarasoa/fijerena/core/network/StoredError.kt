package org.njarasoa.fijerena.core.network

import android.content.Context
import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.player.diagnostics.Redact

/**
 * What went wrong, as a stable code. A kept error stores this code, not message text, so the
 * message follows the app language when it shows ([StoredError]).
 */
enum class ErrorKind(
    @param:StringRes val messageRes: Int,
) {
    GENERIC(R.string.error_generic),
    INVALID_PLAYLIST(R.string.error_invalid_playlist),
    INVALID_SETTINGS_FILE(R.string.settings_export_error_invalid_format),
    UNAUTHORIZED(R.string.error_unauthorized),
    SAVED_LOGIN_LOST(R.string.error_saved_login_lost),
    NOT_FOUND(R.string.error_not_found),
    SERVER(R.string.error_server),
    NO_INTERNET(R.string.error_no_internet),
    TIMEOUT(R.string.error_timeout),
    SSL(R.string.error_ssl),
    NETWORK(R.string.error_network),
    GUIDE_LOGIN_MISSING(R.string.error_guide_login_missing),
    GUIDE_CAME_BACK_EMPTY(R.string.epg_error_came_back_empty),
}

/**
 * An error to keep in a database column or a preference: the [kind], whether it is a catalogue
 * failure ([catalogue]: "Catalogue update failed: …" around the message), and the masked raw
 * exception text ([raw]) for a Nerd.
 *
 * Store [encode], and show [message] with the screen's context: the message is then in the app
 * language in use, not in the language of the time the error occurred. A value from before this
 * format (message text) reads as [ErrorKind.GENERIC], with the old text as its raw text.
 */
data class StoredError(
    val kind: ErrorKind,
    val catalogue: Boolean = false,
    val raw: String? = null,
) {
    /** The value to store: "error:<KIND>", "error:catalogue/<KIND>", then the raw text on the next line. */
    fun encode(): String =
        buildString {
            append(PREFIX)
            if (catalogue) append(CATALOGUE)
            append(kind.name)
            raw?.takeIf { it.isNotBlank() }?.let {
                append('\n')
                append(Redact.text(it))
            }
        }

    /** The friendly message in [context]'s language, with the raw text below it when [includeRaw] is true. */
    fun message(
        context: Context,
        includeRaw: Boolean,
    ): String {
        val friendly = context.getString(kind.messageRes)
        val text = if (catalogue) context.getString(R.string.error_catalog_sync_failed, friendly) else friendly
        return if (includeRaw && !raw.isNullOrBlank()) "$text\n\n[dev] $raw" else text
    }

    companion object {
        private const val PREFIX = "error:"
        private const val CATALOGUE = "catalogue/"

        /** The kept form of [e]: its kind and its masked message. */
        fun of(
            e: Throwable,
            context: Context,
        ): StoredError = StoredError(errorKind(e, context), raw = e.message?.let(Redact::text))

        /** Reads a stored value. An unknown or old value (message text) is [ErrorKind.GENERIC], with that text as [raw]. */
        fun decode(stored: String): StoredError {
            val code = stored.substringBefore('\n')
            val raw = stored.substringAfter('\n', "").ifBlank { null }
            if (!code.startsWith(PREFIX)) return StoredError(ErrorKind.GENERIC, raw = stored.ifBlank { null })
            val body = code.removePrefix(PREFIX)
            val catalogue = body.startsWith(CATALOGUE)
            val kind = ErrorKind.entries.firstOrNull { it.name == body.removePrefix(CATALOGUE) }
            return if (kind == null) {
                StoredError(ErrorKind.GENERIC, raw = stored)
            } else {
                StoredError(kind, catalogue, raw)
            }
        }

        /** The message of a [stored] value in [context]'s language. */
        fun message(
            stored: String,
            context: Context,
            includeRaw: Boolean,
        ): String = decode(stored).message(context, includeRaw)
    }
}
