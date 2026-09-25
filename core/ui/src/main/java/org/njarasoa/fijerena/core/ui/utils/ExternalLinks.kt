package org.njarasoa.fijerena.core.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import org.njarasoa.fijerena.core.ui.R

/**
 * Hands [url] to whatever app claims it — the YouTube app on a TV box, a browser on a phone.
 * A TV with neither installed is a real case, so the failure is reported rather than thrown.
 */
fun openExternalUrl(
    context: Context,
    url: String,
) {
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.details_no_link_handler), Toast.LENGTH_LONG).show()
    }
}

/** Whether any app on the device can handle a calendar-event insert intent. */
fun canOpenCalendar(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
    return intent.resolveActivity(context.packageManager) != null
}

/**
 * Hands an EPG airing to whatever calendar app is installed, pre-filled as a new event.
 * TV boxes commonly have no calendar app at all, so the failure is reported rather than thrown.
 */
fun openAddToCalendarEvent(
    context: Context,
    title: String,
    description: String?,
    location: String,
    startEpochSeconds: Long,
    endEpochSeconds: Long,
) {
    val intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description ?: "")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochSeconds * 1000L)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpochSeconds * 1000L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.epg_browser_no_calendar_app), Toast.LENGTH_LONG).show()
    }
}
