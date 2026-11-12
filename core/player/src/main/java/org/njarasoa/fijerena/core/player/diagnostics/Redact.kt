package org.njarasoa.fijerena.core.player.diagnostics

/**
 * Masks login secrets in text bound for logcat, [CrashLog] or a shared diagnostics report.
 * `core:player` can't see the stored credentials, so this goes by shape:
 * - Xtream stream paths `/(live|movie|series|timeshift)/<user>/<pass>/…`;
 * - the values of the `username`, `password`, `token`, `api_key`, `api-key`, `X-Emby-Token` and
 *   `access_token` query parameters (any case);
 * - URL userinfo `scheme://user:pass@host`.
 *
 * Idempotent, and text without such shapes comes back unchanged. See
 * the next level rock solid resilience plan → R-16.
 */
object Redact {
    const val MASK = "***"

    private val userInfo = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^/?#\s@]+@""")

    private val xtreamPath = Regex("""(/(?:live|movie|series|timeshift)/)[^/?#\s]+/[^/?#\s]+/""", RegexOption.IGNORE_CASE)

    private val secretQuery =
        Regex("""([?&;](?:username|password|token|api_key|api-key|x-emby-token|access_token)=)[^&#\s]+""", RegexOption.IGNORE_CASE)

    fun text(input: String): String =
        input
            .replace(userInfo, "$1$MASK@")
            .replace(xtreamPath, "$1$MASK/$MASK/")
            .replace(secretQuery, "$1$MASK")

    // The host part of a URL (with its userinfo and port), up to the path, query or fragment.
    private val urlHost = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^/?#\s"'<>]+""")

    private val bearer = Regex("""(\bBearer\s+)[^\s"',;]+""", RegexOption.IGNORE_CASE)

    /**
     * For a device report that goes to the other devices of a sync group (the device diagnostics
     * sync plan): [text], then the host of every URL becomes [MASK] (only the scheme stays before
     * the path) and the token after `Bearer` too. A report gets copied and shared from the phone, so it does
     * not name the servers. Logs on the device keep the hosts. Idempotent, as [text].
     */
    fun report(input: String): String =
        text(input)
            .replace(urlHost, "$1$MASK")
            .replace(bearer, "$1$MASK")
}
