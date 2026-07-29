package org.njarasoa.fijerena.core.network

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
fun friendlyErrorMessage(e: Throwable): String =
    when (e) {
        is UnknownHostException -> "Can't reach the server. Check your internet connection."
        is SocketTimeoutException -> "The server took too long to respond. Please try again."
        is SSLException -> "Secure connection failed. Check the server address."
        is IOException -> "Network error. Check your connection and try again."
        else -> {
            val msg = e.message.orEmpty()
            when {
                msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("Unauthorized", ignoreCase = true) ->
                    "Login failed. Check your username and password."
                else -> "Something went wrong. Please try again."
            }
        }
    }

/**
 * Friendly message, with the raw exception text appended when [includeRaw] is true.
 * Gate [includeRaw] on `AppSettings.isDevMode` at the call site so developers can still
 * diagnose while normal users only ever see the friendly message.
 */
fun friendlyErrorMessage(
    e: Throwable,
    includeRaw: Boolean,
): String {
    val friendly = friendlyErrorMessage(e)
    val raw = e.message
    return if (includeRaw && !raw.isNullOrBlank()) "$friendly\n\n[dev] $raw" else friendly
}
