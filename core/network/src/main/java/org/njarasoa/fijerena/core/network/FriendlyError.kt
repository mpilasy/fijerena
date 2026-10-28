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
): String {
    // A network failure inside another exception says what it is: a list read cut short by a
    // timeout comes up as a JSON error, with the timeout among its causes.
    val cause = e.networkCause()
    if (cause != null && cause !== e) return friendlyErrorMessage(cause, context)
    return when {
        // OkHttp's own message when a client's Dispatcher.executorService has already been
        // shut down (e.g. closed while still in use) and something tries to submit one more
        // call through it. It's an InterruptedIOException, so it would otherwise fall into the
        // generic network-error bucket below — but it has nothing to do with the user's
        // connection, and telling them to "check your connection" for an internal lifecycle bug
        // sends them chasing the wrong thing. No connectivity check would ever fix this.
        e.message?.contains("executor rejected", ignoreCase = true) == true -> {
            context.getString(R.string.error_generic)
        }

        e is InvalidPlaylistException -> {
            context.getString(R.string.error_invalid_playlist)
        }

        e is HttpStatusException -> {
            httpStatusMessage(e.code, context)
        }

        e is ResponseException -> {
            httpStatusMessage(e.response.status.value, context)
        }

        e is UnknownHostException -> {
            context.getString(R.string.error_no_internet)
        }

        // HttpRequestTimeoutException: a call that outran its overall deadline (R-14), e.g. a
        // server trickling bytes. It's an IOException, so it would otherwise read as "network error".
        e is SocketTimeoutException || e is HttpRequestTimeoutException -> {
            context.getString(R.string.error_timeout)
        }

        e is SSLException -> {
            context.getString(R.string.error_ssl)
        }

        e is IOException -> {
            context.getString(R.string.error_network)
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
            if (isAuthFailure) authFailureMessage(context) else context.getString(R.string.error_generic)
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

private fun httpStatusMessage(
    code: Int,
    context: Context,
): String =
    when (code) {
        401, 403 -> authFailureMessage(context)
        404, 410 -> context.getString(R.string.error_not_found)
        in 500..599 -> context.getString(R.string.error_server)
        else -> context.getString(R.string.error_generic)
    }

// A saved login this device had to reset (lost Keystore key) explains a failed login better than
// "check your username and password". See CredentialStoreHealth.
private fun authFailureMessage(context: Context): String =
    if (CredentialStoreHealth.anyLost(context)) {
        context.getString(R.string.error_saved_login_lost)
    } else {
        context.getString(R.string.error_unauthorized)
    }

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
