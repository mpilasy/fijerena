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
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-16.
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
}
