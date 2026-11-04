package org.njarasoa.fijerena.core.ui.sports

import android.content.res.Resources
import org.njarasoa.fijerena.core.ui.R

/** A reminder time choice: "At the start", or "10 minutes before" (the Reminders page plan, RP-D11). */
fun reminderLeadLabel(
    resources: Resources,
    minutes: Int,
): String =
    if (minutes == 0) {
        resources.getString(R.string.reminders_lead_at_start)
    } else {
        resources.getQuantityString(R.plurals.reminders_lead_minutes, minutes, minutes)
    }
