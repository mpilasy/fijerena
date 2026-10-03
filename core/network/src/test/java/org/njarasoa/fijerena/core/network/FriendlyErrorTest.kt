package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class FriendlyErrorTest {
    private var lostLogins: Map<String, Any> = emptyMap()
    private val healthPrefs = mockk<SharedPreferences> { every { all } answers { lostLogins } }
    private val context: Context =
        mockk {
            every { getString(R.string.error_generic) } returns "generic"
            every { getString(R.string.error_network) } returns "network"
            every { getString(R.string.error_timeout) } returns "timeout"
            every { getString(R.string.error_unauthorized) } returns "unauthorized"
            every { getString(R.string.error_saved_login_lost) } returns "lost"
            every { applicationContext } returns this
            every { getSharedPreferences(any(), any()) } returns healthPrefs
        }

    @Test
    fun executorRejected_mapsToGeneric_notNetwork() {
        // "executor rejected" is OkHttp's InterruptedIOException message when a client's
        // Dispatcher.executorService was already shut down - an internal lifecycle bug, not a
        // connectivity problem. It must not fall into the generic IOException/network bucket,
        // since "check your connection" is actively wrong guidance for it.
        val e = IOException("executor rejected")

        assertEquals("generic", friendlyErrorMessage(e, context))
    }

    @Test
    fun otherIOException_mapsToNetwork() {
        val e = IOException("connection reset")

        assertEquals("network", friendlyErrorMessage(e, context))
    }

    // docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-14
    @Test
    fun requestDeadlineExceeded_mapsToTimeout_notNetwork() {
        val e =
            io.ktor.client.plugins
                .HttpRequestTimeoutException("http://panel.test/player_api.php", 60_000L)

        assertEquals("timeout", friendlyErrorMessage(e, context))
    }

    // docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-28
    @Test
    fun xtreamInvalidCredentials_isAnAuthFailure() {
        assertEquals("unauthorized", friendlyErrorMessage(Exception("Authentication failed: Invalid credentials"), context))
    }

    @Test
    fun authFailure_afterASavedLoginWasReset_saysTheLoginWasLost() {
        lostLogins = mapOf("provider_creds_4" to true)

        assertEquals("lost", friendlyErrorMessage(Exception("HTTP 401 Unauthorized"), context))
    }

    @Test
    fun missingPassword_afterASavedLoginWasReset_saysTheLoginWasLost() {
        lostLogins = mapOf("xtream_secure_credentials_4" to true)

        assertEquals(
            "lost",
            friendlyErrorMessage(Exception("Failed to connect to Xtream provider: Password not stored. Please login again."), context),
        )
    }

    @Test
    fun unrelatedError_afterASavedLoginWasReset_staysGeneric() {
        lostLogins = mapOf("provider_creds_4" to true)

        assertEquals("generic", friendlyErrorMessage(Exception("Unexpected JSON token"), context))
    }

    @Test
    fun inMemoryPrefs_behaveLikePrefs() {
        val prefs = CredentialStoreHealth.InMemoryPrefs()
        prefs
            .edit()
            .putString("password", "secret")
            .putBoolean("remember", true)
            .apply()
        assertEquals("secret", prefs.getString("password", null))
        prefs.edit().remove("password").commit()
        assertEquals(null, prefs.getString("password", null))
        prefs
            .edit()
            .clear()
            .putString("username", "u")
            .commit()
        assertEquals(mapOf("username" to "u"), prefs.all)
    }
}
