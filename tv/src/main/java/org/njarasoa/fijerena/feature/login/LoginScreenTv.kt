package org.njarasoa.fijerena.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.data.AuthViewModel

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
fun LoginScreenTv(
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(LocalContext.current.applicationContext)
    ),
    authViewModel: AuthViewModel = viewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

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
        buttonFocusRequester.requestFocus()
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
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp), // 5% overscan safety margin for TV
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Xtream IPTV Login",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Server URL field
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://example.com:8080") },
                singleLine = true,
                modifier = Modifier
                    .width(600.dp) // Wide field for TV
                    .padding(bottom = 20.dp)
                    .focusRequester(urlFocusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { usernameFocusRequester.requestFocus() }
                ),
                enabled = uiState !is LoginViewModel.UiState.Loading
            )

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier
                    .width(600.dp)
                    .padding(bottom = 20.dp)
                    .focusRequester(usernameFocusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                ),
                enabled = uiState !is LoginViewModel.UiState.Loading
            )

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .width(600.dp)
                    .padding(bottom = 32.dp)
                    .focusRequester(passwordFocusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { buttonFocusRequester.requestFocus() }
                ),
                enabled = uiState !is LoginViewModel.UiState.Loading
            )

            // Error message
            if (uiState is LoginViewModel.UiState.Error) {
                Text(
                    text = (uiState as LoginViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Login button with highly visible focus state
            Button(
                onClick = {
                    viewModel.login(serverUrl, username, password)
                },
                modifier = Modifier
                    .width(600.dp)
                    .height(64.dp)
                    .focusRequester(buttonFocusRequester),
                enabled = serverUrl.isNotBlank() &&
                        username.isNotBlank() &&
                        password.isNotBlank() &&
                        uiState !is LoginViewModel.UiState.Loading,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContainerColor = Color(0xFF00FF00), // Bright green when focused
                    focusedContentColor = Color.Black
                ),
                scale = ButtonDefaults.scale(
                    focusedScale = 1.05f // Slightly enlarge when focused
                )
            ) {
                if (uiState is LoginViewModel.UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            // Success message
            if (uiState is LoginViewModel.UiState.Success) {
                Text(
                    text = "Login successful!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}
