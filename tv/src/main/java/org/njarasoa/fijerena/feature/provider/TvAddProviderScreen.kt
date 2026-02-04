@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.*

@Composable
fun TvAddProviderScreen(
    editId: Long = -1L,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderViewModel = viewModel(
        factory = ProviderViewModelFactory(context)
    )
    val isEditMode = editId > 0L

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Load existing provider data in edit mode
    LaunchedEffect(editId) {
        if (isEditMode) {
            val uiState = viewModel.uiState.value
            val providers = when (uiState) {
                is org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState.SingleProvider ->
                    listOf(uiState.provider)
                is org.njarasoa.fijerena.core.ui.viewmodels.ProviderUiState.MultipleProviders ->
                    uiState.providers
                else -> emptyList()
            }
            val provider = providers.find { it.id == editId }
            if (provider != null) {
                name = provider.name
                url = provider.url
                username = provider.username
                password = viewModel.getPassword(editId) ?: ""
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = Spacing.tvSafeMarginHorizontal,
                    vertical = Spacing.tvSafeMarginVertical
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.width(TvDimensions.formFieldWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditMode) "Edit Provider" else "Add Provider",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Provider Name") },
                    placeholder = { Text("e.g. My IPTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // URL field
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://provider.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        cursorColor = CinemaAccent,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaTextSecondary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        focusedPlaceholderColor = CinemaTextSecondary,
                        unfocusedPlaceholderColor = CinemaTextSecondary
                    )
                )

                // Error message
                error?.let { errorMsg ->
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaError
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally)
                ) {
                    CinemaSecondaryButton(
                        onClick = onBack,
                        enabled = !isSaving,
                        text = "Cancel"
                    )

                    CinemaPrimaryButton(
                        onClick = {
                            when {
                                name.isBlank() -> error = "Provider name is required"
                                url.isBlank() -> error = "Server URL is required"
                                username.isBlank() -> error = "Username is required"
                                password.isBlank() -> error = "Password is required"
                                else -> {
                                    isSaving = true
                                    if (isEditMode) {
                                        viewModel.updateProvider(
                                            editId,
                                            name.trim(),
                                            url.trim(),
                                            username.trim(),
                                            password.trim()
                                        )
                                    } else {
                                        viewModel.addProvider(
                                            name.trim(),
                                            url.trim(),
                                            username.trim(),
                                            password.trim()
                                        )
                                    }
                                    onSuccess()
                                }
                            }
                        },
                        enabled = !isSaving,
                        text = if (isSaving) "Saving..." else if (isEditMode) "Update" else "Add"
                    )
                }
            }
        }
    }
}
