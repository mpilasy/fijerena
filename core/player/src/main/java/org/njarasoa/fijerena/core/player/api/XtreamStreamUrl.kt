package org.njarasoa.fijerena.core.player.api

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The login inside an Xtream stream URL, `…/(live|movie|series|timeshift)/<user>/<pass>/…`, as
 * [XtreamApiService] builds it (both parts URL-encoded). Logins on one panel share stream ids, so
 * a stream plays on another login by swapping these two parts. See
 * the shared logins plan.
 */
object XtreamStreamUrl {
    private val loginPath = Regex("""(/(?:live|movie|series|timeshift)/)([^/?#]+)/([^/?#]+)/""", RegexOption.IGNORE_CASE)

    /** The username in [url], decoded, or null when [url] isn't an Xtream stream URL. */
    fun username(url: String): String? =
        loginPath
            .find(url)
            ?.groupValues
            ?.get(2)
            ?.let { URLDecoder.decode(it, "UTF-8") }

    /** [url] with [username] and [password] in place of its login, or null when it has none. */
    fun withLogin(
        url: String,
        username: String,
        password: String,
    ): String? =
        loginPath.find(url)?.let { match ->
            url.replaceRange(match.range, match.groupValues[1] + encode(username) + "/" + encode(password) + "/")
        }

    /**
     * Whether [a] and [b] are the same stream, perhaps on different logins of one panel, or a live
     * channel as HLS and as MPEG-TS (the player falls back from one to the other, [LiveStreamFormat]).
     */
    fun sameStream(
        a: String,
        b: String,
    ): Boolean {
        if (a == b) return true
        val x = LiveStreamFormat.tsFallbackFor(a) ?: a
        val y = LiveStreamFormat.tsFallbackFor(b) ?: b
        return x == y || withLogin(x, "", "")?.let { it == withLogin(y, "", "") } == true
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
