package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.legal.TermsOfUse
import org.njarasoa.fijerena.core.ui.licenses.OpenSourceEntry
import org.njarasoa.fijerena.core.ui.licenses.OpenSourceLicenses
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.R as AppR

/** The app's libraries and fonts, null until read. */
@Composable
private fun rememberEntries(): List<OpenSourceEntry>? {
    val context = LocalContext.current
    val entries by produceState<List<OpenSourceEntry>?>(null) { value = OpenSourceLicenses.load(context, AppR.raw.aboutlibraries) }
    return entries
}

/** "1.13.0 · Apache License 2.0": an entry's version and licences, for its row. */
fun OpenSourceEntry.summary(): String = (listOfNotNull(version) + licenses.map { it.name }.distinct()).joinToString(" · ")

/**
 * Settings → About → Open-source licences on the phone: every library and font the app ships, by
 * name, each with its version and licences; a row opens its licence texts ([onOpen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLicensesScreen(
    onOpen: (entryId: String) -> Unit,
    onBack: () -> Unit,
) {
    val entries = rememberEntries()
    LicensesScaffold(stringResource(R.string.licenses_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = CinemaSpacing.md + LocalDockInset.current),
        ) {
            item {
                CinemaText(
                    stringResource(R.string.licenses_desc) +
                        entries?.let { "\n" + stringResource(R.string.licenses_count, it.size) }.orEmpty(),
                    tone = TextTone.Secondary,
                    modifier = Modifier.padding(CinemaSpacing.md),
                )
            }
            items(entries.orEmpty(), key = { it.id }) { entry ->
                SettingsListRow(title = entry.name, summary = entry.summary(), onClick = { onOpen(entry.id) })
            }
        }
    }
}

/** One entry's licence texts, each under its name; a licence with no bundled text names its address. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLicenseDetailScreen(
    entryId: String,
    onBack: () -> Unit,
) {
    val entry = rememberEntries()?.firstOrNull { it.id == entryId }
    LicensesScaffold(entry?.name.orEmpty(), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = CinemaSpacing.md),
            contentPadding = PaddingValues(top = CinemaSpacing.md, bottom = CinemaSpacing.md + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            entry?.version?.let { version -> item { CinemaText(version, tone = TextTone.Secondary) } }
            items(entry?.licenses.orEmpty()) { license ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                        CinemaText(license.name, role = TextRole.Heading, tone = TextTone.Accent)
                        CinemaText(
                            license.content.ifBlank { stringResource(R.string.licenses_no_text, license.url.orEmpty()) },
                            role = TextRole.BodySmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { CinemaText(title, role = TextRole.Title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CinemaIcons.ArrowBack, stringResource(R.string.common_back))
                    }
                },
            )
        },
        content = content,
    )
}

/** Settings → About → Terms of use on the phone: each section's title over its text, in a card. */
@Composable
fun MobileTermsScreen(onBack: () -> Unit) {
    LicensesScaffold(stringResource(R.string.terms_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = CinemaSpacing.md),
            contentPadding = PaddingValues(top = CinemaSpacing.md, bottom = CinemaSpacing.md + LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
        ) {
            item { CinemaText(stringResource(R.string.terms_updated), tone = TextTone.Secondary) }
            items(TermsOfUse.sections) { (title, body) ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CinemaSpacing.md), verticalArrangement = Arrangement.spacedBy(CinemaSpacing.sm)) {
                        CinemaText(stringResource(title), role = TextRole.Heading, tone = TextTone.Accent)
                        CinemaText(stringResource(body))
                    }
                }
            }
        }
    }
}
