package org.njarasoa.fijerena.core.player.source

/**
 * HTTP statuses that say the provider's server can't be reached or isn't answering: a gateway's
 * 502-504, and Cloudflare's 520-527 (in front of many providers; 523 = it can't reach the
 * provider's server). Retried as any fault, but from the second attempt with a message saying so
 * instead of a silent "Buffering…" — a first attempt alone already spends about 45 s on its own
 * retries.
 */
object ServerDown {
    fun isServerDown(httpStatus: Int?): Boolean = httpStatus != null && (httpStatus in 502..504 || httpStatus in 520..527)
}
