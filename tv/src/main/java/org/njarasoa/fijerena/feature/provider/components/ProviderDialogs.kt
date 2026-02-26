package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.FilterMode
import org.njarasoa.fijerena.core.network.provider.ScriptType
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.modifiers.tvFocusableNoScale
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmText: String = "Clear",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = CinemaTextPrimary) },
        text = { Text(text, color = CinemaTextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isDanger) CinemaError else CinemaAccent,
                    contentColor = if (isDanger) Color.White else CinemaTextPrimary
                )
            ) { Text(confirmText) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = CinemaSurfaceVariant,
                    contentColor = CinemaTextPrimary
                )
            ) { Text("Cancel") }
        },
        containerColor = CinemaSurface
    )
}

@Composable
fun CategoryFilterDialog(
    currentFilters: CategoryFilters,
    onSave: (CategoryFilters) -> Unit,
    onDismiss: () -> Unit
) {
    val scale = LocalUiScale.current
    var filterMode by remember { mutableStateOf(currentFilters.mode) }
    var prefixesText by remember { mutableStateOf(currentFilters.prefixes.joinToString(", ")) }
    var selectedScripts by remember { mutableStateOf(currentFilters.allowedScripts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category Filters", color = CinemaTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale))
            ) {
                Text("Filter Mode:", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.tv.material3.Button(
                        onClick = { filterMode = FilterMode.EXCLUDE },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (filterMode == FilterMode.EXCLUDE) CinemaAccent else CinemaSurfaceVariant
                        )
                    ) { Text("Exclude") }
                    androidx.tv.material3.Button(
                        onClick = { filterMode = FilterMode.INCLUDE },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (filterMode == FilterMode.INCLUDE) CinemaAccent else CinemaSurfaceVariant
                        )
                    ) { Text("Include") }
                }
                Text(
                    if (filterMode == FilterMode.EXCLUDE) "Hide categories that start with these prefixes"
                    else "Show only categories that start with these prefixes",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)),
                    color = CinemaTextSecondary
                )
                Text("Prefixes (comma-separated):", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
                OutlinedTextField(
                    value = prefixesText,
                    onValueChange = { prefixesText = it },
                    label = { Text("Prefixes (comma-separated)") },
                    placeholder = { Text("e.g., XXX, Adult, 18+") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CinemaSurface,
                        unfocusedContainerColor = CinemaSurface,
                        focusedBorderColor = CinemaAccent,
                        unfocusedBorderColor = CinemaSurfaceVariant,
                        focusedTextColor = CinemaTextPrimary,
                        unfocusedTextColor = CinemaTextPrimary,
                        focusedLabelColor = CinemaAccent,
                        unfocusedLabelColor = CinemaTextSecondary,
                        cursorColor = CinemaAccent
                    )
                )
                Text("Language Script Filter:", style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize.scaled(scale)), color = CinemaTextPrimary)
                Text("Show only categories in selected scripts (none = show all)", style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize.scaled(scale)), color = CinemaTextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
                    val scriptCheckboxColors = CheckboxDefaults.colors(
                        checkedColor = CinemaAccent,
                        uncheckedColor = CinemaTextSecondary,
                        checkmarkColor = CinemaTextPrimary
                    )
                    ScriptType.entries.forEach { script ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.tvFocusableNoScale()
                        ) {
                            Checkbox(
                                checked = script in selectedScripts,
                                onCheckedChange = { checked ->
                                    selectedScripts = if (checked) selectedScripts + script else selectedScripts - script
                                },
                                colors = scriptCheckboxColors
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs.scaled(scale)))
                            Text(text = script.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize.scaled(scale)), color = CinemaTextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prefixes = prefixesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val newFilters = CategoryFilters(mode = filterMode, prefixes = prefixes, allowedScripts = selectedScripts)
                    onSave(newFilters)
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaAccent, contentColor = CinemaTextPrimary)
            ) { Text("Save") }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant, contentColor = CinemaTextPrimary)
            ) { Text("Cancel") }
        },
        containerColor = CinemaSurface
    )
}

@Composable
fun QuickConnectDialog(
    url: String,
    onSuccess: (name: String, username: String, token: String, userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scale = LocalUiScale.current
    var qcCode by remember { mutableStateOf("") }
    var qcSecret by remember { mutableStateOf("") }
    var qcError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        qcCode = ""
        qcSecret = ""
        qcError = null
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "fijerena"
        val api = JellyfinApiService(url.trimEnd('/'), deviceId)
        val initResult = api.initiateQuickConnect()
        if (initResult.isFailure) {
            qcError = initResult.exceptionOrNull()?.message ?: "Failed to start Quick Connect"
            return@LaunchedEffect
        }
        val init = initResult.getOrThrow()
        qcCode = init.code
        qcSecret = init.secret
        // Poll every 3 s for up to 2 minutes
        repeat(40) {
            delay(3_000)
            val poll = api.pollQuickConnect(qcSecret)
            if (poll.isFailure) {
                qcError = "Polling failed: ${poll.exceptionOrNull()?.message}"
                return@LaunchedEffect
            }
            if (poll.getOrThrow().authenticated) {
                val authResult = api.authenticateWithQuickConnect(qcSecret)
                if (authResult.isFailure) {
                    qcError = "Authentication failed: ${authResult.exceptionOrNull()?.message}"
                    return@LaunchedEffect
                }
                val auth = authResult.getOrThrow()
                onSuccess(auth.user.name, auth.user.name, auth.accessToken, auth.user.id)
                return@LaunchedEffect
            }
        }
        qcError = "Timed out waiting for approval. Please try again."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Connect", color = CinemaTextPrimary) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    qcError != null -> {
                        Text(
                            text = qcError!!,
                            color = CinemaError,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    qcCode.isEmpty() -> {
                        CircularProgressIndicator(color = CinemaAccent)
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = "Connecting to server...",
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            text = "Enter this code in Jellyfin:",
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = qcCode,
                            color = CinemaAccent,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize.scaled(scale))
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        Text(
                            text = "Open Jellyfin → Dashboard → Quick Connect, then enter the code above.",
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                        CircularProgressIndicator(color = CinemaAccent)
                        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
                        Text(
                            text = "Waiting for approval...",
                            color = CinemaTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = CinemaSurfaceVariant,
                    contentColor = CinemaTextPrimary
                )
            ) { Text("Cancel") }
        },
        containerColor = CinemaSurface
    )
}
