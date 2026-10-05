package org.njarasoa.fijerena.core.network.xtream

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.R
import org.njarasoa.fijerena.core.network.XtreamMediaProvider
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import java.net.ConnectException

/**
 * A sync whose connect fails says why: the server being unreachable is a transient network
 * failure (retried, "network error"), not "Login failed. Check your username and password." and a
 * permanent error, which is what every connect failure used to be reported as.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSyncRunnerConnectFailureTest {
    private val context =
        mockk<Context>(relaxed = true).also {
            every { it.getString(R.string.error_unauthorized) } returns "LOGIN FAILED"
            every { it.getString(R.string.error_network) } returns "NETWORK ERROR"
        }
    private val provider = ProviderEntity(id = 7, name = "test", url = "http://example.invalid", username = "u")

    @Before
    fun setUp() {
        mockkObject(MediaProviderFactory)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun failingConnect(cause: Exception) {
        val media =
            mockk<XtreamMediaProvider>().also {
                every { it.isConnected() } returns false
                coEvery { it.connect() } returns Result.failure(Exception("Failed to connect to Xtream provider", cause))
            }
        every { MediaProviderFactory.create(provider, context, "pw") } returns media
    }

    @Test
    fun anUnreachableServerIsATransientNetworkError() =
        runTest {
            failingConnect(ConnectException("Failed to connect to /10.0.2.2:8080"))
            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")
            assertTrue("$outcome", outcome is ProviderSyncRunner.Outcome.Transient)
            assertTrue("$outcome", (outcome as ProviderSyncRunner.Outcome.Transient).error.startsWith("NETWORK ERROR"))
        }

    @Test
    fun refusedCredentialsStayAPermanentLoginFailure() =
        runTest {
            failingConnect(Exception("Stored credentials are invalid"))
            val outcome = ProviderSyncRunner.syncProvider(context, provider, "pw")
            assertTrue("$outcome", outcome is ProviderSyncRunner.Outcome.Permanent)
            assertTrue("$outcome", (outcome as ProviderSyncRunner.Outcome.Permanent).error.startsWith("LOGIN FAILED"))
        }
}
