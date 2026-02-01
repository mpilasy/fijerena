@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * Settings screen for app configuration.
 *
 * Features:
 * - Change provider URL
 * - Adjust watch history size
 * - Toggle developer mode
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProviderChanged: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        val accountManager = AccountManager(context.applicationContext)
        XtreamRepository(accountManager, context.applicationContext)
    }
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(repository.getCurrentUrl() ?: "") }
    var newUrl by remember { mutableStateOf("") }
    var watchHistorySize by remember { mutableStateOf(appSettings.watchHistorySize.toString()) }
    var isDevMode by remember { mutableStateOf(appSettings.isDevMode) }
    var isChangingUrl by remember { mutableStateOf(false) }
    var urlChangeError by remember { mutableStateOf<String?>(null) }
    var urlChangeSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Settings List
        TvLazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Provider URL Setting
            item {
                Column {
                    Text(
                        text = "Provider URL",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current: $currentUrl",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newUrl,
                            onValueChange = {
                                newUrl = it
                                urlChangeError = null
                                urlChangeSuccess = false
                            },
                            label = { Text("New URL") },
                            placeholder = { Text("http://example.com:8080") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                if (newUrl.isNotBlank()) {
                                    isChangingUrl = true
                                    urlChangeError = null
                                    urlChangeSuccess = false

                                    coroutineScope.launch {
                                        when (val result = repository.updateProviderUrl(newUrl)) {
                                            is Result.Success -> {
                                                currentUrl = newUrl
                                                newUrl = ""
                                                urlChangeSuccess = true
                                                isChangingUrl = false
                                                // Notify parent that provider changed
                                                onProviderChanged()
                                            }
                                            is Result.Error -> {
                                                urlChangeError = result.message ?: "Failed to update URL"
                                                isChangingUrl = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = newUrl.isNotBlank() && !isChangingUrl
                        ) {
                            if (isChangingUrl) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            } else {
                                Text("Change")
                            }
                        }
                    }

                    if (urlChangeError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = urlChangeError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (urlChangeSuccess) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Provider URL updated successfully!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Watch History Size Setting
            item {
                Column {
                    Text(
                        text = "Last Watched Queue Size",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Number of items to keep in the Last Watched category (1-100)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = watchHistorySize,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                                    watchHistorySize = newValue
                                }
                            },
                            label = { Text("Queue Size") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(200.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                val size = watchHistorySize.toIntOrNull()
                                if (size != null && size in 1..100) {
                                    appSettings.watchHistorySize = size
                                }
                            },
                            enabled = watchHistorySize.toIntOrNull()?.let { it in 1..100 } == true
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // Developer Mode Setting
            item {
                Column {
                    Text(
                        text = "Developer Mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enable stats for nerds, payload size tracking, and debug features",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isDevMode,
                            onCheckedChange = { enabled ->
                                isDevMode = enabled
                                appSettings.isDevMode = enabled
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isDevMode) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDevMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
