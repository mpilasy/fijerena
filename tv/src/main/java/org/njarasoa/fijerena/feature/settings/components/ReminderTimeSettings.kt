package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.sports.ReminderSchedule
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.sports.reminderLeadLabel

/** Settings → Admin → This Device → Reminder Time: how long before the start a reminder rings here. */
@Composable
fun ReminderTimeSettingsRow(
    leadMinutes: Int,
    onOpenPicker: () -> Unit,
    rowFocusRequester: FocusRequester? = null,
) {
    SettingsRow(
        title = stringResource(R.string.reminders_lead_title),
        description = stringResource(R.string.reminders_lead_desc),
        value = reminderLeadLabel(LocalContext.current.resources, leadMinutes),
        onClick = onOpenPicker,
        focusRequester = rowFocusRequester,
    )
}

/** The Reminder Time choices (the Reminders page plan, RP-D11). */
@Composable
fun ReminderTimePickerPane(
    leadMinutes: Int,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    SettingsPickerPane(
        title = stringResource(R.string.reminders_lead_title),
        options = ReminderSchedule.LEAD_CHOICES_MINUTES.map { PickerOption(reminderLeadLabel(resources, it), it) },
        selectedValue = leadMinutes,
        onPick = onPick,
        onBack = onBack,
    )
}
