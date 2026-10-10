package org.njarasoa.fijerena.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.njarasoa.fijerena.core.player.model.formatRating
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * One plain-text fact in a dot-separated detail meta line — see
 * docs/plans/archive/20260923_ui-ux-transitions-flow-uplift-plan.md, Phase 4 (3b).
 */
@Composable
fun MetaText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/**
 * One small outlined fact in a detail meta line (content rating, resolution) — outlined rather
 * than filled, so a one-letter rating ("R") reads as a badge, not a grey dot (phone UI audit, P4).
 */
@Composable
fun MetaBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textMedium),
        modifier =
            Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    shape = RoundedCornerShape(CinemaCornerRadius.small),
                ).padding(horizontal = CinemaSpacing.sm, vertical = CinemaSpacing.xxs),
    )
}

/**
 * A rating for a meta line: "8.2/10" when it's a number, the provider's own text otherwise.
 * Plain text, no star — a star is the favourite button's icon (as on TV, phone UI audit P4).
 */
@Composable
fun ratingOutOfTen(rating: String): String {
    val value = formatRating(rating)
    return if (value.toDoubleOrNull() != null) stringResource(R.string.details_rating_out_of_ten, value) else value
}

/**
 * A detail screen's facts, joined by dots. Wraps onto a second line rather than running off the
 * edge of a narrow phone (phone UI audit, #3); each dot travels with the fact after it.
 */
@Composable
fun MetaLine(
    segments: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = CinemaAlpha.textLow),
                    )
                }
                segment()
            }
        }
    }
}

/**
 * One line of a details screen's Overview tab: the label in a fixed column so every value starts
 * at the same place (as TV's TvDetailRow, phone UI audit #2). [value] is free-form for the
 * stream-name picker.
 */
@Composable
fun MobileDetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CinemaTextSecondary,
            modifier = Modifier.width(DETAIL_LABEL_WIDTH),
        )
        Spacer(Modifier.width(CinemaSpacing.sm))
        value()
    }
}

/** [MobileDetailRow] with plain text for its value. */
@Composable
fun MobileDetailRow(
    label: String,
    value: String,
) {
    MobileDetailRow(label = label) {
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = CinemaTextPrimary)
    }
}

/**
 * The Overview tab's "Category: 4k ›" — a row like the others with the category as a link to its
 * list, not a full-width button (phone UI audit, #2). Tall enough for a thumb.
 */
@Composable
fun MobileCategoryLinkRow(
    categoryName: String,
    onClick: () -> Unit,
) {
    MobileDetailRow(
        label = stringResource(R.string.details_label_category),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = MobileDimensions.iconXLarge)
                .padding(vertical = CinemaSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = CinemaIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
        }
    }
}

/** Wide enough for "Container:" / "Réalisateur :"; a longer label wraps under itself. */
private val DETAIL_LABEL_WIDTH = 112.dp

/**
 * The synopsis under the hero's actions, always on screen like the streaming apps show it: cut to
 * [collapsedLines] lines, a tap shows the rest (and a second tap folds it back). Only clickable
 * when there's more to show.
 */
@Composable
fun ExpandablePlot(
    plot: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 3,
) {
    var expanded by rememberSaveable(plot) { mutableStateOf(false) }
    var overflows by remember(plot) { mutableStateOf(false) }
    Text(
        text = plot,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!expanded) overflows = result.hasVisualOverflow
        },
        modifier =
            if (overflows || expanded) {
                modifier.clickable { expanded = !expanded }
            } else {
                modifier
            },
    )
}
