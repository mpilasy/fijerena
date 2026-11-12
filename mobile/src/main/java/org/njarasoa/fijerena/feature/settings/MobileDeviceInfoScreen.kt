package org.njarasoa.fijerena.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoAction
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoRow
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.MobileScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.IconAction

/**
 * Settings → About → Device info on mobile, shareable as plain text. A row with an action (the
 * image cache's Clear Image Cache) has its icon at the row's end, with its name in a tooltip; it
 * asks before it runs. See the device info screen plan and the low storage plan → LS-Q3.
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
    val clearedText = stringResource(R.string.device_info_image_cache_cleared)
    val clearFailedText = stringResource(R.string.device_info_image_cache_clear_failed)
    var confirming by remember { mutableStateOf<DeviceInfoAction?>(null) }

    Scaffold(
        topBar = {
            MobileScreenHeader(
                title = stringResource(R.string.device_info_title),
                onBack = onBack,
            ) {
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
            }
        },
    ) { paddingValues ->
        // The Settings page look: a group heading over each section and its rows on the page,
        // not bordered cards.
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(top = CinemaSpacing.md, bottom = CinemaSpacing.md + LocalDockInset.current),
        ) {
            item {
                CinemaText(
                    stringResource(R.string.device_info_desc),
                    tone = TextTone.Secondary,
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md),
                )
            }
            if (failed) {
                item {
                    CinemaText(
                        stringResource(R.string.device_info_load_failed),
                        tone = TextTone.Danger,
                        modifier = Modifier.padding(horizontal = CinemaSpacing.md),
                    )
                }
            }
            items(sections.orEmpty()) { section ->
                SettingsGroupHeader(stringResource(section.title))
                Column(
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    section.rows.forEach { row -> DeviceInfoRowLine(row, onAction = { confirming = it }) }
                }
            }
        }
    }

    if (confirming != null) {
        CinemaAlertDialog(
            onDismissRequest = { confirming = null },
            title = stringResource(R.string.device_info_clear_image_cache_title),
            text = { CinemaText(stringResource(R.string.device_info_clear_image_cache_message), tone = TextTone.Secondary) },
            danger =
                DialogAction(stringResource(R.string.provider_clear_button)) {
                    confirming = null
                    viewModel.clearImageCache { cleared -> AppNotices.show(if (cleared) clearedText else clearFailedText) }
                },
            cancel = DialogAction(stringResource(R.string.common_cancel)) { confirming = null },
        )
    }
}

/**
 * One Device Info row: the label, then the value. A row's action (the image cache's Clear Image
 * Cache) sits over the row's end, so its label and value columns line up with the other rows'.
 */
@Composable
internal fun DeviceInfoRowLine(
    row: DeviceInfoRow,
    onAction: (DeviceInfoAction) -> Unit,
) {
    Box(contentAlignment = Alignment.CenterStart) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CinemaText(
                row.label.asString(),
                role = TextRole.BodySmall,
                tone = TextTone.Secondary,
                modifier = Modifier.weight(LABEL_WEIGHT),
            )
            CinemaText(
                row.value.asString(),
                role = TextRole.BodySmall,
                modifier =
                    Modifier
                        .weight(VALUE_WEIGHT)
                        // A long value wraps before the icon instead of running under it.
                        .then(if (row.action != null) Modifier.padding(end = LocalMinimumInteractiveComponentSize.current) else Modifier),
            )
        }
        row.action?.let { action ->
            // Its own box: the tooltip's anchor does not pass the alignment on.
            Box(Modifier.align(Alignment.CenterEnd)) {
                IconAction(
                    onClick = { onAction(action) },
                    icon = CinemaIcons.ClearData,
                    label = stringResource(actionLabel(action)),
                )
            }
        }
    }
}

private fun actionLabel(action: DeviceInfoAction): Int =
    when (action) {
        DeviceInfoAction.CLEAR_IMAGE_CACHE -> R.string.device_info_clear_image_cache
    }

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
