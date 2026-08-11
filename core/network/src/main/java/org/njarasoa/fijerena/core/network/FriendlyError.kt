package org.njarasoa.fijerena.core.network

import android.content.Context
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
): String =
    when (e) {
        is UnknownHostException -> context.getString(R.string.error_no_internet)
        is SocketTimeoutException -> context.getString(R.string.error_timeout)
        is SSLException -> context.getString(R.string.error_ssl)
        is IOException -> context.getString(R.string.error_network)
        else -> {
            val msg = e.message.orEmpty()
            when {
                msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("Unauthorized", ignoreCase = true) ->
                    context.getString(R.string.error_unauthorized)
                else -> context.getString(R.string.error_generic)
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
    context: Context,
    includeRaw: Boolean,
): String {
    val friendly = friendlyErrorMessage(e, context)
    val raw = e.message
    return if (includeRaw && !raw.isNullOrBlank()) "$friendly\n\n[dev] $raw" else friendly
}
