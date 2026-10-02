@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.LocalUiScale
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.ProfileEditDialog
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled

/**
 * "Who's watching?" — pick the profile this TV uses. See `docs/plans/20260929_live-sync-plan.md`
 * → User profiles. [onProfileChosen] runs once the switch has happened; the caller rebuilds the
 * back stack from home.
 */
@Composable
fun ProfilePickerScreen(onProfileChosen: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val switchingTo by viewModel.switchingTo.collectAsStateWithLifecycle()
    val scale = LocalUiScale.current
    var adding by remember { mutableStateOf(false) }
    val activeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Land on this device's current profile once the list has loaded — OK then keeps it. Scrolled
    // to first: a lazy row only composes what's on screen, and focus can't reach a card that isn't.
    LaunchedEffect(profiles.isNotEmpty()) {
        val activeIndex = profiles.indexOfFirst { it.isActive }
        if (activeIndex >= 0) {
            listState.scrollToItem(activeIndex)
            runCatching { activeFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.tvSafeMarginHorizontal, vertical = Spacing.tvSafeMarginVertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.profile_picker_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.xl.scaled(scale)))
            switchingTo?.let { name ->
                // A switch can take a while (it re-applies the profile's category filters): say so,
                // instead of a list that looks like it ignored the pick.
                androidx.compose.material3.CircularProgressIndicator(color = CinemaAccentLight)
                Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
                Text(
                    text = stringResource(R.string.profile_switching, name),
                    style = MaterialTheme.typography.titleMedium,
                    color = CinemaTextPrimary,
                )
                return@Column
            }
            // Lazy and scrollable: a household with many profiles would otherwise run off the screen,
            // with D-pad focus moving onto cards nobody can see.
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg.scaled(scale)),
                contentPadding = PaddingValues(horizontal = Spacing.lg.scaled(scale), vertical = Spacing.md.scaled(scale)),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    PickerCard(
                        label = profile.name,
                        onClick = { viewModel.switchTo(profile.id, onProfileChosen) },
                        modifier = if (profile.isActive) Modifier.focusRequester(activeFocusRequester) else Modifier,
                        scale = scale,
                    ) {
                        ProfileAvatar(
                            name = profile.name,
                            colorIndex = profile.colorIndex,
                            size = TvDimensions.iconButtonSizeLarge.scaled(scale),
                            fontSize =
                                MaterialTheme.typography.displaySmall.fontSize
                                    .scaled(scale),
                        )
                    }
                }
                item(key = ADD_PROFILE_KEY) {
                    PickerCard(
                        label = stringResource(R.string.settings_profiles_add),
                        onClick = { adding = true },
                        scale = scale,
                    ) {
                        Box(
                            modifier = Modifier.size(TvDimensions.iconButtonSizeLarge.scaled(scale)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = CinemaIcons.Add,
                                contentDescription = null,
                                tint = CinemaTextPrimary,
                                modifier = Modifier.size(TvDimensions.iconLarge.scaled(scale)),
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        ProfileEditDialog(
            title = stringResource(R.string.profile_dialog_add_title),
            initialName = "",
            initialColorIndex = remember { viewModel.nextFreeColorIndex() },
            onSave = { name, color ->
                viewModel.addProfile(name, color)
                adding = false
            },
            onDelete = null,
            onDismiss = { adding = false },
            scale = scale,
        )
    }
}

/** A focusable avatar with its name underneath; the whole card is the D-pad target. */
@Composable
private fun PickerCard(
    label: String,
    onClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = TvFocusTokens.restingContainer,
                    focusedContainerColor = TvFocusTokens.focusedContainer,
                    pressedContainerColor = TvFocusTokens.focusedContainer,
                ),
            scale =
                ClickableSurfaceDefaults.scale(
                    scale = TvFocusTokens.defaultScale,
                    focusedScale = TvFocusTokens.focusedScale,
                    pressedScale = TvFocusTokens.pressedScale,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(TvFocusTokens.focusBorderWidth.scaled(scale), CinemaAccentLight)),
                ),
        ) { avatar() }
        Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
        Text(
            text = label,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize =
                        MaterialTheme.typography.titleMedium.fontSize
                            .scaled(scale),
                ),
            color = CinemaTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val ADD_PROFILE_KEY = "add-profile"
