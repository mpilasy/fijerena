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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.ProfileAvatar
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.components.textStyleOf
import org.njarasoa.fijerena.core.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiScale
import org.njarasoa.fijerena.core.ui.viewmodels.ProfileUi
import org.njarasoa.fijerena.core.ui.viewmodels.ProfilesViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.SettingsViewModelFactory
import org.njarasoa.fijerena.feature.settings.components.ProfileAddDialog
import org.njarasoa.fijerena.ui.components.TvLeaveChildPinDialog
import org.njarasoa.fijerena.ui.components.TvPinDialog
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * "Who's watching?" — pick the profile this TV uses. See the live sync plan
 * → User profiles. [onProfileChosen] runs once the switch has happened; the caller rebuilds the
 * back stack from home.
 */
@Composable
fun ProfilePickerScreen(onProfileChosen: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ProfilesViewModel = viewModel(factory = SettingsViewModelFactory(context))
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val switchingTo by viewModel.switchingTo.collectAsStateWithLifecycle()
    // Add profile is for an Expert only: the profile in use (at launch, the last one used) must
    // have Expert mode. So after a child, the launch picker has no Add profile.
    val appSettings = remember { AppSettings(context.applicationContext) }
    val expert by remember { appSettings.observeDevMode().map { it.second } }
        .collectAsStateWithLifecycle(initialValue = appSettings.isDevMode)
    var adding by remember { mutableStateOf(false) }
    var askingAdminPin by remember { mutableStateOf<List<String>?>(null) }
    val activeFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Land on this device's current profile once the list has loaded — OK then keeps it. Scrolled
    // to first only when it is off screen: a lazy row only composes what's on screen, and focus
    // can't reach a card that isn't. A row that fits stays put (scrolling it anyway pushed the
    // first profiles off the left edge, phone-a).
    LaunchedEffect(profiles.isNotEmpty()) {
        val activeIndex = profiles.indexOfFirst { it.isActive }
        if (activeIndex >= 0) {
            val shown = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
            if (shown.none { it.index == activeIndex }) listState.scrollToItem(activeIndex)
            activeFocusRequester.requestFocusWithRetry()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Only the top and bottom margins here: the row runs the full width, its own padding
                // keeping the cards inside the safe area, so a scrolled row isn't cut off mid-screen.
                .padding(vertical = Spacing.tvSafeMarginVertical),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CinemaText(
                stringResource(R.string.profile_picker_title),
                role = TextRole.Display,
                tone = TextTone.Primary,
            )
            Spacer(modifier = Modifier.height(Spacing.xxl))
            switchingTo?.let { name ->
                // A switch can take a while (it re-applies the profile's category filters): say so,
                // instead of a list that looks like it ignored the pick.
                androidx.compose.material3.CircularProgressIndicator(color = CinemaTheme.colors.accentSoft)
                Spacer(modifier = Modifier.height(Spacing.md))
                CinemaText(
                    stringResource(R.string.profile_switching, name),
                    role = TextRole.Title,
                    tone = TextTone.Primary,
                )
                return@Column
            }
            // Lazy and scrollable: a household with many profiles would otherwise run off the screen,
            // with D-pad focus moving onto cards nobody can see.
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                contentPadding = PaddingValues(horizontal = Spacing.tvSafeMarginHorizontal + Spacing.lg, vertical = Spacing.md),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    PickerCard(
                        label = profile.name,
                        onClick = {
                            viewModel.switchTo(profile.id, onProfileChosen)
                        },
                        modifier = if (profile.isActive) Modifier.focusRequester(activeFocusRequester) else Modifier,
                    ) {
                        ProfileAvatar(
                            name = profile.name,
                            colorIndex = profile.colorIndex,
                            size = TvDimensions.profilePickerAvatarSize,
                            fontSize = textStyleOf(TextRole.Display).fontSize * PICKER_INITIAL_SCALE,
                        )
                    }
                }
                if (expert) {
                    item(key = ADD_PROFILE_KEY) {
                        PickerCard(
                            label = stringResource(R.string.settings_profiles_add),
                            // Adding a profile is the admin's: the same PIN rule as in Settings.
                            onClick = {
                                val holders = viewModel.addProfilePinHolders()
                                if (holders.isEmpty()) adding = true else askingAdminPin = holders
                            },
                        ) {
                            Box(
                                modifier = Modifier.size(TvDimensions.profilePickerAvatarSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = CinemaIcons.Add,
                                    contentDescription = null,
                                    tint = CinemaTheme.colors.textPrimary,
                                    modifier = Modifier.size(TvDimensions.iconXLarge),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Leaving a child profile asks an Expert's PIN (the child lock); Cancel keeps the child.
    TvLeaveChildPinDialog(viewModel, onProfileChosen)

    askingAdminPin?.let { holders ->
        TvPinDialog(
            state =
                rememberPinPromptState(viewModel.pins, PinRequest.AnyOf(holders, stringResource(R.string.pin_enter_admin))) {
                    askingAdminPin = null
                    adding = true
                },
            onCancel = { askingAdminPin = null },
        )
    }

    if (adding) {
        ProfileAddDialog(
            initialColorIndex = remember { viewModel.nextFreeColorIndex() },
            onSave = { name, color, child ->
                viewModel.addProfile(name, color, child)
                adding = false
            },
            onDismiss = { adding = false },
            scale = LocalUiScale.current,
            noAdminPin = viewModel.adminPinHolders().isEmpty(),
        )
    }
}

/** A focusable avatar with its name underneath; the whole card is the D-pad target. */
@Composable
private fun PickerCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape = CinemaTheme.shapes.identity),
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
            // A white ring hugging the avatar (F-PP-1, TV UI audit #1): drawn just outside its edge,
            // with no gap, so it reads as the avatar's own outline; white, the rows' focus colour,
            // never blends into a coloured avatar the way the accent did.
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border = BorderStroke(TvFocusTokens.avatarFocusBorderWidth, TvFocusTokens.focusedRowOutline),
                            inset = TvFocusTokens.avatarFocusBorderWidth / 2,
                            shape = CinemaTheme.shapes.identity,
                        ),
                ),
        ) { avatar() }
        Spacer(modifier = Modifier.height(Spacing.md))
        CinemaText(
            label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            role = TextRole.Title,
            tone = TextTone.Primary,
        )
    }
}

private const val ADD_PROFILE_KEY = "add-profile"

/** The picker's big avatars carry a letter twice the Display role's size. */
private const val PICKER_INITIAL_SCALE = 2f
