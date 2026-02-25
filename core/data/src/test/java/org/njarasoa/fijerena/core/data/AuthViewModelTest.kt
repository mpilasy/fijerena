package org.njarasoa.fijerena.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.model.XtreamAuthResponse
import org.njarasoa.fijerena.core.player.model.XtreamServerInfo
import org.njarasoa.fijerena.core.player.model.XtreamUserInfo

class AuthViewModelTest {

    @Test
    fun `isSessionExpired returns true when no session exists`() {
        val viewModel = AuthViewModel()
        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns false when session is not expired`() {
        val viewModel = AuthViewModel()
        val futureTime = (System.currentTimeMillis() / 1000) + 3600 // 1 hour in future
        val authResponse = createAuthResponse(expDate = futureTime.toString())

        viewModel.setAuthSession(authResponse, "http://example.com")

        assertFalse(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when session is expired`() {
        val viewModel = AuthViewModel()
        val pastTime = (System.currentTimeMillis() / 1000) - 3600 // 1 hour in past
        val authResponse = createAuthResponse(expDate = pastTime.toString())

        viewModel.setAuthSession(authResponse, "http://example.com")

        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when expDate is not a number`() {
        val viewModel = AuthViewModel()
        val authResponse = createAuthResponse(expDate = "not_a_number")

        viewModel.setAuthSession(authResponse, "http://example.com")

        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns true when expDate is null`() {
        val viewModel = AuthViewModel()
        val authResponse = createAuthResponse(expDate = null)

        viewModel.setAuthSession(authResponse, "http://example.com")

        assertTrue(viewModel.isSessionExpired())
    }

    @Test
    fun `isSessionExpired returns false when expDate is Unlimited`() {
        val viewModel = AuthViewModel()
        val authResponse = createAuthResponse(expDate = "Unlimited")

        viewModel.setAuthSession(authResponse, "http://example.com")

        assertFalse(viewModel.isSessionExpired())
    }

    private fun createAuthResponse(expDate: String?): XtreamAuthResponse {
        return XtreamAuthResponse(
            userInfo = XtreamUserInfo(
                username = "testuser",
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
