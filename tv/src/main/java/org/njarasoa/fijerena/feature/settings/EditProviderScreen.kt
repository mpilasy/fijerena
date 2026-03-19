@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaSecondaryButton
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * Edit Provider URL screen.
 * Allows changing the provider URL without re-entering username/password.
 */
@Composable
fun EditProviderScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val repository = remember { XtreamRepository(accountManager, context.applicationContext) }

    val currentUrl = remember { accountManager.getCredentials()?.url ?: "" }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = Spacing.tvSafeMarginHorizontal,
                        vertical = Spacing.tvSafeMarginVertical,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.width(TvDimensions.formFieldWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Edit Provider URL",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                Text(
                    text = "Current URL: $currentUrl",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // URL Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        error = null
                    },
                    label = { Text("Provider URL") },
                    placeholder = { Text("Enter new provider URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CinemaTextPrimary,
                            unfocusedTextColor = CinemaTextPrimary,
                            cursorColor = CinemaAccent,
                            focusedBorderColor = CinemaAccent,
                            unfocusedBorderColor = CinemaTextSecondary,
                            focusedLabelColor = CinemaAccent,
                            unfocusedLabelColor = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                            focusedPlaceholderColor = CinemaTextSecondary,
                            unfocusedPlaceholderColor = CinemaTextSecondary,
                        ),
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Error message
                error?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaError,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
                ) {
                    CinemaSecondaryButton(
                        onClick = onBack,
                        enabled = !isLoading,
                        text = "Cancel",
                    )

                    CinemaPrimaryButton(
                        onClick = {
                            if (urlInput.isBlank()) {
                                error = "URL cannot be empty"
                                return@CinemaPrimaryButton
                            }

                            scope.launch {
                                isLoading = true
                                error = null

                                when (val result = repository.updateProviderUrl(urlInput.trim())) {
                                    is Result.Success -> {
                                        isLoading = false
                                        onSuccess()
                                    }
                                    is Result.Error -> {
                                        isLoading = false
                                        error = result.message ?: "Failed to update provider URL"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && urlInput.trim() != currentUrl,
                        text = if (isLoading) "Saving..." else "Save & Re-authenticate",
                    )
                }
            }
        }
    }
}
