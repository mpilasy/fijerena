package org.njarasoa.fijerena.feature.person

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.person.appearanceLabel
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.cards.TvCard
import org.njarasoa.fijerena.ui.components.cards.TvCardLooks
import org.njarasoa.fijerena.ui.components.input.NavReturnFocus
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing

/**
 * A person's appearances in one show, over the person page (docs/plans/20261030_episode-cast-plan.md,
 * EC-D2): one row per episode ("S9E17 · Authority") or per season ("Season 3"), a row the source
 * lacks marked "Not in your source". OK on a row calls [onSelect]; Back calls [onClose], and the
 * person page gives focus back to the show card. An in-tree overlay: focus stays in it.
 *
 * Focus: the first row on opening; Back from the show page returns to the row left ([returnFocus]).
 */
@Composable
fun ShowAppearancesOverlay(
    title: String,
    subtitle: String?,
    lines: List<ShowAppearance>,
    returnFocus: NavReturnFocus,
    onSelect: (ShowAppearance) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (returnFocus.key?.startsWith(RETURN_LINE_PREFIX) != true) firstRow.requestFocusWithRetry()
    }
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(CinemaTheme.colors.background)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onClose()
                        true
                    } else {
                        false
                    }
                }.focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
        contentPadding = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "header") { TvScreenHeader(title = title, subtitle = subtitle) }
        itemsIndexed(lines, key = { _, line -> lineKey(line) }) { index, line ->
            val key = RETURN_LINE_PREFIX + lineKey(line)
            TvCard(
                onClick = {
                    returnFocus.leaveFrom(key)
                    onSelect(line)
                },
                look = TvCardLooks.listRow(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navReturnFocusTarget(returnFocus, key)
                        .then(if (index == 0) Modifier.focusRequester(firstRow) else Modifier),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CinemaText(
                        text = appearanceLabel(resources, line),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!line.inSource) {
                        CinemaText(text = stringResource(R.string.person_missing_mark), tone = TextTone.Secondary)
                    }
                }
            }
        }
    }
}

private fun lineKey(line: ShowAppearance): String = "${line.season}:${line.episode ?: ""}"

// Keys for the rows that open a show page — see rememberNavReturnFocus.
const val RETURN_LINE_PREFIX = "appearance:"
