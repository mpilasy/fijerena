package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * The one TV screen header (TV UI audit, X6): [title] with an optional one-line [subtitle] on the
 * left, the screen's icon [actions] on the right, all on one row. Same title style and size as
 * Settings' "Settings" (`displaySmall`), and the same gap ([Spacing.lg]) to the content below, so
 * every screen's header sits and reads the same.
 *
 * It adds no safe-area padding of its own: put it first inside the screen's root, which carries
 * `Spacing.tvSafeMarginHorizontal` / `tvSafeMarginVertical` as every TV screen does.
 *
 * [actions] holds the screen's icon buttons ([org.njarasoa.fijerena.ui.components.buttons.TvIconAction],
 * which name themselves while focused), spaced for you; never text buttons. [leading] is for what
 * sits before the title — the section-root button, say — and is usually left out. The header
 * itself is not focusable: focus moves between the buttons in it, and the screen decides how
 * Down / Up cross between the header and its content (AGENTS.md → focus contract).
 *
 * ```
 * Column(
 *     Modifier
 *         .fillMaxSize()
 *         .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
 * ) {
 *     TvScreenHeader(
 *         title = stringResource(R.string.provider_selection_title),
 *         subtitle = activeSourceName,
 *     ) {
 *         TvIconAction(onClick = onAdd, icon = CinemaIcons.Add, label = stringResource(R.string.provider_add_title))
 *         TvIconAction(onClick = onRefresh, icon = CinemaIcons.Refresh, label = stringResource(R.string.common_refresh))
 *     }
 *     // … the screen's content
 * }
 * ```
 */
@Composable
fun TvScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = leading,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = CinemaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextSecondary.copy(alpha = CinemaAlpha.textHigh),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
