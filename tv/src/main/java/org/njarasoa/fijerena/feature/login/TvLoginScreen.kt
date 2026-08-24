package org.njarasoa.fijerena.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.core.data.AuthViewModel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.LoginViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.LoginViewModelFactory
import org.njarasoa.fijerena.ui.components.modifiers.tvDpadEscape
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * TV-optimized login screen for Xtream IPTV authentication.
 *
 * Features:
 * - androidx.tv.material3 components for TV UI
 * - D-pad friendly navigation with automatic focus management
 * - Highly visible focused button state
 * - Auto-focus on first text field
 * - Loading indicator during authentication
 * - Error message display
 * - TV-safe overscan margins
 *
 * @param viewModel LoginViewModel instance
 * @param onLoginSuccess Callback when authentication succeeds
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvLoginScreen(
    viewModel: LoginViewModel =
        viewModel(
            factory = LoginViewModelFactory(LocalContext.current.applicationContext),
        ),
    authViewModel: AuthViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // FocusRequesters for each field
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    // Auto-focus login button on screen open
    LaunchedEffect(Unit) {
        try {
            buttonFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    // Attempt to restore session from encrypted credentials on startup
    LaunchedEffect(Unit) {
        viewModel.restoreSession()
    }

    // Handle success state and update AuthViewModel
    LaunchedEffect(uiState) {
        if (uiState is LoginViewModel.UiState.Success) {
            val authResponse = (uiState as LoginViewModel.UiState.Success).authResponse
            authViewModel.setAuthSession(authResponse, viewModel.getServerUrl())
            onLoginSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = org.njarasoa.fijerena.ui.theme.Spacing.tvSafeMarginHorizontal,
                        vertical = org.njarasoa.fijerena.ui.theme.Spacing.tvSafeMarginVertical,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Title
            Text(
                text = stringResource(R.string.login_xtream_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xxl),
            )

            // Server URL field
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.login_server_url_label)) },
                placeholder = { Text(stringResource(R.string.login_server_url_placeholder)) },
                singleLine = true,
                modifier =
                    Modifier
                        .width(TvDimensions.formFieldWidth) // Wide field for TV
                        .padding(bottom = Spacing.md)
                        .tvDpadEscape()
                        .focusRequester(urlFocusRequester),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        disabledTextColor =
                            org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
                                .copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.scrim),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                        focusedPlaceholderColor = org.njarasoa.fijerena.ui.theme.CinemaTextSecondary,
                        unfocusedPlaceholderColor = org.njarasoa.fijerena.ui.theme.CinemaTextSecondary,
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { usernameFocusRequester.requestFocus() },
                    ),
                enabled = uiState !is LoginViewModel.UiState.Loading,
            )

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username_label)) },
                singleLine = true,
                modifier =
                    Modifier
                        .width(TvDimensions.formFieldWidth)
                        .padding(bottom = Spacing.md)
                        .tvDpadEscape()
                        .focusRequester(usernameFocusRequester),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        disabledTextColor =
                            org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
                                .copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.scrim),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                    ),
                enabled = uiState !is LoginViewModel.UiState.Loading,
            )

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier =
                    Modifier
                        .width(TvDimensions.formFieldWidth)
                        .padding(bottom = Spacing.xl)
                        .tvDpadEscape()
                        .focusRequester(passwordFocusRequester),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        unfocusedTextColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        disabledTextColor =
                            org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
                                .copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.scrim),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = { buttonFocusRequester.requestFocus() },
                    ),
                enabled = uiState !is LoginViewModel.UiState.Loading,
            )

            // Error message
            if (uiState is LoginViewModel.UiState.Error) {
                Text(
                    text = (uiState as LoginViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = Spacing.lg),
                )
            }

            // Login button with highly visible focus state
            Button(
                onClick = {
                    viewModel.login(serverUrl, username, password, rememberMe = true)
                },
                modifier =
                    Modifier
                        .width(TvDimensions.formFieldWidth)
                        .height(TvDimensions.buttonHeight)
                        .focusRequester(buttonFocusRequester),
                enabled =
                    serverUrl.isNotBlank() &&
                        username.isNotBlank() &&
                        password.isNotBlank() &&
                        uiState !is LoginViewModel.UiState.Loading,
                colors =
                    ButtonDefaults.colors(
                        containerColor = org.njarasoa.fijerena.ui.theme.CinemaAccent,
                        contentColor = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                        focusedContainerColor = org.njarasoa.fijerena.ui.theme.CinemaAccentLight,
                        focusedContentColor = org.njarasoa.fijerena.ui.theme.CinemaBackground,
                    ),
                scale =
                    ButtonDefaults.scale(
                        focusedScale = TvFocusTokens.focusedScaleSubtle, // Slightly enlarge when focused
                    ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(CinemaCornerRadius.small.scaled(scale))),
            ) {
                if (uiState is LoginViewModel.UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(TvDimensions.iconLarge),
                        color = org.njarasoa.fijerena.ui.theme.CinemaTextPrimary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_button),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            // Success message
            if (uiState is LoginViewModel.UiState.Success) {
                Text(
                    text = stringResource(R.string.login_success_toast),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
    }
}
