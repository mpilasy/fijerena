package org.njarasoa.fijerena.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Settings → About → Device info on mobile, shareable as plain text. See
 * the device info screen plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDeviceInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: DeviceInfoViewModel =
        viewModel { DeviceInfoViewModel(context.applicationContext, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.settings_diagnostics_share)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { CinemaText(stringResource(R.string.device_info_title), role = TextRole.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconAction(onClick = viewModel::reload, icon = CinemaIcons.Refresh, label = stringResource(R.string.common_refresh))
                    IconAction(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, DeviceInfoViewModel.asText(sections.orEmpty(), context))
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        },
                        icon = Icons.Rounded.Share,
                        label = shareTitle,
                        enabled = !sections.isNullOrEmpty(),
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(start = CinemaSpacing.md, top = CinemaSpacing.md, end = CinemaSpacing.md),
            contentPadding = PaddingValues(bottom = CinemaSpacing.md + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item {
                CinemaText(stringResource(R.string.device_info_desc), tone = TextTone.Secondary)
            }
            if (failed) {
                item {
                    CinemaText(stringResource(R.string.device_info_load_failed), tone = TextTone.Danger)
                }
            }
            items(sections.orEmpty()) { section ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                        CinemaText(stringResource(section.title), role = TextRole.Heading, tone = TextTone.Primary)
                        section.rows.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                                CinemaText(
                                    row.label.asString(),
                                    role = TextRole.BodySmall,
                                    tone = TextTone.Secondary,
                                    modifier = Modifier.weight(LABEL_WEIGHT),
                                )
                                CinemaText(row.value.asString(), role = TextRole.BodySmall, modifier = Modifier.weight(VALUE_WEIGHT))
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
