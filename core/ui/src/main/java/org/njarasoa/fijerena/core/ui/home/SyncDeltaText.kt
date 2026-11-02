package org.njarasoa.fijerena.core.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import org.njarasoa.fijerena.core.ui.R

/**
 * The last catalogue sync's changes: "3 added • 1 updated • 0 removed". Each count agrees with its
 * word ("1 ajouté", "3 ajoutés"), so each part is a plural of its own, joined in code.
 */
@Composable
fun syncDeltaText(
    inserted: Int,
    updated: Int,
    deleted: Int,
): String =
    listOf(
        pluralStringResource(R.plurals.provider_sync_added, inserted, inserted),
        pluralStringResource(R.plurals.provider_sync_updated, updated, updated),
        pluralStringResource(R.plurals.provider_sync_removed, deleted, deleted),
    ).joinToString(" • ")
