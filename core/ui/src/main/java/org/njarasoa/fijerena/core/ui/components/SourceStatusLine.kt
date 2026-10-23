package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.viewmodels.SourceStatus

/**
 * The Sources list's line under a source's name: "Xtream IPTV · Expires Apr 11, 2027 · Max
 * connections 1", then "Trial account" and "Not used by this profile" when they apply.
 */
@Composable
fun sourceStatusLine(status: SourceStatus): String =
    listOfNotNull(
        status.type,
        status.expiry?.let { stringResource(R.string.settings_provider_expires_label) + " " + it },
        status.maxConnections?.let { stringResource(R.string.settings_provider_max_connections_label) + " " + it },
        stringResource(R.string.settings_provider_trial_account_label).takeIf { status.isTrial },
        stringResource(R.string.provider_not_in_profile).takeIf { !status.inProfile },
    ).joinToString(" · ")
