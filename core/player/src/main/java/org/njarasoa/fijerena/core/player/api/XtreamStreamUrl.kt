package org.njarasoa.fijerena.core.player.api

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The login inside an Xtream stream URL, `…/(live|movie|series|timeshift)/<user>/<pass>/…`, as
 * [XtreamApiService] builds it (both parts URL-encoded). Logins on one panel share stream ids, so
 * a stream plays on another login by swapping these two parts. See
 * docs/plans/20261005_shared-logins-plan.md.
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

    /** Whether [a] and [b] are the same stream, perhaps on different logins of one panel. */
    fun sameStream(
        a: String,
        b: String,
    ): Boolean = a == b || withLogin(a, "", "")?.let { it == withLogin(b, "", "") } == true

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
