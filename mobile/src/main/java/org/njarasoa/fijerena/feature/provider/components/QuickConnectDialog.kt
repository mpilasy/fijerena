package org.njarasoa.fijerena.feature.provider.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.jellyfin.JellyfinApiService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.ProviderViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

@Composable
fun QuickConnectDialog(
    showQuickConnectDialog: Boolean,
    qcCode: String,
    qcSecret: String,
    qcError: String?,
    url: String,
    name: String,
    username: String,
    context: Context,
    viewModel: ProviderViewModel,
    onQcCodeChange: (String) -> Unit,
    onQcSecretChange: (String) -> Unit,
    onQcErrorChange: (String?) -> Unit,
    onShowQuickConnectDialogChange: (Boolean) -> Unit,
    onSuccess: () -> Unit
) {
    if (showQuickConnectDialog) {
        LaunchedEffect(Unit) {
            onQcCodeChange("")
            onQcSecretChange("")
            onQcErrorChange(null)
            val deviceId =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                ) ?: "fijerena"
            val api = JellyfinApiService(url.trimEnd('/'), deviceId)
            val initResult = api.initiateQuickConnect()
            if (initResult.isFailure) {
                onQcErrorChange(initResult.exceptionOrNull()?.message ?: context.getString(R.string.provider_qc_init_failed))
                return@LaunchedEffect
            }
            val init = initResult.getOrThrow()
            onQcCodeChange(init.code)
            onQcSecretChange(init.secret)
            // Poll every 3 s for up to 2 minutes
            repeat(40) {
                delay(3_000)
                val poll = api.pollQuickConnect(init.secret)
                if (poll.isFailure) {
                    onQcErrorChange(context.getString(R.string.provider_qc_poll_failed, poll.exceptionOrNull()?.message))
                    return@LaunchedEffect
                }
                if (poll.getOrThrow().authenticated) {
                    val authResult = api.authenticateWithQuickConnect(init.secret)
                    if (authResult.isFailure) {
                        onQcErrorChange(context.getString(R.string.provider_qc_auth_failed, authResult.exceptionOrNull()?.message))
                        return@LaunchedEffect
                    }
                    val auth = authResult.getOrThrow()
                    onShowQuickConnectDialogChange(false)
                    viewModel.quickConnectSave(
                        name = name.ifBlank { auth.user.name },
                        url = url.trimEnd('/'),
                        username = username.ifBlank { auth.user.name },
                        token = auth.accessToken,
                        userId = auth.user.id,
                        onComplete = onSuccess,
                    )
                    return@LaunchedEffect
                }
            }
            onQcErrorChange(context.getString(R.string.provider_qc_timeout))
        }

        CinemaAlertDialog(
            onDismissRequest = { onShowQuickConnectDialogChange(false) },
            title = { Text(stringResource(R.string.provider_quick_connect_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when {
                        qcError != null -> {
                            Text(
                                text = qcError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        qcCode.isEmpty() -> {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                            Text(
                                text = stringResource(R.string.provider_connecting_server),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.provider_qc_enter_code),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                            Text(
                                text = qcCode,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            Text(
                                text = stringResource(R.string.provider_qc_instructions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(CinemaSpacing.md))
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                            Text(
                                text = stringResource(R.string.provider_qc_waiting),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                CinemaOutlinedButton(onClick = { onShowQuickConnectDialogChange(false) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
