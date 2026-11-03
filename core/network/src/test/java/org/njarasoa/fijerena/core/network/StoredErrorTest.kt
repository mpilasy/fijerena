package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * A kept error is a code, so its message follows the app language when it shows, not the language
 * of the time it occurred; an old value (message text) shows the generic message.
 */
class StoredErrorTest {
    private val healthPrefs = mockk<SharedPreferences> { every { all } returns emptyMap<String, Any>() }

    private fun context(
        network: String,
        generic: String,
        catalogue: String,
    ): Context =
        mockk {
            every { getString(R.string.error_network) } returns network
            every { getString(R.string.error_generic) } returns generic
            every { getString(R.string.error_timeout) } returns "timeout"
            every { getString(R.string.error_catalog_sync_failed, "timeout") } returns catalogue.format("timeout")
            every { applicationContext } returns this
            every { getSharedPreferences(any(), any()) } returns healthPrefs
        }

    private val french = context("Erreur réseau", "Une erreur est survenue", "Échec de la mise à jour : %s")
    private val malagasy = context("Olana tamin'ny tambajotra", "Nisy olana", "Tsy nety ny fanavaozana: %s")

    @Test
    fun aKeptErrorShowsInTheLanguageInUse() {
        val stored = StoredError(ErrorKind.NETWORK).encode()

        assertEquals("Erreur réseau", StoredError.message(stored, french, includeRaw = false))
        assertEquals("Olana tamin'ny tambajotra", StoredError.message(stored, malagasy, includeRaw = false))
    }

    @Test
    fun theCodeHoldsNoMessageText() {
        val stored = StoredError.of(java.io.IOException("connection reset"), french).encode()

        assertEquals("error:NETWORK\nconnection reset", stored)
        assertFalse(stored.contains("Erreur"))
    }

    @Test
    fun aCatalogueFailureKeepsItsWrapper() {
        val stored = StoredError(ErrorKind.TIMEOUT, catalogue = true).encode()

        assertEquals(StoredError(ErrorKind.TIMEOUT, catalogue = true), StoredError.decode(stored))
        assertEquals("Échec de la mise à jour : timeout", StoredError.message(stored, french, includeRaw = false))
    }

    @Test
    fun theRawTextShowsOnlyInDeveloperMode() {
        val stored = StoredError.of(SocketTimeoutException("read timed out"), french).encode()

        assertEquals("timeout", StoredError.message(stored, french, includeRaw = false))
        assertEquals("timeout\n\n[dev] read timed out", StoredError.message(stored, french, includeRaw = true))
    }

    @Test
    fun theRawTextIsMaskedBeforeItIsKept() {
        val stored = StoredError(ErrorKind.NETWORK, raw = "GET http://me:secret@host/x failed").encode()

        assertFalse(stored.contains("secret"))
    }

    @Test
    fun anOldTextValueShowsTheGenericMessageInTheLanguageInUse() {
        val old = "Nisy olana tamin'ny mpizara. Andramo indray any aoriana."

        assertEquals(StoredError(ErrorKind.GENERIC, raw = old), StoredError.decode(old))
        assertEquals("Une erreur est survenue", StoredError.message(old, french, includeRaw = false))
        assertTrue(StoredError.message(old, french, includeRaw = true).endsWith("[dev] $old"))
    }

    @Test
    fun anUnknownCodeShowsTheGenericMessage() {
        assertEquals("Une erreur est survenue", StoredError.message("error:SOMETHING_NEW", french, includeRaw = false))
    }

    @Test
    fun everyKindRoundTrips() {
        ErrorKind.entries.forEach { kind ->
            val error = StoredError(kind, raw = "detail\nsecond line")
            assertEquals(error, StoredError.decode(error.encode()))
        }
    }
}
