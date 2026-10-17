package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaAlertDialog
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.home.BrowseSource
import org.njarasoa.fijerena.core.ui.home.ProfileSections
import org.njarasoa.fijerena.core.ui.theme.ButtonKind
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.ui.components.buttons.CinemaDialogActionButton

/**
 * The profile menu, opened from the dock's profile slot (or the top bar's avatar while the
 * profile has no section, so no dock): [ProfileSheet] while [visible] — switch profile
 * ([onProfileChosen] runs after the switch), Settings ([onSettings]), and a "Browse <source>" row
 * per source outside the merged tabs ([sections]' Browse entries, O1): [onBrowseSection] opens the
 * section picked (a dialog asks which when it has several), [onBrowseSignIn] the source's sign-in
 * while this profile has no login for it. Kept composed while hidden, so that dialog outlives the
 * sheet.
 */
@Composable
fun MobileProfileMenu(
    visible: Boolean,
    viewModel: ProfilesViewModel,
    sections: ProfileSections?,
    onDismiss: () -> Unit,
    onProfileChosen: () -> Unit,
    onSettings: () -> Unit,
    onBrowseSection: (providerId: Long, contentType: String) -> Unit,
    onBrowseSignIn: (providerId: Long) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // A Browse entry's sections, when it has more than one: picked in a dialog.
    var browseChoice by remember { mutableStateOf<Pair<BrowseSource, List<String>>?>(null) }

    if (visible) {
        ProfileSheet(
            viewModel = viewModel,
            // Closed once switched: the menu sits above the screens the switch starts over.
            onProfileChosen = {
                onDismiss()
                onProfileChosen()
            },
            onDismiss = onDismiss,
            onSettings = onSettings,
            browse = sections?.browse.orEmpty(),
            onBrowse = { browsed ->
                coroutineScope.launch {
                    when (val opening = ProfileSections.opening(context, browsed.providerId)) {
                        ProfileSections.Opening.SignIn -> {
                            onBrowseSignIn(browsed.providerId)
                        }

                        is ProfileSections.Opening.Sections -> {
                            when (opening.contentTypes.size) {
                                0 -> Unit
                                1 -> onBrowseSection(browsed.providerId, opening.contentTypes.first())
                                else -> browseChoice = browsed to opening.contentTypes
                            }
                        }
                    }
                }
            },
        )
    }

    browseChoice?.let { (browsed, contentTypes) ->
        CinemaAlertDialog(
            onDismissRequest = { browseChoice = null },
            title = {
                CinemaText(
                    stringResource(R.string.browse_source, browsed.name),
                    role = TextRole.Title,
                    tone = TextTone.Inherit,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)) {
                    contentTypes.forEach { contentType ->
                        Surface(
                            onClick = {
                                browseChoice = null
                                onBrowseSection(browsed.providerId, contentType)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = CinemaTheme.colors.surfaceRaised,
                            shape = CinemaTheme.shapes.chip,
                        ) {
                            CinemaText(
                                text =
                                    stringResource(
                                        when (contentType) {
                                            ContentType.LIVE_TV -> R.string.provider_live_tv_label
                                            ContentType.MOVIES -> R.string.provider_movies_label
                                            else -> R.string.provider_tv_shows_label
                                        },
                                    ),
                                modifier = Modifier.padding(CinemaSpacing.md),
                                role = TextRole.Body,
                                tone = TextTone.Inherit,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                CinemaDialogActionButton(onClick = { browseChoice = null }, kind = ButtonKind.Other) {
                    CinemaText(
                        stringResource(R.string.common_cancel),
                        role = TextRole.Label,
                        tone = TextTone.Inherit,
                    )
                }
            },
        )
    }
}
