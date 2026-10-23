package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.legal.TermsOfUse
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → About → Terms of use on TV: each section's title over its text, the text in a card
 * so the D-pad scrolls through them, as the licence texts. Focus starts on the first section.
 */
@Composable
fun TermsScreen() {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWithRetry() }

    LicenseList(rememberLazyListState()) {
        item {
            TvScreenHeader(title = stringResource(R.string.terms_title))
        }
        item {
            CinemaText(stringResource(R.string.terms_updated), role = TextRole.Body, tone = TextTone.Secondary)
        }
        itemsIndexed(TermsOfUse.sections) { index, (title, body) ->
            // The gap keeps the focused card, which grows, off its title.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(LocalUiScale.current))) {
                CinemaText(
                    stringResource(title),
                    role = TextRole.Heading,
                    tone = TextTone.Accent,
                    modifier = Modifier.padding(top = Spacing.md.scaled(LocalUiScale.current)),
                )
                LicenseCard(
                    onClick = {},
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                ) {
                    CinemaText(stringResource(body), role = TextRole.Body)
                }
            }
        }
    }
}
