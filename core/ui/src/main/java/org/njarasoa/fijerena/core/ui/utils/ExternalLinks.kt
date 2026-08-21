package org.njarasoa.fijerena.core.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
