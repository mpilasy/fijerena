package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.ui.components.buttons.CinemaButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileEditProviderScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val accountManager = remember { AccountManager(context.applicationContext) }
    val repository = remember { XtreamRepository(accountManager, context.applicationContext) }
    val scope = rememberCoroutineScope()

    val currentUrl = remember { accountManager.getCredentials()?.url ?: "" }
    var providerUrl by remember { mutableStateOf(currentUrl) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Provider URL") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Change Provider URL",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Your credentials will be preserved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = providerUrl,
                onValueChange = { providerUrl = it },
                label = { Text("Provider URL") },
                placeholder = { Text("http://example.com:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        disabledTextColor = CinemaTextPrimary.copy(alpha = CinemaAlpha.textDisabled),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.tint),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    ),
            )

            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (message.contains("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CinemaOutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }

                CinemaButton(
                    onClick = {
                        if (providerUrl.isBlank()) {
                            message = "URL cannot be empty"
                            return@CinemaButton
                        }

                        scope.launch {
                            isLoading = true
                            message = ""

                            when (val result = repository.updateProviderUrl(providerUrl.trim())) {
                                is Result.Success -> {
                                    isLoading = false
                                    message = "Provider URL updated successfully"
                                    onSaved()
                                }
                                is Result.Error -> {
                                    isLoading = false
                                    message = result.message ?: "Failed to update provider URL"
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && providerUrl.isNotBlank() && providerUrl.trim() != currentUrl,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(MobileDimensions.iconDefault))
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}
