@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing

/*
 * The one Settings layout (TV UI audit #17): a section is a title and a description over its rows;
 * a row is its title and a short description on the left, its value and a chevron on the right,
 * so every value sits in the same column. Where a setting applies (this device, this source) is
 * said once, in the section's description, not on every row.
 *
 * Type: section title `titleMedium` in the accent, row title `titleSmall`, everything secondary
 * `bodyMedium` — the same as the switch rows (`TvSwitchRow`) they sit among.
 */

/** A group of rows under a title (optional) and a description (optional); no container of its own. */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = CinemaAccent)
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
            )
        }
        if (title != null || description != null) Spacer(modifier = Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
    }
}

/** Secondary text under a section's rows (a result, a last-run line): the rows' description style. */
@Composable
fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
    alpha: Float = CinemaAlpha.textHigh,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = CinemaTextSecondary.copy(alpha = alpha),
        modifier = modifier,
    )
}

/**
 * One focusable settings row: [title] and a short [description] on the left, [value] and a
 * chevron on the right (`Theme · Deep Night ›`); without a [value], the chevron alone. OK runs
 * [onClick] — it opens the row's picker or page, it never changes the value itself (plan Part I,
 * B, T-6). [leading] holds an avatar or an icon; [supporting] replaces the description when the
 * row needs more than one line (the source in use, the app's build).
 */
@Composable
fun SettingsRow(
    title: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    chevron: Boolean = true,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
) {
    val trailing = listOfNotNull(value, if (chevron) "›" else null).joinToString(" ")
    TvInputListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
        enabled = enabled,
        leadingContent = leading,
        trailingContent =
            if (trailing.isEmpty()) {
                null
            } else {
                { Text(text = trailing, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
            },
        supportingContent =
            supporting ?: description?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                        maxLines = DESCRIPTION_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
    }
}

/** A row's description wraps once (Edit Source's narrower column), then ends in "…". */
private const val DESCRIPTION_MAX_LINES = 2
