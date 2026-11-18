package org.njarasoa.fijerena.core.player.network

import android.content.Context
import java.net.URLConnection

/**
 * The User-Agent of every call, streams included: the app's own, `Fijerena/<version> (Android)`
 * ([app]), or the one a Nerd set ([value], Settings → This Device). Every OkHttp client (through
 * [NetworkModule.okHttpClient]'s interceptor) and every `HttpURLConnection` ([apply]) send
 * [current]; the trailer's WebView sends [value] only, else its own (YouTube's embedded player
 * expects a browser). `AppSettings` sets [value] at start and when the setting changes.
 */
object CustomUserAgent {
    const val HEADER = "User-Agent"
    const val MAX_LENGTH = 300

    /** The app's own User-Agent with the installed version, set by [init]; without a version until then. */
    @Volatile
    var app: String = appUserAgent(null)
        private set

    /** The custom User-Agent a Nerd set for every call; null for the defaults. */
    @Volatile
    var value: String? = null
        private set

    /** Reads the installed app's version, so [app] always names the build that runs. */
    fun init(context: Context) {
        val version =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        app = appUserAgent(version)
    }

    /** Sets [text] for every call; null, blank or nothing usable left after [clean] goes back to the defaults. */
    fun set(text: String?) {
        value = text?.let(::clean)?.takeIf { it.isNotEmpty() }
    }

    /** What every call sends: the custom one, else the app's. */
    val current: String get() = value ?: app

    /** Puts the User-Agent on [connection]: the custom one, else the app's. */
    fun apply(connection: URLConnection) {
        connection.setRequestProperty(HEADER, current)
    }

    /**
     * [text] as a header value can carry it: printable ASCII only (OkHttp refuses the rest),
     * trimmed, [MAX_LENGTH] characters at most.
     */
    fun clean(text: String): String = text.filter { it in ' '..'~' }.trim().take(MAX_LENGTH)

    /** `Fijerena/<version> (Android)`; `Fijerena (Android)` when the version can't be read. */
    fun appUserAgent(version: String?): String =
        version?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { "Fijerena/$it (Android)" } ?: "Fijerena (Android)"

    /** The installed version as [init] read it, for headers that carry it apart (Jellyfin's); "unknown" before. */
    val appVersion: String get() = app.substringAfter('/', "").substringBefore(' ').ifEmpty { "unknown" }
}
