package org.njarasoa.fijerena.core.player.source

/**
 * HTTP statuses that say the provider's server cannot be reached or does not answer: a gateway's
 * 502-504, and Cloudflare's 520-527 (in front of many providers; 523 = it cannot reach the
 * provider's server). Retried as any fault, but from the second attempt with a message that says
 * so, not a silent "Buffering…": one attempt alone spends about 45 s on its own retries.
 */
object ServerDown {
    fun isServerDown(httpStatus: Int?): Boolean = httpStatus != null && (httpStatus in 502..504 || httpStatus in 520..527)
}
