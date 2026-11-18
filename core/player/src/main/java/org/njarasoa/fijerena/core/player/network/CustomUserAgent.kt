package org.njarasoa.fijerena.core.player.network

import java.net.URLConnection

/**
 * The User-Agent that a Nerd set for every call of this device (Settings → This Device), or null
 * for each call's own: the player's for streams, the HTTP library's for the rest. Every OkHttp
 * client (through [NetworkModule.okHttpClient]'s interceptor), every `HttpURLConnection` ([apply])
 * and the trailer's WebView read it. `AppSettings` sets it at start and when the setting changes.
 */
object CustomUserAgent {
    @Volatile
    var value: String? = null
        private set

    /** Sets [text] for every call; null, blank or nothing usable left after [clean] goes back to the defaults. */
    fun set(text: String?) {
        value = text?.let(::clean)?.takeIf { it.isNotEmpty() }
    }

    /** Puts the custom User-Agent on [connection], when there is one. */
    fun apply(connection: URLConnection) {
        value?.let { connection.setRequestProperty(HEADER, it) }
    }

    /**
     * [text] as a header value can carry it: printable ASCII only (OkHttp refuses the rest),
     * trimmed, [MAX_LENGTH] characters at most.
     */
    fun clean(text: String): String = text.filter { it in ' '..'~' }.trim().take(MAX_LENGTH)

    const val HEADER = "User-Agent"
    const val MAX_LENGTH = 300
}
