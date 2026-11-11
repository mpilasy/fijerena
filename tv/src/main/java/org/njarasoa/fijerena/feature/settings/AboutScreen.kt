package org.njarasoa.fijerena.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.njarasoa.fijerena.BuildConfig
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.QrCode
import org.njarasoa.fijerena.core.ui.components.SettingsRowKind
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.legal.ProjectLink
import org.njarasoa.fijerena.core.ui.utils.rememberAdminTapHandler
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.feature.settings.components.SettingsSection
import org.njarasoa.fijerena.feature.settings.components.TmdbAttributionRow
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.ui.components.TvScreenHeader
import org.njarasoa.fijerena.ui.components.input.NavReturnFocusEffect
import org.njarasoa.fijerena.ui.components.input.navReturnFocusTarget
import org.njarasoa.fijerena.ui.components.input.rememberNavReturnFocus
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.components.rail.leftToRail
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * Settings → About → About Fijerena on TV. The version row (OK counts toward Admin, 7 taps) with
 * the build and the no-content notice; the copyright and licence notice; the project's links,
 * each opening its QR code; Terms of Use and Open-Source Licences; the TMDB and sports data
 * credits. Every block is one focusable row (focus contract rule 3). Focus starts on the version
 * row, comes back to Terms or Licences from their screens and to a link when its dialog closes.
 */
@Composable
fun AboutScreen(
    onTerms: () -> Unit,
    onLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val onVersionClick = rememberAdminTapHandler(uiState.adminExists, viewModel::enableAdmin)
    val listState = rememberLazyListState()
    val returnFocus = rememberNavReturnFocus()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (returnFocus.key == null) firstFocus.requestFocusWithRetry()
    }
    NavReturnFocusEffect(returnFocus, listState = listState, fallback = firstFocus)

    val linkFocus = remember { ProjectLink.entries.associateWith { FocusRequester() } }
    var shownLink by remember { mutableStateOf<ProjectLink?>(null) }
    var closedLink by remember { mutableStateOf<ProjectLink?>(null) }
    LaunchedEffect(closedLink) {
        closedLink?.let { linkFocus.getValue(it).requestFocusWithRetry() }
        closedLink = null
    }

    LicenseList(listState) {
        item { TvScreenHeader(title = stringResource(R.string.about_title)) }
        item {
            SettingsSection {
                SettingsRow(
                    title = stringResource(R.string.settings_about_version_format, BuildConfig.VERSION_NAME),
                    description = null,
                    onClick = onVersionClick,
                    modifier = Modifier.leftToRail(),
                    kind = SettingsRowKind.Action,
                    focusRequester = firstFocus,
                    supporting = {
                        Column {
                            CinemaText(stringResource(R.string.app_disclaimer_no_content), role = TextRole.Body, tone = TextTone.Primary)
                            // Build and time on one line: a fourth line made a pill Look's border cut the last one.
                            CinemaText(
                                stringResource(R.string.settings_about_build_format, BuildConfig.GIT_HASH) + " · " +
                                    stringResource(R.string.settings_about_built_format, BuildConfig.BUILD_TIME),
                                role = TextRole.Body,
                                tone = TextTone.Secondary,
                            )
                        }
                    },
                )
                // GPL-3.0 "Appropriate Legal Notices": copyright, licence, no warranty, source. Its own
                // row: two more lines in the version row made the Console Look's pill clip its corners.
                SettingsRow(
                    title = stringResource(R.string.app_copyright),
                    description = stringResource(R.string.app_license_notice),
                    onClick = {},
                    modifier = Modifier.leftToRail(),
                    kind = SettingsRowKind.Action,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.about_links_title)) {
                ProjectLink.entries.forEach { link ->
                    SettingsRow(
                        title = stringResource(link.titleRes),
                        description = stringResource(link.descriptionRes) + "\n" + link.shownUrl,
                        onClick = { shownLink = link },
                        modifier = Modifier.leftToRail(),
                        focusRequester = linkFocus.getValue(link),
                    )
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.about_legal_title)) {
                SettingsRow(
                    title = stringResource(R.string.terms_title),
                    description = stringResource(R.string.terms_desc),
                    onClick = {
                        returnFocus.leaveFrom(RETURN_TERMS, listState)
                        onTerms()
                    },
                    modifier = Modifier.leftToRail().navReturnFocusTarget(returnFocus, RETURN_TERMS),
                )
                SettingsRow(
                    title = stringResource(R.string.licenses_title),
                    description = stringResource(R.string.licenses_desc),
                    onClick = {
                        returnFocus.leaveFrom(RETURN_LICENSES, listState)
                        onLicenses()
                    },
                    modifier = Modifier.leftToRail().navReturnFocusTarget(returnFocus, RETURN_LICENSES),
                )
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.about_credits_title)) {
                TmdbAttributionRow(Modifier.leftToRail())
                SettingsRow(
                    title = stringResource(R.string.sports_attribution_title),
                    description = stringResource(R.string.sports_attribution),
                    onClick = {},
                    modifier = Modifier.leftToRail(),
                    kind = SettingsRowKind.Action,
                )
            }
        }
    }

    shownLink?.let { link ->
        ProjectLinkDialog(
            link = link,
            onDismiss = {
                shownLink = null
                closedLink = link
            },
        )
    }
}

/** A project link's QR code beside its address: a phone camera opens the page the TV can't. */
@Composable
private fun ProjectLinkDialog(
    link: ProjectLink,
    onDismiss: () -> Unit,
) {
    val scale = LocalUiScale.current
    CinemaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(link.titleRes),
        wide = true,
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // look-ok: the QR code's size is what a phone camera needs to scan it from the sofa, not a look
                QrCode(text = link.url, size = 220.dp, contentDescription = stringResource(R.string.about_qr_description, link.shownUrl))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.scaled(scale))) {
                    CinemaText(link.shownUrl, role = TextRole.Body, tone = TextTone.Primary)
                    CinemaText(stringResource(R.string.about_qr_hint), role = TextRole.Body, tone = TextTone.Secondary)
                }
            }
        },
        cancel = DialogAction(stringResource(R.string.common_close), onClick = onDismiss),
    )
}

private const val RETURN_TERMS = "terms"
private const val RETURN_LICENSES = "licenses"
