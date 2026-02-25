package org.njarasoa.fijerena.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamServerInfo
import org.njarasoa.fijerena.core.player.model.XtreamUserInfo

class AuthViewModelTest {

    private val viewModel = AuthViewModel()

    @Test
    fun `isSessionExpired returns true when auth response is null`() {
        viewModel.clearAuthSession()
        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when expDate is null`() {
        val response = createAuthResponse(expDate = null)
        viewModel.setAuthSession(response, "http://example.com")
        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when expDate is invalid`() {
        val response = createAuthResponse(expDate = "invalid-date")
        viewModel.setAuthSession(response, "http://example.com")
        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when expDate is in the past`() {
        // Current time - 1 hour (3600 seconds)
        val pastDate = (System.currentTimeMillis() / 1000) - 3600
        val response = createAuthResponse(expDate = pastDate.toString())
        viewModel.setAuthSession(response, "http://example.com")
        assertTrue("Session should be expired for past date", viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns false when expDate is in the future`() {
        // Current time + 1 hour (3600 seconds)
        val futureDate = (System.currentTimeMillis() / 1000) + 3600
        val response = createAuthResponse(expDate = futureDate.toString())
        viewModel.setAuthSession(response, "http://example.com")
        assertFalse("Session should be valid for future date", viewModel.isSessionExpired())
    }

    private fun createAuthResponse(expDate: String?): XtreamAuthResponse {
        return XtreamAuthResponse(
            userInfo = XtreamUserInfo(
                username = "user",
                password = "password",
                auth = 1,
                status = "Active",
                expDate = expDate
            ),
            serverInfo = XtreamServerInfo(
                url = "http://example.com",
                port = "80",
                serverProtocol = "http"
            )
        )
    }
}
