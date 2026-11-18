package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.legal.AppDownload
import org.njarasoa.fijerena.core.ui.legal.ProjectLink
import org.njarasoa.fijerena.core.ui.legal.QrLink
import org.njarasoa.fijerena.core.ui.legal.aboutCommitLine
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.cornerShape
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import org.njarasoa.fijerena.core.ui.utils.rememberAdminTapHandler
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.SettingsGroupHeader
import org.njarasoa.fijerena.feature.settings.components.SettingsListRow
import org.njarasoa.fijerena.feature.settings.components.TmdbAttribution
import org.njarasoa.fijerena.navigation.LocalDockInset
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * Settings → About → About Fijerena on the phone: the version (7 taps turn Admin on), the
 * no-content notice, the copyright and licence notice and the build; the project's links, each
 * opening its QR code and an Open in Browser button; Get the App: the Obtainium badges of the phone
 * and TV apps and the GitHub badge, each opening its QR code and link the same way; Terms of Use and
 * Open-Source Licences; the TMDB and sports data credits.
 */
@Composable
fun MobileAboutScreen(
    onTerms: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onVersionClick = rememberAdminTapHandler(uiState.adminExists, viewModel::enableAdmin)
    var shownLink by remember { mutableStateOf<QrLink?>(null) }

    LicensesScaffold(stringResource(R.string.about_title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = CinemaSpacing.md + LocalDockInset.current),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // look-ok: the About text: seven taps turn Admin on, not a list row
                            .clickable(onClick = onVersionClick)
                            .padding(start = CinemaSpacing.md, end = CinemaSpacing.md, top = CinemaSpacing.md, bottom = CinemaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
                ) {
                    // The version, then its commit and the commit's date, as on TV.
                    CinemaText(stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME), role = TextRole.Heading)
                    CinemaText(
                        aboutCommitLine(BuildConfig.GIT_HASH, BuildConfig.COMMIT_TIME),
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                    CinemaText(stringResource(R.string.app_disclaimer_no_content))
                    // GPL-3.0 "Appropriate Legal Notices": copyright, licence, no warranty, source.
                    CinemaText(stringResource(R.string.app_copyright), role = TextRole.BodySmall, tone = TextTone.Secondary)
                    CinemaText(stringResource(R.string.app_license_notice), role = TextRole.BodySmall, tone = TextTone.Secondary)
                }
            }
            item {
                SettingsGroupHeader(stringResource(R.string.about_links_title))
                ProjectLink.entries.forEach { link ->
                    SettingsListRow(
                        title = stringResource(link.titleRes),
                        summary = stringResource(link.descriptionRes) + "\n" + link.shownUrl,
                        onClick = { shownLink = link },
                    )
                }
            }
            item {
                SettingsGroupHeader(stringResource(R.string.about_get_app_title))
                // The badges, as in the README: each shows its QR code, for another phone to scan,
                // and its link (Open in Browser).
                Row(
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.sm),
                ) {
                    AppDownload.entries.forEach { download ->
                        AppBadge(download) { shownLink = download }
                    }
                }
            }
            item {
                SettingsGroupHeader(stringResource(R.string.about_legal_title))
                SettingsListRow(
                    title = stringResource(R.string.terms_title),
                    summary = stringResource(R.string.terms_desc),
                    onClick = onTerms,
                )
                SettingsListRow(
                    title = stringResource(R.string.licenses_title),
                    summary = stringResource(R.string.licenses_desc),
                    onClick = onLicenses,
                )
            }
            item {
                SettingsGroupHeader(stringResource(R.string.about_credits_title))
                // Each credit under its name, as on TV.
                CinemaText(
                    stringResource(R.string.tmdb_attribution_title),
                    modifier = Modifier.padding(start = CinemaSpacing.md, end = CinemaSpacing.md, top = CinemaSpacing.xs),
                )
                TmdbAttribution()
                Column(
                    modifier = Modifier.padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
                ) {
                    CinemaText(stringResource(R.string.sports_attribution_title))
                    CinemaText(stringResource(R.string.sports_attribution), role = TextRole.BodySmall, tone = TextTone.Secondary)
                }
            }
        }
    }

    shownLink?.let { link ->
        CinemaAlertDialog(
            onDismissRequest = { shownLink = null },
            // A badge's QR code is titled with what it gets ("TV App"): its row title takes two
            // lines here and pushed the hint out of the dialog. The address names Obtainium.
            title = stringResource(if (link is AppDownload) link.captionRes else link.titleRes),
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md),
                ) {
                    QrCode(
                        text = link.url,
                        size = MobileDimensions.qrCodeSize,
                        contentDescription = stringResource(R.string.about_qr_description, link.shownUrl),
                    )
                    CinemaText(link.shownUrl)
                    CinemaText(stringResource(R.string.about_qr_hint), role = TextRole.BodySmall, tone = TextTone.Secondary)
                }
            },
            main =
                DialogAction(stringResource(R.string.about_open_link)) {
                    shownLink = null
                    openExternalUrl(context, link.url)
                },
            cancel = DialogAction(stringResource(R.string.common_close)) { shownLink = null },
        )
    }
}

/** A "Get it on" badge, with what it gets under it ("Phone App"). */
@Composable
private fun RowScope.AppBadge(
    download: AppDownload,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .clip(cornerShape(CinemaSpacing.xs))
                // look-ok: a store badge is the button, as on its store's own pages
                .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xxs),
    ) {
        Image(
            painter = painterResource(download.badge),
            contentDescription = stringResource(download.badgeDescriptionRes),
            modifier = Modifier.fillMaxWidth().aspectRatio(BADGE_ASPECT_RATIO),
        )
        CinemaText(
            stringResource(download.captionRes),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The badges' images are 646 × 250. */
private const val BADGE_ASPECT_RATIO = 646f / 250f
