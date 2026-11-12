package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoAction
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoSection
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.viewmodels.DeviceInfoViewModel
import org.njarasoa.fijerena.feature.provider.components.ConfirmActionDialog
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.buttons.TvButtonStyle
import org.njarasoa.fijerena.ui.components.buttons.TvIconAction
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled
import kotlin.math.min

/**
 * Settings → About → Device info on TV. Each section is one focus stop so the D-pad scrolls
 * through them, as on Diagnostics. A row with an action (the image cache's Clear Image Cache)
 * has its icon in the slot at the right of its section, on the row's line: Right reaches it, and
 * it asks before it runs. See the device info screen plan and the low storage plan → LS-Q3.
 */
@Composable
fun DeviceInfoScreen() {
    val context = LocalContext.current
    val viewModel: DeviceInfoViewModel =
        viewModel { DeviceInfoViewModel(context.applicationContext, BuildConfig.GIT_HASH, BuildConfig.BUILD_TIME) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val clearedText = stringResource(R.string.device_info_image_cache_cleared)
    val clearFailedText = stringResource(R.string.device_info_image_cache_clear_failed)
    val actionFocus = remember { FocusRequester() }
    var confirming by remember { mutableStateOf<DeviceInfoAction?>(null) }
    var backToAction by remember { mutableStateOf(false) }

    // Once the confirmation closes, focus goes back to the action that opened it.
    LaunchedEffect(confirming) {
        if (confirming == null && backToAction) {
            actionFocus.requestFocusWithRetry()
            backToAction = false
        }
    }

    DeviceInfoContent(
        sections = sections,
        failed = failed,
        onRefresh = viewModel::reload,
        onAction = { confirming = it },
        actionFocus = actionFocus,
    )

    if (confirming != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.device_info_clear_image_cache_title),
            text = stringResource(R.string.device_info_clear_image_cache_message),
            confirmText = stringResource(R.string.provider_clear_button),
            onConfirm = {
                confirming = null
                backToAction = true
                viewModel.clearImageCache { cleared -> AppNotices.show(if (cleared) clearedText else clearFailedText) }
            },
            onDismiss = {
                confirming = null
                backToAction = true
            },
        )
    }
}

/**
 * The page without its view model, for the focus test. Every section has a slot of one icon's
 * width at its right, so that the cards keep one width; the slot holds the section's action, if it
 * has one. The focused action's label grows to the left over the card's empty end.
 */
@Composable
internal fun DeviceInfoContent(
    sections: List<DeviceInfoSection>?,
    failed: Boolean,
    onRefresh: () -> Unit,
    onAction: (DeviceInfoAction) -> Unit,
    actionFocus: FocusRequester,
) {
    val scale = LocalUiScale.current

    // Entry focus on the first section, or Refresh when none loaded; else the screen opened with nothing
    // focused. Once, when the sections first load (or fail).
    val firstSectionFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val loadedOnce = sections != null || failed
    LaunchedEffect(loadedOnce) {
        if (loadedOnce) {
            if (sections.isNullOrEmpty()) {
                refreshFocus.requestFocusWithRetry()
            } else {
                firstSectionFocus.requestFocusWithRetry(
                    fallback = refreshFocus,
                )
            }
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
    ) {
        item {
            TvScreenHeader(title = stringResource(R.string.device_info_title)) {
                TvIconAction(
                    onClick = onRefresh,
                    icon = CinemaIcons.Refresh,
                    label = stringResource(R.string.common_refresh),
                    modifier = Modifier.focusRequester(refreshFocus),
                )
            }
        }
        item {
            CinemaText(
                stringResource(R.string.device_info_desc),
                role = TextRole.Body,
                tone = TextTone.Secondary,
            )
        }
        if (failed) {
            item {
                CinemaText(
                    stringResource(R.string.device_info_load_failed),
                    role = TextRole.Body,
                    tone = TextTone.Danger,
                )
            }
        }
        itemsIndexed(sections.orEmpty()) { index, section ->
            val action = section.rows.firstNotNullOfOrNull { it.action }
            val slotWidth = TvButtonStyle.iconButtonSize.scaled(scale)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale)),
            ) {
                // A clickable card that does nothing, so each section takes D-pad focus (and the list scrolls to it).
                TvCard(
                    onClick = {},
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstSectionFocus) else Modifier)
                            .then(if (action != null) Modifier.alignBy(ActionRowMiddle) else Modifier)
                            .leftToRail(),
                    look = TvCardLooks.infoBlock(),
                ) {
                    Column(
                        Modifier.padding(Spacing.md.scaled(scale)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale)),
                    ) {
                        CinemaText(stringResource(section.title), role = TextRole.Heading, tone = TextTone.Primary)
                        section.rows.forEach { row ->
                            Row(
                                modifier = if (row.action != null) Modifier.markActionRowMiddle() else Modifier,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
                            ) {
                                CinemaText(
                                    row.label.asString(),
                                    modifier = Modifier.weight(LABEL_WEIGHT),
                                    role = TextRole.Body,
                                    tone = TextTone.Secondary,
                                )
                                CinemaText(
                                    row.value.asString(),
                                    modifier = Modifier.weight(VALUE_WEIGHT),
                                    role = TextRole.Body,
                                    tone = TextTone.Primary,
                                )
                            }
                        }
                    }
                }
                if (action != null) {
                    Box(
                        modifier = Modifier.width(slotWidth).alignBy { it.measuredHeight / 2 },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        TvIconAction(
                            onClick = { onAction(action) },
                            icon = CinemaIcons.ClearData,
                            label = stringResource(actionLabel(action)),
                            modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true).focusRequester(actionFocus),
                        )
                    }
                } else {
                    Spacer(Modifier.width(slotWidth))
                }
            }
        }
    }
}

private fun actionLabel(action: DeviceInfoAction): Int =
    when (action) {
        DeviceInfoAction.CLEAR_IMAGE_CACHE -> R.string.device_info_clear_image_cache
    }

/** The middle of the row that has an action; its section's slot puts the action's icon on it. */
private val ActionRowMiddle = HorizontalAlignmentLine(::min)

/** Gives [ActionRowMiddle] at this row's middle. The card and its column pass it up to the section's row. */
private fun Modifier.markActionRowMiddle(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height, mapOf(ActionRowMiddle to placeable.height / 2)) {
            placeable.place(0, 0)
        }
    }

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
