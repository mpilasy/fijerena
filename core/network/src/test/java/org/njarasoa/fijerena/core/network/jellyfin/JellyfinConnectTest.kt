package org.njarasoa.fijerena.core.network.jellyfin

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** connect(): one sign-in at a time, and a refused login is not sent again right away. */
class JellyfinConnectTest {
    private val api = mockk<JellyfinApiService>(relaxed = true)
    private var clockMs = 1_000_000L
    private val provider =
        JellyfinMediaProvider(
            providerId = 1L,
            serverUrl = "http://localhost",
            username = "user",
            password = "pass",
            deviceId = "device",
            injectedApi = api,
            now = { clockMs },
        )

    @Test
    fun `requests that start together share one sign-in`() =
        runTest {
            var signedIn = false
            every { api.isAuthenticated() } answers { signedIn }
            coEvery { api.authenticate(any(), any()) } coAnswers {
                delay(50)
                signedIn = true
                Result.success(mockk(relaxed = true))
            }

            val results = List(5) { async { provider.connect() } }.awaitAll()

            assertTrue(results.all { it.isSuccess })
            coVerify(exactly = 1) { api.authenticate("user", "pass") }
        }

    @Test
    fun `a refused login is sent once, not by every waiting request`() =
        runTest {
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate(any(), any()) } coAnswers {
                delay(50)
                Result.failure(JellyfinLoginRejectedException("Invalid username or password"))
            }

            val results = List(5) { async { provider.connect() } }.awaitAll()

            assertTrue(results.all { it.exceptionOrNull() is JellyfinLoginRejectedException })
            coVerify(exactly = 1) { api.authenticate(any(), any()) }
        }

    @Test
    fun `a refused login is tried again once the wait is over`() =
        runTest {
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate(any(), any()) } returns
                Result.failure(JellyfinLoginRejectedException("Invalid username or password"))

            provider.connect()
            clockMs += REJECTED_LOGIN_RETRY_MS - 1
            provider.connect()
            coVerify(exactly = 1) { api.authenticate(any(), any()) }

            clockMs += 1
            provider.connect()
            coVerify(exactly = 2) { api.authenticate(any(), any()) }
        }

    @Test
    fun `other failures (network) are not held back`() =
        runTest {
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate(any(), any()) } returns Result.failure(java.io.IOException("timeout"))

            provider.connect()
            provider.connect()

            coVerify(exactly = 2) { api.authenticate(any(), any()) }
        }
}
