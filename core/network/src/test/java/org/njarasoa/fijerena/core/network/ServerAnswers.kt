package org.njarasoa.fijerena.core.network

import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk

/** A server's [code] answer as Ktor throws it, with a Retry-After header when [retryAfter] is set. */
fun serverAnswer(
    code: Int,
    retryAfter: String? = null,
): ServerResponseException {
    val response =
        mockk<HttpResponse>(relaxed = true) {
            every { status } returns HttpStatusCode.fromValue(code)
            every { headers } returns
                if (retryAfter == null) headersOf() else headersOf(HttpHeaders.RetryAfter, retryAfter)
        }
    return ServerResponseException(response, "")
}
