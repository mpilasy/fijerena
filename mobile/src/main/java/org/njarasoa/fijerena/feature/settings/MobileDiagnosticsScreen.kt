package org.njarasoa.fijerena.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/**
 * Settings → Diagnostics on mobile (developer mode): recorded crashes and process exit reasons,
 * shareable as plain text. See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-30.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DiagnosticsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.settings_diagnostics_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(CinemaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_diagnostics_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    CinemaOutlinedButton(onClick = viewModel::reload) { Text(stringResource(R.string.common_refresh)) }
                    CinemaOutlinedButton(onClick = viewModel::clear) { Text(stringResource(R.string.settings_diagnostics_clear)) }
                    CinemaOutlinedButton(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, viewModel.asText(entries.orEmpty()))
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        enabled = !entries.isNullOrEmpty(),
                    ) { Text(shareTitle) }
                }
            }
            val loaded = entries
            if (loaded != null && loaded.isEmpty()) {
                item {
                    Text(stringResource(R.string.settings_diagnostics_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(loaded.orEmpty()) { entry ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = entry.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textHigh),
                        )
                    }
                }
            }
        }
    }
}
