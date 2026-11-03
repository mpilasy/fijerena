package org.njarasoa.fijerena.core.network

import android.content.Context
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinLoginRejectedException
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Map an exception to a short, user-facing message. Internal exception text
 * (Room internals, DNS/host details, HTTP stack fragments) must never reach the UI
 * on its own — this keeps "silly alerts" like Room's "Cannot access database on the
 * main thread…" from being shown to users as if the operation had really failed.
 *
 * Developer Mode callers append the raw text via the [includeRaw] overload.
 */
fun friendlyErrorMessage(
    e: Throwable,
    context: Context,
): String = context.getString(errorKind(e, context).messageRes)

/** The [ErrorKind] of [e]: what [friendlyErrorMessage] says, as a code to keep ([StoredError]). */
fun errorKind(
    e: Throwable,
    context: Context,
): ErrorKind {
    // A network failure inside another exception says what it is: a list read cut short by a
    // timeout comes up as a JSON error, with the timeout among its causes.
    val cause = e.networkCause()
    if (cause != null && cause !== e) return errorKind(cause, context)
    return when {
        // OkHttp's own message when a client's Dispatcher.executorService has already been
        // shut down (e.g. closed while still in use) and something tries to submit one more
        // call through it. It's an InterruptedIOException, so it would otherwise fall into the
        // generic network-error bucket below — but it has nothing to do with the user's
        // connection, and telling them to "check your connection" for an internal lifecycle bug
        // sends them chasing the wrong thing. No connectivity check would ever fix this.
        e.message?.contains("executor rejected", ignoreCase = true) == true -> {
            ErrorKind.GENERIC
        }

        e is InvalidPlaylistException -> {
            ErrorKind.INVALID_PLAYLIST
        }

        e is InvalidSettingsFileException -> {
            ErrorKind.INVALID_SETTINGS_FILE
        }

        e is HttpStatusException -> {
            httpStatusKind(e.code, context)
        }

        e is ResponseException -> {
            httpStatusKind(e.response.status.value, context)
        }

        e is UnknownHostException -> {
            ErrorKind.NO_INTERNET
        }

        // HttpRequestTimeoutException: a call that outran its overall deadline (R-14), e.g. a
        // server trickling bytes. It's an IOException, so it would otherwise read as "network error".
        e is SocketTimeoutException || e is HttpRequestTimeoutException -> {
            ErrorKind.TIMEOUT
        }

        e is SSLException -> {
            ErrorKind.SSL
        }

        e is IOException -> {
            ErrorKind.NETWORK
        }

        else -> {
            val msg = e.message.orEmpty()
            val isAuthFailure =
                e is JellyfinLoginRejectedException ||
                    msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("Unauthorized", ignoreCase = true) ||
                    // XtreamSessionManager's own wording: user_info.auth != 1, or no usable password.
                    msg.contains("Invalid credentials", ignoreCase = true) ||
                    msg.contains("credentials are invalid", ignoreCase = true) ||
                    msg.contains("Password not stored", ignoreCase = true)
            if (isAuthFailure) authFailureKind(context) else ErrorKind.GENERIC
        }
    }
}

/**
 * The first of this exception and its causes that is a network failure (a timeout included) or a
 * server's non-success answer, or null when there is none.
 */
fun Throwable.networkCause(): Throwable? =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .firstOrNull { it is IOException || it is ResponseException || it is HttpStatusException }

private const val MAX_CAUSE_DEPTH = 16

/** A server answered with a non-success HTTP [code]. Users see what it means, never the number. */
class HttpStatusException(
    val code: Int,
) : Exception("HTTP $code")

/** What should be an M3U playlist has no `#EXTM3U` header. */
class InvalidPlaylistException : Exception("Not an M3U playlist: missing #EXTM3U header")

/** A file to import is not a settings file that this app exported. */
class InvalidSettingsFileException : Exception("Invalid settings file format")

private fun httpStatusKind(
    code: Int,
    context: Context,
): ErrorKind =
    when (code) {
        401, 403 -> authFailureKind(context)
        404, 410 -> ErrorKind.NOT_FOUND
        in 500..599 -> ErrorKind.SERVER
        else -> ErrorKind.GENERIC
    }

// A saved login this device had to reset (lost Keystore key) explains a failed login better than
// "check your username and password". See CredentialStoreHealth.
private fun authFailureKind(context: Context): ErrorKind =
    if (CredentialStoreHealth.anyLost(context)) ErrorKind.SAVED_LOGIN_LOST else ErrorKind.UNAUTHORIZED

/**
 * Friendly message, with the raw exception text appended when [includeRaw] is true.
 * Gate [includeRaw] on `AppSettings.isDevMode` at the call site so developers can still
 * diagnose while normal users only ever see the friendly message.
 */
fun friendlyErrorMessage(
    e: Throwable,
    context: Context,
    includeRaw: Boolean,
): String {
    val friendly = friendlyErrorMessage(e, context)
    // Masked: a raw message can carry the request URL, login and password included.
    val raw = e.message?.let(Redact::text)
    return if (includeRaw && !raw.isNullOrBlank()) "$friendly\n\n[dev] $raw" else friendly
}
