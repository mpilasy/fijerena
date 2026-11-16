package org.njarasoa.fijerena.core.network.jellyfin

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.RefusedLogins

/** A refused password (401) goes to [RefusedLogins] for the pop-up; Not Now holds it until a sign-in. */
class JellyfinRefusedLoginTest {
    private val api = mockk<JellyfinApiService>(relaxed = true)
    private var clockMs = 1_000_000L

    private fun provider(id: Long) =
        JellyfinMediaProvider(
            providerId = id,
            serverUrl = "http://localhost",
            username = "user",
            password = "pass",
            deviceId = "device",
            injectedApi = api,
            now = { clockMs },
        )

    private fun refusal(status: HttpStatusCode): JellyfinLoginRejectedException {
        val cause = mockk<ClientRequestException>()
        every { cause.response.status } returns status
        return JellyfinLoginRejectedException("refused", cause)
    }

    @After
    fun tearDown() {
        listOf(0L, 7L).forEach(RefusedLogins::forget)
    }

    @Test
    fun `a 401 reports the source, a sign-in clears it`() =
        runTest {
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate(any(), any()) } returns Result.failure(refusal(HttpStatusCode.Unauthorized))
            provider(7L).connect()
            assertEquals(7L, RefusedLogins.first.first())

            coEvery { api.authenticate(any(), any()) } returns Result.success(mockk(relaxed = true))
            provider(7L).connect()
            assertNull(RefusedLogins.first.first())
        }

    @Test
    fun `a 403 or the source form's test login does not report`() =
        runTest {
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate(any(), any()) } returns Result.failure(refusal(HttpStatusCode.Forbidden))
            provider(7L).connect()
            coEvery { api.authenticate(any(), any()) } returns Result.failure(refusal(HttpStatusCode.Unauthorized))
            provider(0L).connect()
            assertNull(RefusedLogins.first.first())
        }

    @Test
    fun `a screen that keeps the provider signs in with the password saved since`() =
        runTest {
            var stored = "old"
            val provider =
                JellyfinMediaProvider(
                    providerId = 7L,
                    serverUrl = "http://localhost",
                    username = "user",
                    password = "old",
                    deviceId = "device",
                    latestPassword = { stored },
                    injectedApi = api,
                    now = { clockMs },
                )
            every { api.isAuthenticated() } returns false
            coEvery { api.authenticate("user", "old") } returns Result.failure(refusal(HttpStatusCode.Unauthorized))
            coEvery { api.authenticate("user", "new") } returns Result.success(mockk(relaxed = true))
            assertTrue(provider.connect().isFailure)

            // Within the refused login's wait: the same password is not sent again, a new one is.
            stored = "new"
            assertTrue(provider.connect().isSuccess)
            coVerify(exactly = 1) { api.authenticate("user", "new") }
        }

    @Test
    fun `Not Now holds the source until its login changes`() =
        runTest {
            RefusedLogins.report(7L)
            RefusedLogins.dismiss(7L)
            RefusedLogins.report(7L)
            assertNull(RefusedLogins.first.first())

            RefusedLogins.forget(7L)
            RefusedLogins.report(7L)
            assertEquals(7L, RefusedLogins.first.first())
        }
}
