package org.njarasoa.fijerena.core.player.network

import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** A failed load in a few characters, for the Expert-mode buffering line: "HTTP 503", "timeout", "DNS". */
fun loadErrorLabel(error: IOException): String {
    if (error is HttpDataSource.InvalidResponseCodeException) return "HTTP ${error.responseCode}"
    val causes = generateSequence<Throwable>(error) { it.cause }
    val cause = causes.firstOrNull { it is SocketTimeoutException || it is UnknownHostException }
    return when (cause) {
        is SocketTimeoutException -> {
            "timeout"
        }

        is UnknownHostException -> {
            "DNS"
        }

        else -> {
            error.javaClass.simpleName
                .removeSuffix("Exception")
                .ifEmpty { "error" }
        }
    }
}
