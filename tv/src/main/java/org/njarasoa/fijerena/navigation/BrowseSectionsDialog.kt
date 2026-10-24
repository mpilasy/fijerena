package org.njarasoa.fijerena.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSurface
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.input.TvInputListItem
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A "Browse <source>" entry's sections, when it has more than one (docs/plans/20261012_multi-source-search-history-plan.md
 * → O1): one row per section, focus on the first; OK opens it on that source, Back closes the
 * dialog (focus contract: a vertical list, the first useful item focused, Cancel last).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BrowseSectionsDialog(
    source: BrowseSource,
    contentTypes: List<String>,
    onPick: (contentType: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CinemaText(text = stringResource(R.string.browse_source, source.name), role = TextRole.Heading, maxLines = 2)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                contentTypes.forEachIndexed { index, contentType ->
                    TvInputListItem(
                        selected = false,
                        onClick = { onPick(contentType) },
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        leadingContent = {
                            Icon(
                                imageVector = sectionIcon(contentType),
                                contentDescription = null,
                                modifier = Modifier.size(TvDimensions.iconSmall),
                                tint = CinemaTheme.colors.textPrimary,
                            )
                        },
                        headlineContent = {
                            CinemaText(text = sectionLabel(contentType), role = TextRole.Heading)
                        },
                    )
                }
                TvInputListItem(
                    selected = false,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = CinemaIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(TvDimensions.iconSmall),
                            tint = CinemaTheme.colors.textSecondary,
                        )
                    },
                    headlineContent = {
                        CinemaText(text = stringResource(R.string.common_cancel), tone = TextTone.Secondary)
                    },
                )
            }
        },
        initialFocus = firstFocus,
        confirmButton = {},
        containerColor = CinemaTheme.colors.surface,
        titleContentColor = CinemaTheme.colors.textPrimary,
        textContentColor = CinemaTheme.colors.textSecondary,
    )
}

@Composable
private fun sectionIcon(contentType: String): ImageVector =
    when (contentType) {
        ContentType.LIVE_TV -> CinemaIcons.LiveTv
        ContentType.MOVIES -> CinemaIcons.Movie
        else -> CinemaIcons.Tv
    }

@Composable
private fun sectionLabel(contentType: String): String =
    when (contentType) {
        ContentType.LIVE_TV -> stringResource(R.string.provider_live_tv_label)
        ContentType.MOVIES -> stringResource(R.string.provider_movies_label)
        else -> stringResource(R.string.provider_tv_shows_label)
    }
