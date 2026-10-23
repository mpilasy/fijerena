package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.licenses.OpenSourceEntry
import org.njarasoa.fijerena.core.ui.licenses.OpenSourceLicenses
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.CornerRadius
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.R as AppR

/** The app's libraries and fonts, null until read. */
@Composable
private fun rememberEntries(): List<OpenSourceEntry>? {
    val context = LocalContext.current
    val entries by produceState<List<OpenSourceEntry>?>(null) { value = OpenSourceLicenses.load(context, AppR.raw.aboutlibraries) }
    return entries
}

/**
 * Settings → About → Open-source licences on TV: every library and font the app ships, by name,
 * each with its version and licences; OK opens its licence texts ([onOpen]). Focus starts on the
 * first entry and comes back to the one opened.
 */
@Composable
fun LicensesScreen(onOpen: (entryId: String) -> Unit) {
    val entries = rememberEntries()
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(entries != null) {
        if (!entries.isNullOrEmpty()) firstFocus.requestFocusWithRetry()
    }
    NavReturnFocusEffect(returnFocus, listState = listState, fallback = firstFocus)

    LicenseList(listState) {
        item {
            TvScreenHeader(title = stringResource(R.string.licenses_title))
        }
        item {
            val count = entries?.let { " " + stringResource(R.string.licenses_count, it.size) }.orEmpty()
            CinemaText(stringResource(R.string.licenses_desc) + "." + count, role = TextRole.Body, tone = TextTone.Secondary)
        }
        itemsIndexed(entries.orEmpty(), key = { _, entry -> entry.id }) { index, entry ->
            LicenseCard(
                onClick = {
                    returnFocus.leaveFrom(entry.id, listState)
                    onOpen(entry.id)
                },
                modifier =
                    Modifier
                        .navReturnFocusTarget(returnFocus, entry.id)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
            ) {
                CinemaText(entry.name, role = TextRole.Title)
                CinemaText(
                    (listOfNotNull(entry.version) + entry.licenses.map { it.name }.distinct()).joinToString(" · "),
                    role = TextRole.Body,
                    tone = TextTone.Secondary,
                )
            }
        }
    }
}

/**
 * One entry's licence texts on TV, each under its name, a paragraph per card so the D-pad scrolls
 * through them; a licence with no bundled text names its address.
 */
@Composable
fun LicenseDetailScreen(entryId: String) {
    val entry = rememberEntries()?.firstOrNull { it.id == entryId }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(entry != null) {
        if (entry != null) firstFocus.requestFocusWithRetry()
    }
    // Paragraphs, not whole licences: a card taller than the screen can't be scrolled through.
    val paragraphs =
        remember(entry) {
            entry?.licenses.orEmpty().flatMap { license ->
                listOf(license.name to true) +
                    license.content
                        .split(Regex("\\n\\s*\\n"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf("") }
                        .map { it to false }
            }
        }
    val noText = entry?.licenses?.firstOrNull { it.content.isBlank() }?.let { stringResource(R.string.licenses_no_text, it.url.orEmpty()) }

    LicenseList(rememberLazyListState()) {
        item {
            TvScreenHeader(title = entry?.name.orEmpty())
        }
        entry?.version?.let { version -> item { CinemaText(version, role = TextRole.Body, tone = TextTone.Secondary) } }
        itemsIndexed(paragraphs) { index, (text, heading) ->
            if (heading) {
                CinemaText(
                    text,
                    role = TextRole.Heading,
                    tone = TextTone.Accent,
                    modifier = Modifier.padding(top = Spacing.md.scaled(LocalUiScale.current)),
                )
            } else {
                LicenseCard(
                    onClick = {},
                    modifier = if (index == 1) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    CinemaText(text.ifEmpty { noText.orEmpty() }, role = TextRole.Body)
                }
            }
        }
    }
}

@Composable
internal fun LicenseList(
    listState: LazyListState,
    content: LazyListScope.() -> Unit,
) {
    val scale = LocalUiScale.current
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.scaled(scale)),
        content = content,
    )
}

/** A focusable card as Device info's sections: the standard focus border, Left to the rail. */
@Composable
internal fun LicenseCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = LocalUiScale.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().leftToRail(),
        shape = ClickableSurfaceDefaults.shape(shape = cornerShape(CornerRadius.large)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = TvFocusTokens.restingContainer,
                focusedContainerColor = TvFocusTokens.focusedContainer,
                pressedContainerColor = TvFocusTokens.focusedContainer,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusTokens.defaultScale),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth, TvFocusTokens.focusedRowOutline)),
            ),
    ) {
        Column(Modifier.padding(Spacing.md.scaled(scale)), verticalArrangement = Arrangement.spacedBy(Spacing.xs.scaled(scale))) {
            content()
        }
    }
}
