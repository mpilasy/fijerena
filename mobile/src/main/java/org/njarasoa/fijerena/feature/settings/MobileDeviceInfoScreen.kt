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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaOutlinedButton

/**
 * Settings → About → Device info on mobile, shareable as plain text. See
 * docs/plans/archive/20261004_device-info-screen-plan.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDeviceInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DeviceInfoViewModel =
        viewModel { DeviceInfoViewModel(context.applicationContext, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.settings_diagnostics_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_info_title)) },
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
                    text = stringResource(R.string.device_info_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                    CinemaOutlinedButton(onClick = viewModel::reload) { Text(stringResource(R.string.common_refresh)) }
                    CinemaOutlinedButton(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, DeviceInfoViewModel.asText(sections.orEmpty(), context))
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        enabled = !sections.isNullOrEmpty(),
                    ) { Text(shareTitle) }
                }
            }
            items(sections.orEmpty()) { section ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        Text(
                            stringResource(section.title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        section.rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                Text(
                                    text = row.label.asString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
                                    modifier = Modifier.weight(LABEL_WEIGHT),
                                )
                                Text(
                                    text = row.value.asString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textHigh),
                                    modifier = Modifier.weight(VALUE_WEIGHT),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
